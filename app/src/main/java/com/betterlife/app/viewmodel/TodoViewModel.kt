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

    private val _items = MutableStateFlow<List<TodoItem>>(emptyList())
    val items: StateFlow<List<TodoItem>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            combine(taskManager.todayTasksFlow(), taskManager.todoListFlow()) { daily, once -> daily + once }
                .collect { tasks ->
                    val data = withContext(Dispatchers.IO) { entryRepository.entriesData() }
                    _items.value = tasks.map { TodoItem(it, data.byId[it.entryId]) }
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
