package com.betterlife.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun profileFlow(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE type = 'DAILY' AND date = :date ORDER BY taskId")
    fun dailyTasksFlow(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE type = 'ONCE' ORDER BY done, createdAt")
    fun onceTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE type = 'DAILY' AND date = :date")
    suspend fun countDailyByDate(date: String): Int

    /** 当天已有的 DAILY 条目 id,「换一条」补位时用来避免重复 */
    @Query("SELECT entryId FROM tasks WHERE type = 'DAILY' AND date = :date")
    suspend fun dailyEntryIds(date: String): List<String>

    @Query("SELECT COUNT(*) FROM tasks WHERE type = 'DAILY' AND date = :date AND done = 0")
    suspend fun countUndoneDaily(date: String): Int

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Query("UPDATE tasks SET done = 1, doneAt = :doneAt WHERE taskId = :taskId")
    suspend fun markDone(taskId: Long, doneAt: Long)

    @Query("UPDATE tasks SET done = 0, doneAt = NULL WHERE taskId = :taskId")
    suspend fun markUndone(taskId: Long)

    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    suspend fun getTask(taskId: Long): TaskEntity?

    /** 设置/清除单任务提醒时间；改时间同时清掉 notified，让新时间的提醒能再发 */
    @Query("UPDATE tasks SET remindAtMinutes = :minutes, notified = 0 WHERE taskId = :taskId")
    suspend fun setReminder(taskId: Long, minutes: Int?)

    @Query("UPDATE tasks SET notified = 1 WHERE taskId = :taskId")
    suspend fun markNotified(taskId: Long)

    /** 同条目最近一次设过提醒的 DAILY 任务的时间，新建次日任务时继承 */
    @Query(
        "SELECT remindAtMinutes FROM tasks WHERE entryId = :entryId AND type = 'DAILY'" +
            " AND remindAtMinutes IS NOT NULL ORDER BY taskId DESC LIMIT 1"
    )
    suspend fun lastDailyReminderMinutes(entryId: String): Int?

    @Query("DELETE FROM tasks WHERE taskId = :taskId")
    suspend fun delete(taskId: Long)

    /** 某条目 DAILY 任务的完成日期（降序），用于计算连续天数。date 为空的记录不参与统计。 */
    @Query("SELECT date FROM tasks WHERE entryId = :entryId AND type = 'DAILY' AND done = 1 AND date IS NOT NULL ORDER BY date DESC")
    suspend fun doneDates(entryId: String): List<String>
}

@Dao
interface EntryStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: EntryStateEntity)

    @Query("DELETE FROM entry_states WHERE entryId = :entryId AND state = :state")
    suspend fun delete(entryId: String, state: String)

    /** 推荐引擎需要排除的条目 id：DONE 或 DISMISSED */
    @Query("SELECT DISTINCT entryId FROM entry_states WHERE state IN ('DONE', 'DISMISSED')")
    suspend fun excludedIds(): List<String>

    @Query("SELECT * FROM entry_states")
    fun allStatesFlow(): Flow<List<EntryStateEntity>>

    /** 处于某个状态的条目 id，收藏列表与收藏态判断用 */
    @Query("SELECT entryId FROM entry_states WHERE state = :state")
    fun entryIdsByStateFlow(state: String): Flow<List<String>>
}
