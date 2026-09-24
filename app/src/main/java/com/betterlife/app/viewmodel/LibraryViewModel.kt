package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.ai.EntryRetriever
import com.betterlife.app.ai.RetrievedEntry
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.ProfileRepository
import com.betterlife.app.data.SectionDto
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
) : ViewModel() {

    data class UiState(
        val sections: List<SectionDto> = emptyList(),
        val selectedSection: Int? = null,
        val sectionEntries: List<EntryDto> = emptyList(),
        val recommended: LinkedHashMap<String, List<ScoredEntry>> = LinkedHashMap(),
        val entryStates: Map<String, String> = emptyMap(),
        val query: String = "",
        val searchResults: List<RetrievedEntry> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val data = entryRepository.entriesData()
            _uiState.value = _uiState.value.copy(
                sections = data.sections,
                selectedSection = data.sections.firstOrNull()?.n,
                sectionEntries = data.sections.firstOrNull()?.let { data.bySection[it.n].orEmpty() }.orEmpty(),
            )

            // 档案或条目状态变化时重算推荐
            kotlinx.coroutines.flow.combine(
                profileRepository.profileFlow,
                entryStateDao.allStatesFlow(),
            ) { profile, states -> profile to states }.collect { (profile, states) ->
                val stateMap = states.associate { it.entryId to it.state }
                val excluded = states.filter {
                    it.state == EntryStateEntity.STATE_DONE || it.state == EntryStateEntity.STATE_DISMISSED
                }.mapTo(HashSet()) { it.entryId }
                val recommended = profile?.let {
                    recommendationEngine.recommend(it, data.entries, data.rules, excluded)
                } ?: LinkedHashMap()
                _uiState.value = _uiState.value.copy(recommended = recommended, entryStates = stateMap)
            }
        }
    }

    fun selectSection(n: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = entryRepository.entriesData()
            _uiState.value = _uiState.value.copy(
                selectedSection = n,
                sectionEntries = data.bySection[n].orEmpty(),
            )
        }
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

    fun markEntryDone(entryId: String) = setState(entryId, EntryStateEntity.STATE_DONE)
    fun dismissEntry(entryId: String) = setState(entryId, EntryStateEntity.STATE_DISMISSED)
    fun restoreEntry(entryId: String) = setState(entryId, null)

    fun addToTodo(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.addOneOffTodo(entryId) }
    }

    private fun setState(entryId: String, state: String?) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.setEntryState(entryId, state) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                LibraryViewModel(
                    c.entryRepository, c.profileRepository, c.database.entryStateDao(),
                    c.taskManager, c.recommendationEngine, c.entryRetriever,
                )
            }
        }
    }
}
