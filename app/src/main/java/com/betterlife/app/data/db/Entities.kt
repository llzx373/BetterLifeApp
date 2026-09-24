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
    val type: String,            // DAILY | ONCE
    val date: String? = null,    // yyyy-MM-dd，仅 DAILY 使用
    val done: Boolean = false,
    val doneAt: Long? = null,
    val notified: Boolean = false,
    val createdAt: Long = 0L,
) {
    companion object {
        const val TYPE_DAILY = "DAILY"
        const val TYPE_ONCE = "ONCE"
    }
}

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
    }
}
