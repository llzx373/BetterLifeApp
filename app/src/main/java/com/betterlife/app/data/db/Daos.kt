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

    @Query("DELETE FROM entry_states WHERE entryId = :entryId")
    suspend fun delete(entryId: String)

    /** 推荐引擎需要排除的条目 id：DONE 或 DISMISSED */
    @Query("SELECT entryId FROM entry_states WHERE state IN ('DONE', 'DISMISSED')")
    suspend fun excludedIds(): List<String>

    @Query("SELECT * FROM entry_states")
    fun allStatesFlow(): Flow<List<EntryStateEntity>>
}
