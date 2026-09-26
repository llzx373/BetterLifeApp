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
import com.betterlife.app.data.Profile
import com.betterlife.app.data.ProfileRepository
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.tasks.TaskManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
     *
     * [Empty] 专指「还没有档案」——这时推荐质量无从谈起，整页应该引导去填档案，
     * 而不是显示一个空的今日任务区。有档案但当天没任务是 [Ready] 且 items 为空。
     */
    sealed interface UiState {
        data object Loading : UiState
        data object Empty : UiState
        data class Ready(val items: List<TaskItem>) : UiState {
            val undoneCount: Int get() = items.count { !it.task.done }
            val allDone: Boolean get() = items.isNotEmpty() && items.all { it.task.done }
        }
        data object Error : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 换一条要用到完整档案，随数据流缓存下来 */
    @Volatile
    private var profile: Profile? = null

    private var observeJob: Job? = null

    init {
        observe()
    }

    /**
     * 档案 → 今日任务 → 条目内容 的单向数据流。
     * 档案为 null 时直接进 [UiState.Empty] 且不生成任务；档案变化会重启下游订阅。
     */
    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                profileRepository.profileFlow.collectLatest { p ->
                    profile = p
                    if (p == null) {
                        _uiState.value = UiState.Empty
                        return@collectLatest
                    }
                    taskManager.ensureTodayTasks(p)
                    taskManager.todayTasksFlow().collect { tasks ->
                        val data = entryRepository.entriesData()
                        _uiState.value = UiState.Ready(
                            tasks.map { t ->
                                TaskItem(t, data.byId[t.entryId], taskManager.streak(t.entryId))
                            },
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "observe failed", t)
                _uiState.value = UiState.Error
            }
        }
    }

    fun retry() {
        _uiState.value = UiState.Loading
        observe()
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (task.done) taskManager.uncompleteTask(task.taskId)
            else taskManager.completeTask(task.taskId)
        }
    }

    /** 换一条：当前这条不再推荐，补一条新的顶上 */
    fun swapTask(task: TaskEntity) {
        val p = profile ?: return
        viewModelScope.launch(Dispatchers.IO) { taskManager.replaceTodayTask(p, task) }
    }

    /** 今天不做：只移除今天的安排，不把条目标记为不再推荐 */
    fun dropTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.deleteTask(task.taskId) }
    }

    /** 设置/清除单任务提醒时间（一天内分钟数，null = 清除、跟随全局汇总） */
    fun setTaskReminder(taskId: Long, minutes: Int?) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.setTaskReminder(taskId, minutes) }
    }

    companion object {
        private const val TAG = "TodayViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                TodayViewModel(c.taskManager, c.profileRepository, c.entryRepository)
            }
        }
    }
}
