package com.betterlife.app.tasks

import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.Profile
import com.betterlife.app.data.db.EntryStateDao
import com.betterlife.app.data.db.EntryStateEntity
import com.betterlife.app.data.db.TaskDao
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.recommend.DailyTaskPlanner
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 任务协调层：把 DailyTaskPlanner 的规划结果落到 Room，并处理打卡、待办与连续天数。
 *
 * 同时持有 [ReminderScheduler]：凡是动到任务生命周期（新建/打卡/删除/恢复/改提醒时间）
 * 的地方都在这里同步重排或取消对应的单任务提醒 work，ViewModel 不需要各自编排。
 */
class TaskManager(
    private val taskDao: TaskDao,
    private val entryStateDao: EntryStateDao,
    private val planner: DailyTaskPlanner,
    private val entryRepository: EntryRepository,
    private val reminderScheduler: ReminderScheduler,
) : TimerTaskGateway {

    /** 当天没有任何 DAILY 任务时，按档案规划并插入；幂等，可反复调用 */
    suspend fun ensureTodayTasks(profile: Profile, date: LocalDate = LocalDate.now()) {
        val dateStr = date.toString()
        if (taskDao.countDailyByDate(dateStr) > 0) return
        val data = entryRepository.entriesData()
        val excluded = entryStateDao.excludedIds().toSet()
        val planned = planner.plan(date, profile, data.entries, data.rules, excluded)
        if (planned.isEmpty()) return
        val now = System.currentTimeMillis()
        // 继承同条目最近一次设过的提醒时间：每天的任务是新建的行，
        // 不继承的话「每天 8:30 提醒我」只生效一天。
        val tasks = planned.map {
            TaskEntity(
                entryId = it.id,
                type = TaskEntity.TYPE_DAILY,
                date = dateStr,
                remindAtMinutes = taskDao.lastDailyReminderMinutes(it.id),
                createdAt = now,
            )
        }
        val ids = taskDao.insertAll(tasks)
        tasks.zip(ids).forEach { (task, id) ->
            task.remindAtMinutes?.let { reminderScheduler.scheduleTaskReminder(id, it) }
        }
    }

    override suspend fun completeTask(taskId: Long) {
        taskDao.markDone(taskId, System.currentTimeMillis())
        // 已完成的任务的提醒 work 触发时也会自查 done 跳过，提前取消省一次唤醒
        reminderScheduler.cancelTaskReminder(taskId)
    }

    override suspend fun taskTitle(taskId: Long): String? {
        val task = taskDao.getTask(taskId) ?: return null
        return entryRepository.entriesData().byId[task.entryId]?.title
    }

    suspend fun uncompleteTask(taskId: Long) = taskDao.markUndone(taskId)

    /** 把条目加入一次性待办，并把条目标记为 TODO 状态 */
    suspend fun addOneOffTodo(entryId: String) {
        taskDao.insert(
            TaskEntity(
                entryId = entryId,
                type = TaskEntity.TYPE_ONCE,
                date = null,
                createdAt = System.currentTimeMillis(),
            )
        )
        entryStateDao.upsert(
            EntryStateEntity(entryId, EntryStateEntity.STATE_TODO, System.currentTimeMillis())
        )
    }

    suspend fun deleteTask(taskId: Long) {
        taskDao.delete(taskId)
        reminderScheduler.cancelTaskReminder(taskId)
    }

    /** 撤销删除:按原 taskId 把任务写回,顺序与状态都保持不变 */
    suspend fun restoreTask(task: TaskEntity) {
        taskDao.insert(task)
        task.remindAtMinutes?.let { reminderScheduler.scheduleTaskReminder(task.taskId, it) }
    }

    /** 设置/清除单任务提醒时间（一天内分钟数，null = 跟随全局汇总），并同步重排 work */
    suspend fun setTaskReminder(taskId: Long, minutes: Int?) {
        taskDao.setReminder(taskId, minutes)
        if (minutes != null) reminderScheduler.scheduleTaskReminder(taskId, minutes)
        else reminderScheduler.cancelTaskReminder(taskId)
    }

    suspend fun getTask(taskId: Long): TaskEntity? = taskDao.getTask(taskId)

    suspend fun markNotified(taskId: Long) = taskDao.markNotified(taskId)

    /**
     * 换一条：把当前条目标记为「不再推荐」（DISMISSED，会被推荐引擎排除），
     * 删除今天的这条任务，再按档案补一条当天还没有的顶上。
     *
     * 与「今天不做」区分：后者只是 [deleteTask] 移除今天的安排，
     * 不写 DISMISSED，所以这条内容以后还可能被推荐回来。
     */
    suspend fun replaceTodayTask(profile: Profile, task: TaskEntity, date: LocalDate = LocalDate.now()) {
        val dateStr = date.toString()
        addEntryState(task.entryId, EntryStateEntity.STATE_DISMISSED)
        deleteTask(task.taskId)
        val data = entryRepository.entriesData()
        val taken = entryStateDao.excludedIds().toSet() + taskDao.dailyEntryIds(dateStr)
        val next = planner.plan(date, profile, data.entries, data.rules, taken).firstOrNull() ?: return
        taskDao.insert(
            TaskEntity(
                entryId = next.id,
                type = TaskEntity.TYPE_DAILY,
                date = dateStr,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * 打上某种条目状态。
     *
     * 状态之间是正交的：加收藏不会顶掉「加入待办」，标记不再推荐也不会清掉收藏
     * （主键是 (entryId, state)，见 [EntryStateEntity]）。
     */
    suspend fun addEntryState(entryId: String, state: String) {
        entryStateDao.upsert(EntryStateEntity(entryId, state, System.currentTimeMillis()))
    }

    /** 清除某种条目状态，不影响该条目的其他状态 */
    suspend fun removeEntryState(entryId: String, state: String) {
        entryStateDao.delete(entryId, state)
    }

    /** 收藏开关 */
    suspend fun setFavorite(entryId: String, favorite: Boolean) {
        if (favorite) addEntryState(entryId, EntryStateEntity.STATE_FAVORITE)
        else removeEntryState(entryId, EntryStateEntity.STATE_FAVORITE)
    }

    /** 已收藏的条目 id */
    fun favoriteIdsFlow(): Flow<List<String>> =
        entryStateDao.entryIdsByStateFlow(EntryStateEntity.STATE_FAVORITE)

    /** 「不再推荐」开关:DISMISSED 会被推荐引擎排除,不影响收藏/待办等其他状态 */
    suspend fun setDismissed(entryId: String, dismissed: Boolean) {
        if (dismissed) addEntryState(entryId, EntryStateEntity.STATE_DISMISSED)
        else removeEntryState(entryId, EntryStateEntity.STATE_DISMISSED)
    }

    /** 已屏蔽（不再推荐）的条目 id */
    fun dismissedIdsFlow(): Flow<List<String>> =
        entryStateDao.entryIdsByStateFlow(EntryStateEntity.STATE_DISMISSED)

    fun todayTasksFlow(date: LocalDate = LocalDate.now()): Flow<List<TaskEntity>> =
        taskDao.dailyTasksFlow(date.toString())

    fun todoListFlow(): Flow<List<TaskEntity>> = taskDao.onceTasksFlow()

    /**
     * 某条目 DAILY 任务的连续完成天数。
     * 今天还没完成时从昨天往前数，避免「今天还没打卡就清零」的误导。
     */
    suspend fun streak(entryId: String, today: LocalDate = LocalDate.now()): Int {
        val doneDates = taskDao.doneDates(entryId).toHashSet()
        var cursor = today
        if (cursor.toString() !in doneDates) cursor = cursor.minusDays(1)
        var count = 0
        while (cursor.toString() in doneDates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    suspend fun todayUndoneCount(date: LocalDate = LocalDate.now()): Int =
        taskDao.countUndoneDaily(date.toString())
}
