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
 */
class TaskManager(
    private val taskDao: TaskDao,
    private val entryStateDao: EntryStateDao,
    private val planner: DailyTaskPlanner,
    private val entryRepository: EntryRepository,
) {

    /** 当天没有任何 DAILY 任务时，按档案规划并插入；幂等，可反复调用 */
    suspend fun ensureTodayTasks(profile: Profile, date: LocalDate = LocalDate.now()) {
        val dateStr = date.toString()
        if (taskDao.countDailyByDate(dateStr) > 0) return
        val data = entryRepository.entriesData()
        val excluded = entryStateDao.excludedIds().toSet()
        val planned = planner.plan(date, profile, data.entries, data.rules, excluded)
        if (planned.isEmpty()) return
        val now = System.currentTimeMillis()
        taskDao.insertAll(planned.map {
            TaskEntity(entryId = it.id, type = TaskEntity.TYPE_DAILY, date = dateStr, createdAt = now)
        })
    }

    suspend fun completeTask(taskId: Long) =
        taskDao.markDone(taskId, System.currentTimeMillis())

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

    suspend fun deleteTask(taskId: Long) = taskDao.delete(taskId)

    /** 条目状态操作：DONE / DISMISSED / TODO；传 null 清除状态（恢复可推荐） */
    suspend fun setEntryState(entryId: String, state: String?) {
        if (state == null) entryStateDao.delete(entryId)
        else entryStateDao.upsert(EntryStateEntity(entryId, state, System.currentTimeMillis()))
    }

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
