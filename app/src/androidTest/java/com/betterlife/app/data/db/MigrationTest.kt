package com.betterlife.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Room schema v5 → v6 迁移校验：旧数据保留、新列给默认值、新表结构被 Room 认可。 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate5To6() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO profile (id, ageRange, gender, smoking, secondhandSmoke, alcohol," +
                    " betelNut, sugaryDrinks, exercise, sleepShort, chronic, occupation," +
                    " financialStress, housing, children, hasElderly, pregnant, planningAbroad," +
                    " goals) VALUES (1, '26-35', 'other', 'no', 0, 'no', 0, 'no', 'none', 0," +
                    " '', 'other', 0, 'rent', 'none', 0, 0, 0, '')"
            )
            execSQL(
                "INSERT INTO tasks (taskId, entryId, type, date, done, doneAt, notified," +
                    " remindAtMinutes, createdAt) VALUES (1, 'entry-1', 'DAILY', '2025-01-01'," +
                    " 1, 1700000000000, 0, NULL, 1700000000000)"
            )
            execSQL(
                "INSERT INTO entry_states (entryId, state, updatedAt)" +
                    " VALUES ('entry-1', 'DONE', 1700000000000)"
            )
            close()
        }

        // validateDroppedTables = true：v6 没有删表，主要让它比对 Room 期望的表结构
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        // 旧行仍在，新列给默认值：note/dueDate 为 NULL，doneBy = 'manual'
        db.query("SELECT entryId, done, note, doneBy, dueDate FROM tasks WHERE taskId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("entry-1", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertTrue(c.isNull(2))
            assertEquals("manual", c.getString(3))
            assertTrue(c.isNull(4))
        }
        db.query("SELECT ageRange FROM profile WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("26-35", c.getString(0))
        }
        db.query("SELECT state FROM entry_states WHERE entryId = 'entry-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("DONE", c.getString(0))
        }

        // 三张新表都已创建
        for (table in listOf("entry_notes", "streak_leaves", "chat_messages")) {
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table),
            ).use { c ->
                assertTrue("missing table: $table", c.moveToFirst())
            }
        }
    }

    @Test
    fun migrate6To7() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO tasks (entryId, type) VALUES ('02-01', 'DAILY')"
            )
            close()
        }

        // 纯建表迁移：老行原样保留，重点校验三张新表结构与 Room 期望一致
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        db.query("SELECT entryId FROM tasks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("02-01", c.getString(0))
        }
        for (table in listOf("content_sections", "content_entries", "content_meta")) {
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table),
            ).use { c ->
                assertTrue("missing table: $table", c.moveToFirst())
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
