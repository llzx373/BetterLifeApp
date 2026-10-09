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

/**
 * v6 → v7：新增内容库三表（条目内容从 assets 迁入 Room，见 ContentEntities.kt）。
 * 纯建表，不动存量用户表；SS-NN → 稳定 key 的 entryId 改写由 ContentBootstrap 在首启时做。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS content_sections" +
                " (`key` TEXT NOT NULL, n INTEGER NOT NULL, title TEXT NOT NULL," +
                " intro TEXT NOT NULL, entryCount INTEGER NOT NULL, PRIMARY KEY(`key`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS content_entries" +
                " (`key` TEXT NOT NULL, secKey TEXT NOT NULL, sec INTEGER NOT NULL," +
                " n INTEGER NOT NULL, title TEXT NOT NULL, cost TEXT NOT NULL DEFAULT ''," +
                " human TEXT NOT NULL DEFAULT '', gain TEXT NOT NULL DEFAULT ''," +
                " grade TEXT NOT NULL DEFAULT '', src TEXT NOT NULL DEFAULT ''," +
                " note TEXT NOT NULL DEFAULT '', money TEXT NOT NULL DEFAULT ''," +
                " time TEXT NOT NULL DEFAULT '', will TEXT NOT NULL DEFAULT ''," +
                " level TEXT NOT NULL DEFAULT '', lens TEXT NOT NULL DEFAULT ''," +
                " dispute INTEGER NOT NULL DEFAULT 0, todo INTEGER NOT NULL DEFAULT 0," +
                " cs INTEGER NOT NULL DEFAULT 0, ratio TEXT NOT NULL DEFAULT ''," +
                " hay TEXT NOT NULL DEFAULT '', hash TEXT NOT NULL DEFAULT ''," +
                " removed INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL," +
                " PRIMARY KEY(`key`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_content_entries_secKey ON content_entries (secKey)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_content_entries_removed ON content_entries (removed)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS content_meta" +
                " (id INTEGER NOT NULL, contentVersion TEXT NOT NULL," +
                " upstreamCommit TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))"
        )
    }
}

/**
 * v7 → v8：新增 content_articles（上游长文，见 ContentEntities.kt）。
 * 纯建表，不动存量表；内容本身由 ContentBootstrap 在首启时从 assets/articles.json 补播
 * （老库 meta 已存在、不会重播条目，所以长文单独按「表为空」补播）。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS content_articles" +
                " (`key` TEXT NOT NULL, file TEXT NOT NULL, title TEXT NOT NULL," +
                " secs TEXT NOT NULL DEFAULT '', hash TEXT NOT NULL DEFAULT ''," +
                " body TEXT NOT NULL DEFAULT '', PRIMARY KEY(`key`))"
        )
    }
}
