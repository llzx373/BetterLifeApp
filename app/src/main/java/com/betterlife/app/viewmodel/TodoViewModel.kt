package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.tasks.GRADUATION_CONSECUTIVE_WEEKS
import com.betterlife.app.tasks.TaskManager
import com.betterlife.app.tasks.consecutiveReachedWeeks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class TodoViewModel(
    private val taskManager: TaskManager,
    private val entryRepository: EntryRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    data class TodoItem(
        val task: TaskEntity,
        val entry: EntryDto?,
        /** 自定义任务（无条目库条目）的标题，来自 custom_entries */
        val customTitle: String? = null,
    ) {
        val displayTitle: String get() = entry?.title ?: customTitle ?: task.entryId
    }

    data class WeeklyEntry(
        val item: TaskManager.WeeklyItem,
        val entry: EntryDto?,
        val customTitle: String? = null,
    ) {
        val displayTitle: String get() = entry?.title ?: customTitle ?: item.habit.entryId
    }

    /** 「已养成」提示:每周习惯连续达标 [GRADUATION_CONSECUTIVE_WEEKS] 周且还没提示过 */
    data class GraduationPrompt(
        val entryId: String,
        val weeks: Int,
    )

    /** 自定义任务的类型，AddTaskDialog 的选择项 */
    enum class CustomTaskKind { ONCE, DAILY, WEEKLY }

    /**
     * 每日习惯、每周习惯与一次性待办分开给出:三类的语义和交互都不同,
     * 分区在 VM 里算一次,界面不做过滤也不重组重算。
     */
    data class UiState(
        val daily: List<TodoItem> = emptyList(),
        val weekly: List<WeeklyEntry> = emptyList(),
        val once: List<TodoItem> = emptyList(),
        /** 用户自选的每日习惯条目 id，决定行上是否显示「取消每日」 */
        val userDailyIds: Set<String> = emptySet(),
        /** 待展示的「已养成」提示 */
        val graduationPrompts: List<GraduationPrompt> = emptyList(),
    ) {
        val dailyDoneCount: Int get() = daily.count { it.task.done }
        val dailyAllDone: Boolean get() = daily.isNotEmpty() && dailyDoneCount == daily.size
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                taskManager.todayTasksFlow(),
                taskManager.todoListFlow(),
                taskManager.weeklyItemsFlow(),
                taskManager.userDailyIdsFlow(),
                taskManager.customEntriesFlow(),
            ) { dailyTasks, onceTasks, weeklyItems, userDaily, customEntries ->
                val data = withContext(Dispatchers.IO) { entryRepository.entriesData() }
                val customTitles = customEntries.associate { it.entryId to it.title }
                val tasks = (dailyTasks + onceTasks).map {
                    TodoItem(it, data.byId[it.entryId], customTitles[it.entryId])
                }
                UiState(
                    daily = tasks.filter { it.task.type == TaskEntity.TYPE_DAILY },
                    weekly = weeklyItems.map {
                        WeeklyEntry(it, data.byId[it.habit.entryId], customTitles[it.habit.entryId])
                    },
                    once = tasks.filter { it.task.type == TaskEntity.TYPE_ONCE },
                    userDailyIds = userDaily.toSet(),
                )
            }.collect { _uiState.value = it }
        }

        // 「已养成」提示:连续达标 N 周的每周习惯,一次性、可 dismiss;单独的流,
        // 不混进上面的主分区 combine(那里已经五路了)
        viewModelScope.launch {
            combine(
                taskManager.weeklyItemsFlow(),
                taskManager.weeklyHistoryFlow(),
                settingsStore.graduationPromptedFlow,
            ) { items, history, prompted ->
                val datesByEntry = history
                    .groupBy({ it.entryId }, { it.date })
                    .mapValues { (_, dates) -> dates.filterNotNull() }
                val today = LocalDate.now()
                items.mapNotNull { item ->
                    val weeks = consecutiveReachedWeeks(
                        datesByEntry[item.habit.entryId].orEmpty(),
                        item.habit.timesPerWeek,
                        today,
                    )
                    if (weeks >= GRADUATION_CONSECUTIVE_WEEKS && item.habit.entryId !in prompted) {
                        GraduationPrompt(item.habit.entryId, weeks)
                    } else {
                        null
                    }
                }
            }.collect { prompts ->
                _uiState.update { it.copy(graduationPrompts = prompts) }
            }
        }
    }

    /** 「知道了」:这个习惯提示过一次就不再打扰 */
    fun dismissGraduation(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsStore.addGraduationPrompted(entryId) }
    }

    fun addTodo(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.addOneOffTodo(entryId) }
    }

    /** 新增自定义任务：一次性（可带到期日） / 每日 / 每周（每周用 [timesPerWeek]） */
    fun addCustom(
        title: String,
        kind: CustomTaskKind,
        timesPerWeek: Int = 3,
        dueDate: LocalDate? = null,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            when (kind) {
                CustomTaskKind.ONCE -> taskManager.addCustomOnce(title, dueDate)
                CustomTaskKind.DAILY -> taskManager.addCustomDaily(title)
                CustomTaskKind.WEEKLY -> taskManager.addCustomWeekly(title, timesPerWeek)
            }
        }
    }

    /** 设置/清除一次性待办的到期日（null = 清除），已设提醒的会按新日期重排 */
    fun setOnceDueDate(taskId: Long, dueDate: LocalDate?) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.setOnceDueDate(taskId, dueDate) }
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

    /** 一次性待办 → 每日习惯 */
    fun convertToDaily(item: TodoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.convertToDaily(item.task.entryId, item.task.taskId)
        }
    }

    /** 取消用户自选的每日习惯 */
    fun removeDailyHabit(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.removeDailyHabit(entryId) }
    }

    /** 撤销「取消每日习惯」：自定义习惯的标题一并恢复（否则行回来了标题没了） */
    fun restoreDailyHabit(entryId: String, customTitle: String?) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.restoreDailyHabit(entryId, customTitle) }
    }

    /** 一次性待办 → 每周习惯（一周 [timesPerWeek] 次） */
    fun convertToWeekly(item: TodoItem, timesPerWeek: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.convertToWeekly(item.task.entryId, item.task.taskId, timesPerWeek)
        }
    }

    /** 每周习惯今日打卡开关 */
    fun toggleWeekly(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.toggleWeeklyToday(entryId) }
    }

    fun removeWeekly(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) { taskManager.removeWeeklyHabit(entryId) }
    }

    /** 撤销移除每周习惯：恢复模板（自定义习惯的标题一并恢复） */
    fun restoreWeekly(entry: WeeklyEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.restoreWeeklyHabit(entry.item.habit, entry.customTitle)
        }
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
                TodoViewModel(c.taskManager, c.entryRepository, c.settingsStore)
            }
        }
    }
}
