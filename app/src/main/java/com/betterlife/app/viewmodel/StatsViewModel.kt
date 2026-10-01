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
import com.betterlife.app.data.db.CustomEntryEntity
import com.betterlife.app.data.db.EntryStateDao
import com.betterlife.app.data.db.StreakLeaveDao
import com.betterlife.app.data.db.StreakLeaveEntity
import com.betterlife.app.data.db.TaskDao
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.data.resolveActiveProvider
import com.betterlife.app.recommend.EntryStats
import com.betterlife.app.recommend.RecommendationEngine
import com.betterlife.app.recommend.computeEntryStats
import com.betterlife.app.stats.Achievement
import com.betterlife.app.stats.AchievementInput
import com.betterlife.app.stats.DailyStreak
import com.betterlife.app.stats.MilestoneShareData
import com.betterlife.app.stats.PeriodReport
import com.betterlife.app.stats.StatsEntry
import com.betterlife.app.stats.StatsPeriod
import com.betterlife.app.stats.StatsResult
import com.betterlife.app.stats.StatsTaskRow
import com.betterlife.app.stats.WeeklyCompletion
import com.betterlife.app.stats.buildPeriodInterpretPrompt
import com.betterlife.app.stats.computeStats
import com.betterlife.app.stats.evaluateAchievements
import com.betterlife.app.stats.isStreakMilestone
import com.betterlife.app.stats.periodReport
import com.betterlife.app.stats.pickMilestoneQuote
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
    private val entryStateDao: EntryStateDao,
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
        /** 按口径的条目完成度（全书的 待看/完成/忽略，按口径固定序） */
        val lensCompletion: List<LensCompletion> = emptyList(),
        /** 按章的条目完成度，章号升序 */
        val sectionCompletion: List<SectionCompletion> = emptyList(),
    )

    /** 一个口径的完成度 */
    data class LensCompletion(val lens: String, val stats: EntryStats)

    /** 一章的完成度 */
    data class SectionCompletion(val n: Int, val title: String, val stats: EntryStats)

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
            val entriesData = withContext(Dispatchers.IO) { entryRepository.entriesData() }
            val libraryEntries = entriesData.byId
            // 主统计五路 combine 已满是其一;条目状态(完成度卡)变化频率低,嵌套一层即可
            combine(
                combine(
                    taskDao.allFlow(),
                    streakLeaveDao.allFlow(),
                    customEntryDao.allFlow(),
                    todayFlow,
                    settingsStore.celebratedMilestonesFlow,
                ) { tasks, leaves, customs, today, celebrated ->
                    Bundle0(tasks, leaves, customs, today, celebrated)
                },
                entryStateDao.allStatesFlow(),
            ) { bundle0, states ->
                val (tasks, leaves, customs, today, celebrated) = bundle0
                val entries = buildEntryMap(libraryEntries.mapValues { (_, e) -> e.title to e.lens }, customs.map { it.entryId to it.title })
                val rows = tasks.map { it.toStatsRow() }
                val leaveMap = leaves.groupBy({ it.entryId }, { it.date }).mapValues { (_, v) -> v.toSet() }
                val stats = computeStats(rows, entries, leaveMap, today)
                val achievements = evaluateAchievements(
                    AchievementInput(stats.bestStreak, stats.totalCompletions, stats.perfectWeeks)
                )
                // 全书的 待看/完成/忽略 完成度:按口径与按章两个视角
                val statesByEntry = states.groupBy({ it.entryId }, { it.state })
                    .mapValues { (_, v) -> v.toSet() }
                val lensCompletion = entriesData.entries
                    .filter { !it.removed }
                    .groupBy { it.lens }
                    .map { (lens, lensEntries) -> LensCompletion(lens, computeEntryStats(lensEntries, statesByEntry)) }
                    .sortedBy { lensCompletionOrder(it.lens) }
                val sectionCompletion = entriesData.sections.map { section ->
                    SectionCompletion(
                        section.n,
                        section.title,
                        computeEntryStats(
                            entriesData.bySection[section.n].orEmpty().filter { !it.removed },
                            statesByEntry,
                        ),
                    )
                }
                Bundle(rows, entries, today, stats, achievements, celebrated, lensCompletion, sectionCompletion)
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
                        lensCompletion = bundle.lensCompletion,
                        sectionCompletion = bundle.sectionCompletion,
                    )
                }
            }
        }
    }

    fun selectPeriod(period: StatsPeriod) {
        _uiState.update { it.copy(selectedPeriod = period, report = reportFor(period), insight = InsightState()) }
    }

    /**
     * N6:日签分享数据,点连签里程碑行的「分享」时现取(不常驻 UiState)。
     * 非连签里程碑或成就列表里找不到时返回 null。书摘优先取已 DONE 的库内条目,
     * 没 DONE 过用种子池兜底(挑选规则见 stats/MilestoneShare.kt)。
     */
    suspend fun milestoneShareData(key: String): MilestoneShareData? {
        if (!isStreakMilestone(key)) return null
        val achievement = _uiState.value.achievements.firstOrNull { it.key == key } ?: return null
        val entriesData = withContext(Dispatchers.IO) { entryRepository.entriesData() }
        val doneIds = lastRows.filter { it.done }.map { it.entryId }
            .filter { it in entriesData.byId }.toSet()
        val quoteId = pickMilestoneQuote(doneIds, entriesData.seedEntryIds, lastToday)
        val quote = quoteId?.let { entriesData.byId[it] }
        return MilestoneShareData(
            streakDays = achievement.value,
            date = lastToday,
            quoteTitle = quote?.title,
            quoteHuman = quote?.human,
        )
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

    /** 内层 combine 的中间打包：主统计的五路输入 */
    private data class Bundle0(
        val tasks: List<TaskEntity>,
        val leaves: List<StreakLeaveEntity>,
        val customs: List<CustomEntryEntity>,
        val today: LocalDate,
        val celebrated: Set<String>,
    )

    private class Bundle(
        val rows: List<StatsTaskRow>,
        val entries: Map<String, StatsEntry>,
        val today: LocalDate,
        val stats: StatsResult,
        val achievements: List<Achievement>,
        val celebrated: Set<String>,
        val lensCompletion: List<LensCompletion>,
        val sectionCompletion: List<SectionCompletion>,
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
                    entryStateDao = c.database.entryStateDao(),
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

/** 完成度卡的口径排序:固定序优先,其余口径字典序排后 */
private fun lensCompletionOrder(lens: String): Int {
    val index = RecommendationEngine.LENS_ORDER.indexOf(lens)
    return if (index >= 0) index else RecommendationEngine.LENS_ORDER.size
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
