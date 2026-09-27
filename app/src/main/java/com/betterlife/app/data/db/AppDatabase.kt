package com.betterlife.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        TaskEntity::class,
        EntryStateEntity::class,
        WeeklyHabitEntity::class,
        CustomEntryEntity::class,
        EntryNoteEntity::class,
        StreakLeaveEntity::class,
        ChatMessageEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun taskDao(): TaskDao
    abstract fun entryStateDao(): EntryStateDao
    abstract fun weeklyHabitDao(): WeeklyHabitDao
    abstract fun customEntryDao(): CustomEntryDao
    abstract fun entryNoteDao(): EntryNoteDao
    abstract fun streakLeaveDao(): StreakLeaveDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "betterlife.db")
                // 历史版本说明：v2 把 entry_states 的主键从 entryId 改成 (entryId, state)，
                // v3 给 tasks 加 remindAtMinutes（单任务独立提醒时间），
                // v4 加 weekly_habits（每周习惯模板），
                // v5 加 custom_entries（用户自建任务的标题），
                // v6 给 tasks 加 note/doneBy/dueDate，新增 entry_notes、streak_leaves、
                // chat_messages 三表（见 Migrations.kt）。
                .addMigrations(MIGRATION_5_6)
                .build()
    }
}
