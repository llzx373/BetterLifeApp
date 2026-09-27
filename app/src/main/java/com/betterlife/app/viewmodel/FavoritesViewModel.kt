package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.db.EntryNoteDao
import com.betterlife.app.tasks.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val entryRepository: EntryRepository,
    private val taskManager: TaskManager,
    private val entryNoteDao: EntryNoteDao,
) : ViewModel() {

    data class UiState(
        val entries: List<EntryDto> = emptyList(),
        /** 条目 id → 用户笔记,行内展示预览用 */
        val notes: Map<String, String> = emptyMap(),
        val loaded: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                taskManager.favoriteIdsFlow(),
                entryNoteDao.allFlow(),
            ) { ids, notes -> ids to notes }.collect { (ids, notes) ->
                val byId = entryRepository.entriesData().byId
                _uiState.value = UiState(
                    entries = ids.mapNotNull { byId[it] }.sortedBy { it.id },
                    notes = notes.associate { it.entryId to it.text },
                    loaded = true,
                )
            }
        }
    }

    /** 在收藏列表里取消收藏,列表会随数据流自动收缩 */
    fun remove(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.setFavorite(entryId, false) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                FavoritesViewModel(c.entryRepository, c.taskManager, c.database.entryNoteDao())
            }
        }
    }
}
