package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.tasks.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DismissedViewModel(
    private val entryRepository: EntryRepository,
    private val taskManager: TaskManager,
) : ViewModel() {

    data class UiState(
        val entries: List<EntryDto> = emptyList(),
        val loaded: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.dismissedIdsFlow().collect { ids ->
                val byId = entryRepository.entriesData().byId
                _uiState.value = UiState(
                    entries = ids.mapNotNull { byId[it] }
                        .sortedWith(compareBy({ it.sec }, { it.n })),
                    loaded = true,
                )
            }
        }
    }

    /** 恢复推荐:清掉 DISMISSED,列表随数据流自动收缩 */
    fun restore(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.setDismissed(entryId, false) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                DismissedViewModel(c.entryRepository, c.taskManager)
            }
        }
    }
}
