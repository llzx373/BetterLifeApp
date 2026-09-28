package com.betterlife.app.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.betterlife.app.data.content.BundledContent
import com.betterlife.app.data.content.LegacyEntryId
import com.betterlife.app.data.db.AppDatabase
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.tasks.ReminderScheduler
import java.time.LocalDate

/**
 * 本地 JSON 备份的导出/导入。
 * 只覆盖 7 张持久表（见 [BackupPayload]）：chat_messages 不备份，
 * DataStore 里的 API key 等敏感配置不出设备。
 *
 * 旧版备份里的条目 id 是位置序号 "SS-NN"，导入时按捆绑 entries.json 的
 * id→key 映射改写成稳定 key（映射不上的原样保留，如 "custom:" 前缀行）。
 */
class BackupManager(
    private val database: AppDatabase,
    private val reminderScheduler: ReminderScheduler,
    private val context: Context,
) {

    /** SS-NN → 稳定 key 映射，首次导入时才解析 assets，随实例缓存 */
    private val legacyIdToKey: Map<String, String> by lazy { BundledContent.legacyIdToKey(context) }

    /** 导出全部持久表为 JSON 字符串；单事务读取保证一致性快照，写入文件/分享由调用方负责 */
    suspend fun exportJson(): String {
        val payload = database.withTransaction {
            BackupCodec.payloadOf(
                exportedAt = System.currentTimeMillis(),
                profile = database.profileDao().getProfile(),
                tasks = database.taskDao().all(),
                entryStates = database.entryStateDao().allStates(),
                weeklyHabits = database.weeklyHabitDao().all(),
                customEntries = database.customEntryDao().all(),
                entryNotes = database.entryNoteDao().all(),
                streakLeaves = database.streakLeaveDao().all(),
            )
        }
        return BackupCodec.encode(payload)
    }

    /**
     * 导入备份：先完整解析+校验版本，全部通过后才在单个事务里清空 7 张表并写入，
     * 任何一步失败都回滚，现有数据不变。成功返回导入的行数（含 profile 行）。
     * taskId 原样保留插入（Room 显式主键插入），单任务提醒的 work 名与通知 id 保持稳定。
     */
    suspend fun importJson(json: String): Result<Int> {
        val payload = BackupCodec.decode(json).getOrElse { return Result.failure(it) }.remapEntryIds()
        return runCatching {
            val count = database.withTransaction {
                database.profileDao().deleteAll()
                database.taskDao().deleteAll()
                database.entryStateDao().deleteAll()
                database.weeklyHabitDao().deleteAll()
                database.customEntryDao().deleteAll()
                database.entryNoteDao().deleteAll()
                database.streakLeaveDao().deleteAll()

                payload.profile?.let { database.profileDao().upsert(it.toEntity()) }
                database.taskDao().insertAll(payload.tasks.map { it.toEntity() })
                database.entryStateDao().insertAll(payload.entryStates.map { it.toEntity() })
                database.weeklyHabitDao().insertAll(payload.weeklyHabits.map { it.toEntity() })
                database.customEntryDao().insertAll(payload.customEntries.map { it.toEntity() })
                database.entryNoteDao().insertAll(payload.entryNotes.map { it.toEntity() })
                database.streakLeaveDao().insertAll(payload.streakLeaves.map { it.toEntity() })

                (if (payload.profile != null) 1 else 0) +
                    payload.tasks.size + payload.entryStates.size + payload.weeklyHabits.size +
                    payload.customEntries.size + payload.entryNotes.size + payload.streakLeaves.size
            }
            // 事务提交成功后才重排提醒；失败会抛异常走 failure，不会动到 WorkManager
            rescheduleRemindersAfterImport()
            count
        }
    }

    /**
     * 旧版（SS-NN）条目 id → 稳定 key 改写。只动匹配 `SS-NN` 形态的 entryId，
     * 已是 key 或 "custom:" 前缀的行原样保留；映射不上（上游已删的条目）也保留原值。
     * customEntries 的 id 恒为 "custom:<UUID>"，不在改写范围内。
     */
    private fun BackupPayload.remapEntryIds(): BackupPayload {
        fun remap(id: String): String = LegacyEntryId.remap(id, legacyIdToKey)
        return copy(
            tasks = tasks.map { it.copy(entryId = remap(it.entryId)) },
            entryStates = entryStates.map { it.copy(entryId = remap(it.entryId)) },
            weeklyHabits = weeklyHabits.map { it.copy(entryId = remap(it.entryId)) },
            entryNotes = entryNotes.map { it.copy(entryId = remap(it.entryId)) },
            streakLeaves = streakLeaves.map { it.copy(entryId = remap(it.entryId)) },
        )
    }

    /**
     * 导入后重排单任务提醒：一次性待办（ONCE）未完成且设了提醒时间的重新排 work。
     * taskId 已保留，enqueueUniqueWork(REPLACE) 会覆盖同名旧 work；DAILY 任务的提醒
     * 由 ensureTodayTasks 次日继承重排，不在此处理。
     * 导入时被清掉提醒/任务留下的过期 work 无法枚举，依赖 worker 自查（任务不存在或已通知则跳过）。
     */
    suspend fun rescheduleRemindersAfterImport() {
        for (task in database.taskDao().all()) {
            val minutes = task.remindAtMinutes ?: continue
            if (task.type != TaskEntity.TYPE_ONCE || task.done) continue
            val dueDate = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            reminderScheduler.scheduleTaskReminder(task.taskId, minutes, dueDate)
        }
    }
}
