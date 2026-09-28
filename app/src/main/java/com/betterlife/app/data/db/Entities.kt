package com.betterlife.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 用户档案，单行存储（id 固定为 1）。多选字段存逗号分隔字符串。 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val ageRange: String = "26-35",
    val gender: String = "other",
    val smoking: String = "no",
    val secondhandSmoke: Boolean = false,
    val alcohol: String = "no",
    val betelNut: Boolean = false,
    val sugaryDrinks: String = "no",
    val exercise: String = "none",
    val sleepShort: Boolean = false,
    /** 逗号分隔：hypertension,diabetes,kidney,heart,other；空串 = 无 */
    val chronic: String = "",
    val occupation: String = "other",
    val financialStress: Boolean = false,
    val housing: String = "rent",
    val children: String = "none",
    val hasElderly: Boolean = false,
    val pregnant: Boolean = false,
    val planningAbroad: Boolean = false,
    /** 逗号分隔：health,money,time,career,family,relax */
    val goals: String = "",
)

@Entity(
    tableName = "tasks",
    indices = [Index("date"), Index("entryId"), Index("type", "date")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val taskId: Long = 0,
    val entryId: String,
    val type: String,            // DAILY | ONCE | WEEKLY
    val date: String? = null,    // yyyy-MM-dd，DAILY / WEEKLY 使用（WEEKLY 为实际打卡日）
    val done: Boolean = false,
    val doneAt: Long? = null,
    val notified: Boolean = false,
    /** 一天内的分钟数（0-1439）；null = 不单独提醒，跟随全局汇总通知 */
    val remindAtMinutes: Int? = null,
    val createdAt: Long = 0L,
    /** 完成时随手记的备注 */
    val note: String? = null,
    /** 完成来源：manual（手动打卡）等，便于统计与去重 */
    val doneBy: String = "manual",
    /** 截止日期（yyyy-MM-dd），只对 ONCE 任务有意义 */
    val dueDate: String? = null,
) {
    companion object {
        const val TYPE_DAILY = "DAILY"
        const val TYPE_ONCE = "ONCE"
        /** 每周习惯的打卡记录：每次打卡一行，date 为实际打卡日，done 恒为 true */
        const val TYPE_WEEKLY = "WEEKLY"
    }
}

/** 每周习惯模板：一周目标完成 [timesPerWeek] 次；打卡记录是 tasks 表里的 WEEKLY 行 */
@Entity(tableName = "weekly_habits")
data class WeeklyHabitEntity(
    @PrimaryKey val entryId: String,
    val timesPerWeek: Int,
    val createdAt: Long,
)

/**
 * 用户自建任务的标题存储。自定义任务没有条目库条目，
 * id 统一 "custom:<UUID>" 前缀（见 TaskManager.isCustomEntryId），标题存在这里。
 */
@Entity(tableName = "custom_entries")
data class CustomEntryEntity(
    @PrimaryKey val entryId: String,
    val title: String,
    val createdAt: Long,
)

/** 条目级备注：每条目一行，REPLACE 覆盖更新 */
@Entity(tableName = "entry_notes")
data class EntryNoteEntity(
    @PrimaryKey val entryId: String,
    val text: String,
    val updatedAt: Long,
)

/** 连续打卡的「请假」日：请假当天不断签，(entryId, date) 去重 */
@Entity(tableName = "streak_leaves", primaryKeys = ["entryId", "date"])
data class StreakLeaveEntity(
    val entryId: String,
    /** yyyy-MM-dd */
    val date: String,
    val createdAt: Long,
)

/** AI 对话记录，按 id 升序即时间序；role 为 "user" / "assistant" */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    /** 生成该条回复的模型提供方；用户消息为 null */
    val providerName: String? = null,
    val createdAt: Long,
)

/**
 * 条目级状态。主键是 (entryId, state) 而不是单独的 entryId —— 状态之间是**正交**的:
 * 一个条目可以既被加入待办(TODO)、又被收藏(FAVORITE);用单主键会互相覆盖。
 *
 * 推荐引擎只看 DONE / DISMISSED 两种状态(见 EntryStateDao.excludedIds)。
 */
@Entity(tableName = "entry_states", primaryKeys = ["entryId", "state"])
data class EntryStateEntity(
    val entryId: String,
    val state: String,
    val updatedAt: Long,
) {
    companion object {
        const val STATE_TODO = "TODO"
        const val STATE_DONE = "DONE"
        const val STATE_DISMISSED = "DISMISSED"
        const val STATE_FAVORITE = "FAVORITE"
        /** 用户自选的每日习惯：ensureTodayTasks 每天把它落成当天的 DAILY 任务行 */
        const val STATE_DAILY = "DAILY"
    }
}
