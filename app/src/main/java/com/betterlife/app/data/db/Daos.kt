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

    /** 全表清空，备份导入用 */
    @Query("DELETE FROM profile")
    suspend fun deleteAll()
}

/** 提醒继承用的 DAILY 行最小投影（全行回读没必要） */
data class DailyReminderRow(
    val taskId: Long,
    val date: String?,
    val remindAtMinutes: Int?,
)

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

    @Query("UPDATE tasks SET done = 1, doneAt = :doneAt, note = :note, doneBy = :doneBy WHERE taskId = :taskId")
    suspend fun markDone(taskId: Long, doneAt: Long, note: String? = null, doneBy: String = "manual")

    @Query("UPDATE tasks SET done = 0, doneAt = NULL WHERE taskId = :taskId")
    suspend fun markUndone(taskId: Long)

    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    suspend fun getTask(taskId: Long): TaskEntity?

    /** 设置/清除单任务提醒时间；改时间同时清掉 notified，让新时间的提醒能再发 */
    @Query("UPDATE tasks SET remindAtMinutes = :minutes, notified = 0 WHERE taskId = :taskId")
    suspend fun setReminder(taskId: Long, minutes: Int?)

    /** 设置/清除一次性待办的截止日；改日期同时清掉 notified，让新日期的提醒能再发 */
    @Query("UPDATE tasks SET dueDate = :dueDate, notified = 0 WHERE taskId = :taskId")
    suspend fun setDueDate(taskId: Long, dueDate: String?)

    @Query("UPDATE tasks SET notified = 1 WHERE taskId = :taskId")
    suspend fun markNotified(taskId: Long)

    /** 同条目全部 DAILY 行的提醒设置历史，次日继承时取最近一次（见 inheritedReminderMinutes） */
    @Query("SELECT taskId, date, remindAtMinutes FROM tasks WHERE entryId = :entryId AND type = 'DAILY'")
    suspend fun dailyReminderHistory(entryId: String): List<DailyReminderRow>

    @Query("DELETE FROM tasks WHERE taskId = :taskId")
    suspend fun delete(taskId: Long)

    /** 某条目 DAILY 任务的完成日期（降序），用于计算连续天数。date 为空的记录不参与统计。 */
    @Query("SELECT date FROM tasks WHERE entryId = :entryId AND type = 'DAILY' AND done = 1 AND date IS NOT NULL ORDER BY date DESC")
    suspend fun doneDates(entryId: String): List<String>

    /** 今天某条目已有的 DAILY 行（转入每日时避免重复插入） */
    @Query("SELECT * FROM tasks WHERE type = 'DAILY' AND date = :date AND entryId = :entryId")
    suspend fun dailyTaskByEntry(entryId: String, date: String): TaskEntity?

    /** 一周区间 [start, end] 内的 WEEKLY 打卡行，聚合每周习惯进度用 */
    @Query("SELECT * FROM tasks WHERE type = 'WEEKLY' AND date >= :start AND date <= :end")
    fun weeklyCheckinsFlow(start: String, end: String): Flow<List<TaskEntity>>

    /** 撤销某条目某天的 WEEKLY 打卡，返回删除的行数（0 = 今天没打过卡） */
    @Query("DELETE FROM tasks WHERE type = 'WEEKLY' AND entryId = :entryId AND date = :date")
    suspend fun deleteWeeklyCheckin(entryId: String, date: String): Int

    /** 删除某条目全部 WEEKLY 打卡行（移除每周习惯时清理历史） */
    @Query("DELETE FROM tasks WHERE type = 'WEEKLY' AND entryId = :entryId")
    suspend fun deleteWeeklyCheckins(entryId: String)

    /** 全表读取，备份导出用 */
    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    /** 全表任务流：统计页在任意打卡变化后重算 */
    @Query("SELECT * FROM tasks")
    fun allFlow(): Flow<List<TaskEntity>>

    /** 全表清空，备份导入用 */
    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface WeeklyHabitDao {
    @Query("SELECT * FROM weekly_habits ORDER BY createdAt")
    fun allFlow(): Flow<List<WeeklyHabitEntity>>

    /** 全表读取，备份导出用 */
    @Query("SELECT * FROM weekly_habits")
    suspend fun all(): List<WeeklyHabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(habit: WeeklyHabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(habits: List<WeeklyHabitEntity>)

    @Query("DELETE FROM weekly_habits WHERE entryId = :entryId")
    suspend fun delete(entryId: String)

    /** 全表清空，备份导入用 */
    @Query("DELETE FROM weekly_habits")
    suspend fun deleteAll()
}

@Dao
interface CustomEntryDao {
    @Query("SELECT * FROM custom_entries ORDER BY createdAt")
    fun allFlow(): Flow<List<CustomEntryEntity>>

    @Query("SELECT * FROM custom_entries")
    suspend fun all(): List<CustomEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CustomEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CustomEntryEntity>)

    @Query("SELECT title FROM custom_entries WHERE entryId = :entryId")
    suspend fun title(entryId: String): String?

    @Query("DELETE FROM custom_entries WHERE entryId = :entryId")
    suspend fun delete(entryId: String)

    /** 全表清空，备份导入用 */
    @Query("DELETE FROM custom_entries")
    suspend fun deleteAll()
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

    /** 同上的挂起版本，ensureTodayTasks 读取用户自选每日习惯用 */
    @Query("SELECT entryId FROM entry_states WHERE state = :state")
    suspend fun entryIdsByState(state: String): List<String>

    /** 全表读取，备份导出用 */
    @Query("SELECT * FROM entry_states")
    suspend fun allStates(): List<EntryStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<EntryStateEntity>)

    /** 全表清空，备份导入用 */
    @Query("DELETE FROM entry_states")
    suspend fun deleteAll()
}

@Dao
interface EntryNoteDao {
    @Query("SELECT * FROM entry_notes WHERE entryId = :entryId")
    fun noteFlow(entryId: String): Flow<EntryNoteEntity?>

    @Query("SELECT * FROM entry_notes WHERE entryId = :entryId")
    suspend fun note(entryId: String): EntryNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: EntryNoteEntity)

    @Query("DELETE FROM entry_notes WHERE entryId = :entryId")
    suspend fun delete(entryId: String)

    /** 全表读取，备份导出用 */
    @Query("SELECT * FROM entry_notes")
    suspend fun all(): List<EntryNoteEntity>

    /** 全表笔记流：收藏列表的笔记预览随编辑实时更新 */
    @Query("SELECT * FROM entry_notes")
    fun allFlow(): Flow<List<EntryNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<EntryNoteEntity>)

    /** 全表清空，备份导入用 */
    @Query("DELETE FROM entry_notes")
    suspend fun deleteAll()
}

@Dao
interface StreakLeaveDao {
    /** 请假日（降序），展示与连签计算共用 */
    @Query("SELECT date FROM streak_leaves WHERE entryId = :entryId ORDER BY date DESC")
    fun datesFlow(entryId: String): Flow<List<String>>

    @Query("SELECT date FROM streak_leaves WHERE entryId = :entryId ORDER BY date DESC")
    suspend fun dates(entryId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(leave: StreakLeaveEntity)

    @Query("DELETE FROM streak_leaves WHERE entryId = :entryId AND date = :date")
    suspend fun delete(entryId: String, date: String)

    /** 全表读取，备份导出用 */
    @Query("SELECT * FROM streak_leaves")
    suspend fun all(): List<StreakLeaveEntity>

    /** 全表请假流：统计页连签计算随请假变化重算 */
    @Query("SELECT * FROM streak_leaves")
    fun allFlow(): Flow<List<StreakLeaveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(leaves: List<StreakLeaveEntity>)

    /** 全表清空，备份导入用 */
    @Query("DELETE FROM streak_leaves")
    suspend fun deleteAll()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun allFlow(): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(msg: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clear()

    /** 只保留最新 [keep] 条，超出部分从最旧的删起（聊天记录封顶用） */
    @Query("DELETE FROM chat_messages WHERE id NOT IN (SELECT id FROM chat_messages ORDER BY id DESC LIMIT :keep)")
    suspend fun trimToLatest(keep: Int)
}
