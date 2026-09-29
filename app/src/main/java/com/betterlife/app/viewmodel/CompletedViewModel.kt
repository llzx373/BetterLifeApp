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

/** 已完成列表：所有带 DONE 状态的条目（打卡完成或「我做过了」），可在此撤销 */
class CompletedViewModel(
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
            taskManager.doneIdsFlow().collect { ids ->
                val byId = entryRepository.entriesData().byId
                _uiState.value = UiState(
                    entries = ids.mapNotNull { byId[it] }
                        .sortedWith(compareBy({ it.sec }, { it.n })),
                    loaded = true,
                )
            }
        }
    }

    /** 撤销完成：清掉 DONE，条目回到推荐池 */
    fun unmark(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.unmarkDoneBefore(entryId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                CompletedViewModel(c.entryRepository, c.taskManager)
            }
        }
    }
}
