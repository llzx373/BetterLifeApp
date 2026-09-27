package com.betterlife.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6：tasks 加 note / doneBy / dueDate 三列（老行 note、dueDate 为 NULL，
 * doneBy 默认 'manual'）；新增 entry_notes（条目备注）、streak_leaves（连签请假）、
 * chat_messages（AI 对话记录）三张表。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN note TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN doneBy TEXT NOT NULL DEFAULT 'manual'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN dueDate TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS entry_notes" +
                " (entryId TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL, updatedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS streak_leaves" +
                " (entryId TEXT NOT NULL, date TEXT NOT NULL, createdAt INTEGER NOT NULL," +
                " PRIMARY KEY(entryId, date))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS chat_messages" +
                " (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, role TEXT NOT NULL," +
                " content TEXT NOT NULL, providerName TEXT, createdAt INTEGER NOT NULL)"
        )
    }
}
