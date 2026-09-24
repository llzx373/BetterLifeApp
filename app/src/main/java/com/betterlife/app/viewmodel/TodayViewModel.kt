package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.ProfileRepository
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.tasks.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TodayViewModel(
    private val taskManager: TaskManager,
    private val profileRepository: ProfileRepository,
    private val entryRepository: EntryRepository,
) : ViewModel() {

    data class TaskItem(
        val task: TaskEntity,
        val entry: EntryDto?,
        val streak: Int,
    )

    data class UiState(
        val loading: Boolean = true,
        val items: List<TaskItem> = emptyList(),
    ) {
        val undoneCount: Int get() = items.count { !it.task.done }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // 档案就绪后确保今日任务已生成（幂等，可反复触发）
        viewModelScope.launch(Dispatchers.IO) {
            profileRepository.profileFlow.filterNotNull().collect { profile ->
                taskManager.ensureTodayTasks(profile)
            }
        }
        // 订阅今日任务并附带条目内容与连续天数
        viewModelScope.launch {
            taskManager.todayTasksFlow().collect { tasks ->
                val items = withContext(Dispatchers.IO) {
                    val data = entryRepository.entriesData()
                    tasks.map { t ->
                        TaskItem(t, data.byId[t.entryId], taskManager.streak(t.entryId))
                    }
                }
                _uiState.value = UiState(loading = false, items = items)
            }
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (task.done) taskManager.uncompleteTask(task.taskId)
            else taskManager.completeTask(task.taskId)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                TodayViewModel(c.taskManager, c.profileRepository, c.entryRepository)
            }
        }
    }
}
