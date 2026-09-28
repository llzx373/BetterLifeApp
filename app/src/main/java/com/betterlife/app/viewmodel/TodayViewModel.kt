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
import com.betterlife.app.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class TodayViewModel(
    private val taskManager: TaskManager,
    private val profileRepository: ProfileRepository,
    private val entryRepository: EntryRepository,
    /** 任务数据变化后刷新桌面小部件；默认空实现，单元测试/预览不用碰 Context */
    private val refreshWidgets: suspend () -> Unit = {},
) : ViewModel() {

    data class TaskItem(
        val task: TaskEntity,
        val entry: EntryDto?,
        val streak: Int,
        /** 自定义任务（无条目库条目）的标题，来自 custom_entries */
        val customTitle: String? = null,
        /** 是否用户自选的每日习惯（STATE_DAILY）：请假/补卡入口只对它们开放 */
        val isDailyHabit: Boolean = false,
        /** 今天是否已请假：请假日不打卡也不断签，卡片退成安静的「已请假」 */
        val onLeaveToday: Boolean = false,
    ) {
        val displayTitle: String get() = entry?.title ?: customTitle ?: task.entryId

        /** 打卡来源：手动 "manual"、小部件 "widget"、Health Connect 自动核销 auto:hc */
        val doneBy: String get() = task.doneBy
    }

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

    /** 一次订阅里随任务流一起走的上游快照，请假日期流在下游按任务逐个挂 */
    private data class TasksSnapshot(
        val tasks: List<TaskEntity>,
        val customTitles: Map<String, String>,
        val dailyHabitIds: Set<String>,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observe()
    }

    /**
     * 档案 → 今天 → 今日任务 → 条目内容 的单向数据流。
     * 档案为 null 时直接进 [UiState.Empty] 且不生成任务；档案或日期变化都会重启下游订阅，
     * 跨天后自动为新日期规划任务并切换查询（进程跨夜存活时日期由 TaskManager.refreshToday 推进）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                profileRepository.profileFlow.collectLatest { p ->
                    if (p == null) {
                        _uiState.value = UiState.Empty
                        return@collectLatest
                    }
                    taskManager.todayFlow().collectLatest { date ->
                        taskManager.ensureTodayTasks(p, date)
                        combine(
                            taskManager.todayTasksFlow(date),
                            taskManager.customEntriesFlow(),
                            taskManager.userDailyIdsFlow(),
                        ) { tasks, customEntries, dailyIds ->
                            TasksSnapshot(
                                tasks,
                                customEntries.associate { it.entryId to it.title },
                                dailyIds.toSet(),
                            )
                        }.flatMapLatest { snapshot ->
                            // 请假状态是独立表：逐任务挂日期流，请假/取消请假才能即时反映到卡片
                            if (snapshot.tasks.isEmpty()) {
                                flowOf(snapshot to emptyList<Set<String>>())
                            } else {
                                combine(
                                    snapshot.tasks.map { t ->
                                        taskManager.streakLeaveDatesFlow(t.entryId).map { it.toSet() }
                                    },
                                ) { leaves -> snapshot to leaves.toList() }
                            }
                        }.collect { (snapshot, leavesPerTask) ->
                            val todayStr = date.toString()
                            val data = entryRepository.entriesData()
                            _uiState.value = UiState.Ready(
                                snapshot.tasks.mapIndexed { i, t ->
                                    TaskItem(
                                        task = t,
                                        entry = data.byId[t.entryId],
                                        streak = taskManager.streak(t.entryId),
                                        customTitle = snapshot.customTitles[t.entryId],
                                        isDailyHabit = t.entryId in snapshot.dailyHabitIds,
                                        onLeaveToday = leavesPerTask
                                            .getOrElse(i) { emptySet() }
                                            .contains(todayStr),
                                    )
                                },
                            )
                        }
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

    /** 打卡（C2）：[note] 为可选的随手记，空白按 null 存 */
    fun checkIn(task: TaskEntity, note: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.completeTask(task.taskId, note?.ifBlank { null })
            refreshWidgets()
        }
    }

    /** 撤销打卡 */
    fun undoCheckIn(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.uncompleteTask(task.taskId)
            refreshWidgets()
        }
    }

    /** B1：回前台时安静地跑一次 Health Connect 自动核销；无 HC/权限时是 no-op */
    fun autoCompleteByHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            if (taskManager.autoCompleteByHealth() > 0) refreshWidgets()
        }
    }

    /** B3 请假一天：当天不打卡也不断签（TaskManager 内部只对 STATE_DAILY 条目生效） */
    fun takeLeaveToday(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.takeLeave(entryId, LocalDate.now())
            refreshWidgets()
        }
    }

    /** 取消今天的请假，卡片回到可打卡状态 */
    fun cancelLeaveToday(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.cancelLeave(entryId, LocalDate.now())
            refreshWidgets()
        }
    }

    /**
     * B3 补昨天的卡。[onResult] 回 true 表示补上了，false 表示昨天已有记录（没动数据），
     * UI 据此弹对应的 snackbar。
     */
    fun backfillYesterday(entryId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val filled = if (taskManager.doneYesterday(entryId)) {
                false
            } else {
                taskManager.backfillYesterday(entryId)
                refreshWidgets()
                true
            }
            withContext(Dispatchers.Main) { onResult(filled) }
        }
    }

    /** 今天不做：只移除今天的安排，不把条目标记为不再推荐 */
    fun dropTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            taskManager.deleteTask(task.taskId)
            refreshWidgets()
        }
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
                TodayViewModel(c.taskManager, c.profileRepository, c.entryRepository) {
                    WidgetUpdater.refresh(app.applicationContext)
                }
            }
        }
    }
}
