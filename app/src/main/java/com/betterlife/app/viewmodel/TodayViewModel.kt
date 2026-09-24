package com.betterlife.app.viewmodel

import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

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

    /**
     * 今日页状态。资产/数据库读取失败不再让协程直接崩溃，而是落到 [Error] 并允许重试。
     * 没有档案、或当天没有任务时都表现为 [Ready] 且 items 为空，由界面给出对应引导。
     */
    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val items: List<TaskItem>) : UiState {
            val undoneCount: Int get() = items.count { !it.task.done }
        }
        data class Error(val message: String = LOAD_ERROR) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var tasksJob: Job? = null

    init {
        observeProfile()
        loadTasks()
    }

    /** 档案就绪后确保今日任务已生成（幂等，可反复触发） */
    private fun observeProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                profileRepository.profileFlow.filterNotNull().collect { profile ->
                    taskManager.ensureTodayTasks(profile)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "观察档案失败", t)
                _uiState.value = UiState.Error()
            }
        }
    }

    /** 订阅今日任务并附带条目内容与连续天数；失败时暴露为可重试的错误态 */
    private fun loadTasks() {
        tasksJob?.cancel()
        tasksJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                taskManager.todayTasksFlow().collect { tasks ->
                    val data = entryRepository.entriesData()
                    val items = tasks.map { t ->
                        TaskItem(t, data.byId[t.entryId], taskManager.streak(t.entryId))
                    }
                    _uiState.value = UiState.Ready(items)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "加载今日任务失败", t)
                _uiState.value = UiState.Error()
            }
        }
    }

    fun retry() {
        _uiState.value = UiState.Loading
        loadTasks()
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (task.done) taskManager.uncompleteTask(task.taskId)
            else taskManager.completeTask(task.taskId)
        }
    }

    companion object {
        private const val TAG = "TodayViewModel"
        const val LOAD_ERROR = "内容加载失败"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                TodayViewModel(c.taskManager, c.profileRepository, c.entryRepository)
            }
        }
    }
}
