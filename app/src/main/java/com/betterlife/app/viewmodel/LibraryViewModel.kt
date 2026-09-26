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
import com.betterlife.app.data.ProfileRepository
import com.betterlife.app.data.SectionDto
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.EntryStateDao
import com.betterlife.app.data.db.EntryStateEntity
import com.betterlife.app.recommend.RecommendationEngine
import com.betterlife.app.recommend.ScoredEntry
import com.betterlife.app.tasks.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    private val entryRepository: EntryRepository,
    private val profileRepository: ProfileRepository,
    private val entryStateDao: EntryStateDao,
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
        val sectionEntries: List<EntryDto> = emptyList(),
        val sort: EntrySort = EntrySort.RATIO,
        val recommended: LinkedHashMap<String, List<ScoredEntry>> = LinkedHashMap(),
        val query: String = "",
        val searchResults: List<RetrievedEntry> = emptyList(),
        /** 最近提交的搜索词,新词在前;输入框为空时展示 */
        val searchHistory: List<String> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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

            // 档案或条目状态变化时重算推荐
            kotlinx.coroutines.flow.combine(
                profileRepository.profileFlow,
                entryStateDao.allStatesFlow(),
            ) { profile, states -> profile to states }.collect { (profile, states) ->
                val excluded = states.filter {
                    it.state == EntryStateEntity.STATE_DONE || it.state == EntryStateEntity.STATE_DISMISSED
                }.mapTo(HashSet()) { it.entryId }
                val recommended = profile?.let {
                    recommendationEngine.recommend(it, data.entries, data.rules, excluded)
                } ?: LinkedHashMap()
                _uiState.value = _uiState.value.copy(recommended = recommended)
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
                sectionEntries = sortedSectionEntries(data, n, _uiState.value.sort),
            )
        }
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
            val results = if (query.isBlank()) emptyList()
            else retriever.search(query, data.entries, topK = 20)
            _uiState.value = _uiState.value.copy(searchResults = results)
        }
    }

    fun addToTodo(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.addOneOffTodo(entryId) }
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

    /** 不再推荐:标记 DISMISSED 后推荐列表随数据流自动收缩 */
    fun dismissEntry(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.setDismissed(entryId, true) }
    }

    /** 撤销「不再推荐」,清除 DISMISSED 状态 */
    fun restoreEntry(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.setDismissed(entryId, false) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                LibraryViewModel(
                    c.entryRepository, c.profileRepository, c.database.entryStateDao(),
                    c.taskManager, c.recommendationEngine, c.entryRetriever, c.settingsStore,
                )
            }
        }
    }
}

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
    EntrySort.ORDER -> entries.sortedBy { it.id }
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
