package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.tasks.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TodoViewModel(
    private val taskManager: TaskManager,
    private val entryRepository: EntryRepository,
) : ViewModel() {

    data class TodoItem(val task: TaskEntity, val entry: EntryDto?)

    /**
     * 每日习惯与一次性待办分开给出:两类的语义和交互都不同,
     * 分区在 VM 里算一次,界面不做过滤也不重组重算。
     */
    data class UiState(
        val daily: List<TodoItem> = emptyList(),
        val once: List<TodoItem> = emptyList(),
    ) {
        val dailyDoneCount: Int get() = daily.count { it.task.done }
        val dailyAllDone: Boolean get() = daily.isNotEmpty() && dailyDoneCount == daily.size
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(taskManager.todayTasksFlow(), taskManager.todoListFlow()) { daily, once -> daily + once }
                .collect { tasks ->
                    val data = withContext(Dispatchers.IO) { entryRepository.entriesData() }
                    val items = tasks.map { TodoItem(it, data.byId[it.entryId]) }
                    _uiState.value = UiState(
                        daily = items.filter { it.task.type == TaskEntity.TYPE_DAILY },
                        once = items.filter { it.task.type == TaskEntity.TYPE_ONCE },
                    )
                }
        }
    }

    fun addTodo(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.addOneOffTodo(entryId) }
    }

    fun toggle(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (task.done) taskManager.uncompleteTask(task.taskId)
            else taskManager.completeTask(task.taskId)
        }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.deleteTask(task.taskId) }
    }

    /** 设置/清除单任务提醒时间（一天内分钟数，null = 清除、跟随全局汇总） */
    fun setTaskReminder(taskId: Long, minutes: Int?) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.setTaskReminder(taskId, minutes) }
    }

    /** 撤销删除:把同一条任务按原 id 写回 */
    fun restore(item: TodoItem) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.restoreTask(item.task) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                TodoViewModel(c.taskManager, c.entryRepository)
            }
        }
    }
}
