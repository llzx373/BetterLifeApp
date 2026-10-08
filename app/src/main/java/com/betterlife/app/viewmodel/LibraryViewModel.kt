package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.ai.EntryRetriever
import com.betterlife.app.ai.RetrievedEntry
import com.betterlife.app.data.EntriesData
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.Profile
import com.betterlife.app.data.ProfileRepository
import com.betterlife.app.data.SectionDto
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.EntryNoteDao
import com.betterlife.app.data.db.EntryNoteEntity
import com.betterlife.app.data.db.EntryStateDao
import com.betterlife.app.data.db.EntryStateEntity
import com.betterlife.app.recommend.EntryFilter
import com.betterlife.app.recommend.EntryStats
import com.betterlife.app.recommend.ProfileQuestions
import com.betterlife.app.recommend.RecommendationEngine
import com.betterlife.app.recommend.ScoredEntry
import com.betterlife.app.recommend.applyFilter
import com.betterlife.app.recommend.applyStatusFilter
import com.betterlife.app.recommend.computeEntryStats
import com.betterlife.app.recommend.matchesFilter
import com.betterlife.app.recommend.matchesStatus
import com.betterlife.app.tasks.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    private val entryRepository: EntryRepository,
    private val profileRepository: ProfileRepository,
    private val entryStateDao: EntryStateDao,
    private val entryNoteDao: EntryNoteDao,
    private val taskManager: TaskManager,
    private val recommendationEngine: RecommendationEngine,
    private val retriever: EntryRetriever,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    data class UiState(
        val sections: List<SectionDto> = emptyList(),
        /** 每章的主导口径,用来给目录着色 */
        val sectionLens: Map<Int, String> = emptyMap(),
        val selectedSection: Int? = null,
        /** 宽屏(≥840dp 的列表|详情双栏、≥1200dp 的三栏)里详情栏展示的条目;窄屏详情走整屏路由,不用这个状态 */
        val selectedEntryId: String? = null,
        val sectionEntries: List<EntryDto> = emptyList(),
        val sort: EntrySort = EntrySort.RATIO,
        /** 章内列表与搜索结果共用的筛选条件;推荐不受影响 */
        val filter: EntryFilter = EntryFilter(),
        val recommended: LinkedHashMap<String, List<ScoredEntry>> = LinkedHashMap(),
        /** 已完成(STATE_DONE)的条目 id,条目库/详情页据此显示「已完成」徽标 */
        val doneIds: Set<String> = emptySet(),
        /** 已加入任一计划(一次性/每日/每周)的条目 id,条目库据此显示「已加入」徽标 */
        val plannedIds: Set<String> = emptySet(),
        /** 每章的 待看/已完成/已忽略 统计,目录行展示用 */
        val sectionStats: Map<Int, EntryStats> = emptyMap(),
        /** 每个口径的 待看/已完成/已忽略 统计,今日页推荐分组头展示用 */
        val lensStats: Map<String, EntryStats> = emptyMap(),
        val query: String = "",
        val searchResults: List<RetrievedEntry> = emptyList(),
        /** 当前筛选下的总命中数（截断前）;超过 searchResults.size 说明只显示了前若干条 */
        val searchTotalHits: Int = 0,
        /** 最近提交的搜索词,新词在前;输入框为空时展示 */
        val searchHistory: List<String> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 搜索结果的未筛选基线;setFilter 从它重算,清空筛选才能恢复被滤掉的条目 */
    private var searchBaseline: List<RetrievedEntry> = emptyList()

    /** 已收藏的条目 id,详情页据此显示收藏态 */
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val data = entryRepository.entriesData()
            val first = data.sections.firstOrNull()
            _uiState.value = _uiState.value.copy(
                sections = data.sections,
                sectionLens = data.sections.associate { it.n to dominantLens(data, it.n) },
                selectedSection = first?.n,
                sectionEntries = first?.let { sortedSectionEntries(data, it.n, _uiState.value.sort) }.orEmpty(),
            )

            // 档案、条目状态或计划成员变化时重算推荐与统计
            // 推荐排除:做过(DONE)、不再推荐(DISMISSED)、已加入任一计划(TODO/DAILY/每周习惯)
            // recommendOffset:「换一批」的轮次,组内候选按它轮转
            // N1:没有档案也能出推荐——空档案只命中 {} 普惠规则;部分档案只认已答字段
            kotlinx.coroutines.flow.combine(
                profileRepository.profileFlow,
                entryStateDao.allStatesFlow(),
                taskManager.plannedEntryIdsFlow(),
                settingsStore.recommendOffsetFlow,
                settingsStore.profileQuestionsAnsweredFlow,
            ) { profile, states, planned, offset, answered ->
                RecommendInput(profile, states, planned, offset, answered)
            }
                .collect { (profile, states, planned, offset, answered) ->
                    val doneIds = states.filter { it.state == EntryStateEntity.STATE_DONE }
                        .mapTo(HashSet()) { it.entryId }
                    val dismissedIds = states.filter { it.state == EntryStateEntity.STATE_DISMISSED }
                        .mapTo(HashSet()) { it.entryId }
                    val excluded = HashSet<String>(doneIds.size + dismissedIds.size + planned.size).apply {
                        addAll(doneIds); addAll(dismissedIds); addAll(planned)
                    }
                    val recommended = recommendationEngine.recommend(
                        ProfileQuestions.effectiveProfile(profile, answered),
                        data.entries, data.rules, excluded, offset = offset,
                    )
                    val statesByEntry = states.groupBy({ it.entryId }, { it.state })
                        .mapValues { it.value.toSet() }
                    val sectionStats = data.sections.associate { section ->
                        section.n to computeEntryStats(data.bySection[section.n].orEmpty(), statesByEntry)
                    }
                    val lensStats = data.entries.groupBy { it.lens }
                        .mapValues { (_, lensEntries) -> computeEntryStats(lensEntries, statesByEntry) }
                    _uiState.value = _uiState.value.copy(
                        recommended = recommended,
                        doneIds = doneIds,
                        plannedIds = planned,
                        sectionStats = sectionStats,
                        lensStats = lensStats,
                    )
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            taskManager.favoriteIdsFlow().collect { ids -> _favorites.value = ids.toSet() }
        }

        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.searchHistoryFlow.collect { history ->
                _uiState.value = _uiState.value.copy(searchHistory = history)
            }
        }
    }

    fun selectSection(n: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = entryRepository.entriesData()
            _uiState.value = _uiState.value.copy(
                selectedSection = n,
                // 换章后右栏若还留着旧章的详情就串味了,一并清掉
                selectedEntryId = null,
                sectionEntries = sortedSectionEntries(data, n, _uiState.value.sort)
                    .applyFullFilter(_uiState.value.filter),
            )
        }
    }

    /** 内容维度 + 状态维度的完整筛选;状态判定用当前快照里的 done/planned id 集合 */
    private fun List<EntryDto>.applyFullFilter(filter: EntryFilter): List<EntryDto> =
        applyFilter(filter).applyStatusFilter(filter.status, _uiState.value.doneIds, _uiState.value.plannedIds)

    private fun RetrievedEntry.matchesFullFilter(filter: EntryFilter): Boolean =
        entry.matchesFilter(filter) &&
            entry.matchesStatus(filter.status, _uiState.value.doneIds, _uiState.value.plannedIds)

    /** 筛选条件切换:章内列表与搜索结果都跟着收缩;推荐不变 */
    fun setFilter(filter: EntryFilter) {
        val hits = searchBaseline.filter { it.matchesFullFilter(filter) }
        _uiState.value = _uiState.value.copy(
            filter = filter,
            searchResults = hits.take(SEARCH_TOP_K),
            searchTotalHits = hits.size,
        )
        val section = _uiState.value.selectedSection ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val data = entryRepository.entriesData()
            _uiState.value = _uiState.value.copy(
                sectionEntries = sortedSectionEntries(data, section, _uiState.value.sort).applyFullFilter(filter),
            )
        }
    }

    fun toggleRatioFilter(value: String) = setFilter(_uiState.value.filter.toggleRatio(value))

    fun toggleGradeFilter(value: String) = setFilter(_uiState.value.filter.toggleGrade(value))

    fun toggleLensFilter(value: String) = setFilter(_uiState.value.filter.toggleLens(value))

    fun clearFilter() = setFilter(EntryFilter())

    /** 某条目的用户笔记流;详情页据此显示/编辑 */
    fun noteFlow(entryId: String): Flow<EntryNoteEntity?> = entryNoteDao.noteFlow(entryId)

    /** 保存笔记;空白文本等价于删除 */
    fun saveNote(entryId: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                entryNoteDao.delete(entryId)
            } else {
                entryNoteDao.upsert(EntryNoteEntity(entryId, trimmed, System.currentTimeMillis()))
            }
        }
    }

    fun deleteNote(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { entryNoteDao.delete(entryId) }
    }

    /** 三栏布局里点条目:右栏就地展示详情,不跳路由 */
    fun selectEntry(entryId: String?) {
        _uiState.value = _uiState.value.copy(selectedEntryId = entryId)
    }

    /** 章内排序切换:按性价比 / 按证据等级 / 按原书顺序 */
    fun setSort(sort: EntrySort) {
        _uiState.value = _uiState.value.copy(
            sort = sort,
            sectionEntries = sortEntries(_uiState.value.sectionEntries, sort),
        )
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        viewModelScope.launch(Dispatchers.IO) {
            val data = entryRepository.entriesData()
            if (query.isBlank()) {
                searchBaseline = emptyList()
                _uiState.value = _uiState.value.copy(searchResults = emptyList(), searchTotalHits = 0)
            } else {
                // 搜索语料同样剔除下架条目（与 bySection 的浏览过滤一致）。
                // 检索器本来就对全库打分排序,topK 放开了拿全量,命中数与「前 N 条」才说得清
                val baseline = retriever.search(
                    query, data.entries.filter { !it.removed }, topK = Int.MAX_VALUE,
                )
                searchBaseline = baseline
                val hits = baseline.filter { it.matchesFullFilter(_uiState.value.filter) }
                _uiState.value = _uiState.value.copy(
                    searchResults = hits.take(SEARCH_TOP_K),
                    searchTotalHits = hits.size,
                )
            }
        }
    }

    fun addToTodo(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.addOneOffTodo(entryId) }
    }

    /** 详情页「加入计划」选每日：写 STATE_DAILY 并立即为今天补一行 */
    fun addDaily(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.addDailyHabit(entryId) }
    }

    /** 详情页「加入计划」选每周：建一周 [timesPerWeek] 次的每周习惯模板 */
    fun addWeekly(entryId: String, timesPerWeek: Int) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.addWeeklyHabit(entryId, timesPerWeek) }
    }

    /** 「换一批」:推荐组内候选按轮次轮转(持久化在 SettingsStore,重启不跳回第一批) */
    fun reshuffleRecommendations() {
        viewModelScope.launch(Dispatchers.IO) { settingsStore.bumpRecommendOffset() }
    }

    /** 提交搜索:当前词非空白才进历史(输入即搜不产生历史,否则每敲一个字都是一条) */
    fun submitSearch() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { settingsStore.addSearchHistory(query) }
    }

    /** 收藏开关;与「加入待办」是两回事,互不覆盖 */
    fun toggleFavorite(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.setFavorite(entryId, entryId !in _favorites.value)
        }
    }

    /** 不再推荐:标记 DISMISSED 后推荐列表随数据流自动收缩;三栏右栏若正展示这条,清掉选中 */
    fun dismissEntry(entryId: String) {
        if (_uiState.value.selectedEntryId == entryId) {
            _uiState.value = _uiState.value.copy(selectedEntryId = null)
        }
        viewModelScope.launch(Dispatchers.IO) { taskManager.setDismissed(entryId, true) }
    }

    /** 撤销「不再推荐」,清除 DISMISSED 状态 */
    fun restoreEntry(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.setDismissed(entryId, false) }
    }

    /** 我做过了:写 DONE 状态,推荐列表随数据流自动收缩(不产生任务行) */
    fun markDoneBefore(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.markDoneBefore(entryId) }
    }

    /** 撤销「我做过了」,只清 DONE 状态 */
    fun unmarkDoneBefore(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.unmarkDoneBefore(entryId) }
    }

    companion object {
        /** 搜索结果最多显示条数;总命中数(searchTotalHits)不受它限制 */
        private const val SEARCH_TOP_K = 20

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                LibraryViewModel(
                    c.entryRepository, c.profileRepository, c.database.entryStateDao(),
                    c.database.entryNoteDao(), c.taskManager, c.recommendationEngine,
                    c.entryRetriever, c.settingsStore,
                )
            }
        }
    }
}

/** 推荐重算的输入打包:五路 combine 的超长 lambda 参数不好读,收成一个元组类 */
private data class RecommendInput(
    val profile: Profile?,
    val states: List<EntryStateEntity>,
    val planned: Set<String>,
    val offset: Int,
    /** 每日一问已答的档案字段（N1）：重建部分档案的已知字段用 */
    val answered: Set<String>,
)

/** 章内条目排序方式 */
enum class EntrySort { RATIO, GRADE, ORDER }

/**
 * 一章的主导口径 = 该章条目里出现最多的 lens。
 * 并列时按 RecommendationEngine.LENS_ORDER 取靠前的,保证结果稳定。
 */
internal fun dominantLens(data: EntriesData, sectionN: Int): String {
    val counts = data.bySection[sectionN].orEmpty()
        .filter { it.lens.isNotBlank() }
        .groupingBy { it.lens }
        .eachCount()
    if (counts.isEmpty()) return ""
    val ordered = RecommendationEngine.LENS_ORDER.filter { it in counts } +
        counts.keys.filter { it !in RecommendationEngine.LENS_ORDER }.sorted()
    return ordered.maxByOrNull { counts.getValue(it) }.orEmpty()
}

private fun gradeRank(grade: String): Int = when (grade) {
    "A" -> 0
    "B" -> 1
    "C" -> 2
    else -> 3
}

internal fun sortEntries(entries: List<EntryDto>, sort: EntrySort): List<EntryDto> = when (sort) {
    EntrySort.RATIO -> entries.sortedWith(RecommendationEngine.ENTRY_COMPARATOR)
    EntrySort.GRADE -> entries.sortedWith(
        compareBy<EntryDto> { gradeRank(it.grade) }.thenComparing(RecommendationEngine.ENTRY_COMPARATOR),
    )
    EntrySort.ORDER -> entries.sortedWith(compareBy({ it.sec }, { it.n }))
}

/** 某一章按当前排序方式取条目：首屏填充与切换章节都走它，保证两处结果一致 */
internal fun sortedSectionEntries(data: EntriesData, sectionN: Int, sort: EntrySort): List<EntryDto> =
    sortEntries(data.bySection[sectionN].orEmpty(), sort)

/** 搜索历史更新:新词去重置顶,超长砍掉最旧的;空白词不进历史 */
internal fun updateHistory(old: List<String>, new: String, max: Int = 10): List<String> {
    val trimmed = new.trim()
    if (trimmed.isEmpty()) return old
    return (listOf(trimmed) + old.filter { it != trimmed }).take(max)
}
