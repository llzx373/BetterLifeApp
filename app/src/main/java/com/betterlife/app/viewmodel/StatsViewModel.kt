package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.ai.AiAdvisor
import com.betterlife.app.ai.AiStreamEvent
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.Profile
import com.betterlife.app.data.ProfileRepo
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.CustomEntryDao
import com.betterlife.app.data.db.StreakLeaveDao
import com.betterlife.app.data.db.TaskDao
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.data.resolveActiveProvider
import com.betterlife.app.stats.Achievement
import com.betterlife.app.stats.AchievementInput
import com.betterlife.app.stats.DailyStreak
import com.betterlife.app.stats.PeriodReport
import com.betterlife.app.stats.StatsEntry
import com.betterlife.app.stats.StatsPeriod
import com.betterlife.app.stats.StatsResult
import com.betterlife.app.stats.StatsTaskRow
import com.betterlife.app.stats.WeeklyCompletion
import com.betterlife.app.stats.buildPeriodInterpretPrompt
import com.betterlife.app.stats.computeStats
import com.betterlife.app.stats.evaluateAchievements
import com.betterlife.app.stats.periodReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class StatsViewModel(
    private val taskDao: TaskDao,
    private val streakLeaveDao: StreakLeaveDao,
    private val customEntryDao: CustomEntryDao,
    private val entryRepository: EntryRepository,
    private val settingsStore: SettingsStore,
    private val aiAdvisor: AiAdvisor,
    private val profileRepository: ProfileRepo,
    private val todayFlow: StateFlow<LocalDate>,
) : ViewModel() {

    /** AI 解读的上屏状态：failed 时 UI 给安静的内联错误 + 重试 */
    data class InsightState(
        val requested: Boolean = false,
        val text: String = "",
        val streaming: Boolean = false,
        val failed: Boolean = false,
    )

    data class UiState(
        val loaded: Boolean = false,
        /** 是否一条打卡记录都没有（空状态走安静的引导文案） */
        val hasAnyActivity: Boolean = false,
        val achievements: List<Achievement> = emptyList(),
        /** 本次打开页面时新达成的里程碑 key，做一次性低调高亮 */
        val newAchievementKeys: Set<String> = emptySet(),
        val streaks: List<DailyStreak> = emptyList(),
        val bestStreak: Int = 0,
        val weeklyTrend: List<WeeklyCompletion> = emptyList(),
        val selectedPeriod: StatsPeriod = StatsPeriod.THIS_WEEK,
        val report: PeriodReport? = null,
        val aiConfigured: Boolean = false,
        val insight: InsightState = InsightState(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 最近一次统计的原始输入，切换周期时不必等 Flow 重发就能重算报告
    private var lastRows: List<StatsTaskRow> = emptyList()
    private var lastEntries: Map<String, StatsEntry> = emptyMap()
    private var lastToday: LocalDate = LocalDate.now()

    /** 本次进入页面时已庆祝过的里程碑快照；新达成 = 已达成 − 这个快照 */
    private var celebratedAtEntry: Set<String>? = null

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { cfg ->
                val configured = resolveActiveProvider(cfg.aiProviders, cfg.activeProviderId)
                    ?.apiKey?.isNotBlank() == true
                _uiState.update { it.copy(aiConfigured = configured) }
            }
        }
        viewModelScope.launch {
            val libraryEntries = withContext(Dispatchers.IO) { entryRepository.entriesData().byId }
            combine(
                taskDao.allFlow(),
                streakLeaveDao.allFlow(),
                customEntryDao.allFlow(),
                todayFlow,
                settingsStore.celebratedMilestonesFlow,
            ) { tasks, leaves, customs, today, celebrated ->
                val entries = buildEntryMap(libraryEntries.mapValues { (_, e) -> e.title to e.lens }, customs.map { it.entryId to it.title })
                val rows = tasks.map { it.toStatsRow() }
                val leaveMap = leaves.groupBy({ it.entryId }, { it.date }).mapValues { (_, v) -> v.toSet() }
                val stats = computeStats(rows, entries, leaveMap, today)
                val achievements = evaluateAchievements(
                    AchievementInput(stats.bestStreak, stats.totalCompletions, stats.perfectWeeks)
                )
                Bundle(rows, entries, today, stats, achievements, celebrated)
            }.flowOn(Dispatchers.Default).collect { bundle ->
                val firstVisit = celebratedAtEntry == null
                if (firstVisit) {
                    celebratedAtEntry = bundle.celebrated
                    val achievedKeys = bundle.achievements.map { it.key }.toSet()
                    // 进来就把已达成的都标记为已庆祝，高亮只发生在「已达成 − 进入时已庆祝」这一帧
                    if (achievedKeys.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            settingsStore.addCelebratedMilestones(achievedKeys)
                        }
                    }
                }
                lastRows = bundle.rows
                lastEntries = bundle.entries
                lastToday = bundle.today
                val stats = bundle.stats
                _uiState.update { s ->
                    s.copy(
                        loaded = true,
                        hasAnyActivity = stats.totalCompletions > 0 || stats.dailyStreaks.isNotEmpty(),
                        achievements = bundle.achievements,
                        newAchievementKeys = bundle.achievements.map { it.key }.toSet() - celebratedAtEntry.orEmpty(),
                        streaks = stats.dailyStreaks,
                        bestStreak = stats.bestStreak,
                        weeklyTrend = stats.weeklyTrend,
                        report = reportFor(s.selectedPeriod),
                    )
                }
            }
        }
    }

    fun selectPeriod(period: StatsPeriod) {
        _uiState.update { it.copy(selectedPeriod = period, report = reportFor(period), insight = InsightState()) }
    }

    private fun reportFor(period: StatsPeriod): PeriodReport {
        val (start, end) = period.range(lastToday)
        return periodReport(lastRows, lastEntries, start, end)
    }

    /**
     * C5③：解读当前选中的周期。[periodLabel] 由 UI 从 string 资源取（本周/上月…），
     * ViewModel 不出现 UI 文案。
     */
    fun interpretPeriod(periodLabel: String) {
        val s = _uiState.value
        val report = s.report ?: return
        if (s.insight.streaming) return
        _uiState.update { it.copy(insight = InsightState(requested = true, streaming = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = settingsStore.current()
            val provider = resolveActiveProvider(cfg.aiProviders, cfg.activeProviderId)
            if (provider == null || provider.apiKey.isBlank()) {
                _uiState.update { it.copy(insight = InsightState(requested = true, failed = true)) }
                return@launch
            }
            val profile = profileRepository.profileFlow.first() ?: Profile()
            val prompt = buildPeriodInterpretPrompt(report, periodLabel, s.bestStreak)
            try {
                aiAdvisor.interpretStatsStream(profile, prompt, provider).collect { event ->
                    when (event) {
                        is AiStreamEvent.Delta -> _uiState.update {
                            it.copy(insight = it.insight.copy(text = it.insight.text + event.text))
                        }
                        is AiStreamEvent.Done -> _uiState.update {
                            it.copy(
                                insight = InsightState(
                                    requested = true,
                                    text = event.fullText,
                                    streaming = false,
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(insight = InsightState(requested = true, failed = true))
                }
            }
        }
    }

    private class Bundle(
        val rows: List<StatsTaskRow>,
        val entries: Map<String, StatsEntry>,
        val today: LocalDate,
        val stats: StatsResult,
        val achievements: List<Achievement>,
        val celebrated: Set<String>,
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                StatsViewModel(
                    taskDao = c.database.taskDao(),
                    streakLeaveDao = c.database.streakLeaveDao(),
                    customEntryDao = c.database.customEntryDao(),
                    entryRepository = c.entryRepository,
                    settingsStore = c.settingsStore,
                    aiAdvisor = c.aiAdvisor,
                    profileRepository = c.profileRepository,
                    todayFlow = c.taskManager.todayFlow(),
                )
            }
        }
    }
}

private fun TaskEntity.toStatsRow() = StatsTaskRow(
    entryId = entryId,
    type = type,
    date = date,
    done = done,
    note = note,
)

/** 条目库 + 自定义条目的标题/口径合并视图；自定义条目没有口径（空串） */
private fun buildEntryMap(
    library: Map<String, Pair<String, String>>,
    customs: List<Pair<String, String>>,
): Map<String, StatsEntry> {
    val map = library.mapValues { (id, titleLens) -> StatsEntry(id, titleLens.first, titleLens.second) }
    return map + customs.map { (id, title) -> id to StatsEntry(id, title, "") }
}
