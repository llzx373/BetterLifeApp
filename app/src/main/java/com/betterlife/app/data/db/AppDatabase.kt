package com.betterlife.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, TaskEntity::class, EntryStateEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun taskDao(): TaskDao
    abstract fun entryStateDao(): EntryStateDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "betterlife.db")
                // 应用尚未发布：v2 把 entry_states 的主键从 entryId 改成 (entryId, state)，
                // v3 给 tasks 加 remindAtMinutes（单任务独立提醒时间）。
                // 直接重建库即可，不写 migration（数据丢了也无所谓）。
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
