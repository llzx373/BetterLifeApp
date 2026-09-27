package com.betterlife.app.tasks

import com.betterlife.app.data.db.DailyReminderRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 次日提醒继承：取最近一次 DAILY 行的设置，「清除也是设置」，不回溯更早的旧时间 */
class ReminderInheritanceTest {

    private fun row(taskId: Long, date: String, minutes: Int?) =
        DailyReminderRow(taskId, date, minutes)

    @Test
    fun `无历史行时不继承`() {
        assertNull(inheritedReminderMinutes(emptyList()))
    }

    @Test
    fun `继承最近一次行的提醒时间`() {
        val history = listOf(
            row(1, "2026-03-01", 8 * 60),
            row(2, "2026-03-02", 9 * 60 + 30),
        )
        assertEquals(9 * 60 + 30, inheritedReminderMinutes(history))
    }

    @Test
    fun `最近一次是清除则不继承更早的旧时间`() {
        // 3-02 行是「清除」（null）：不能因为 3-01 设过 8:00 就复活
        val history = listOf(
            row(1, "2026-03-01", 8 * 60),
            row(2, "2026-03-02", null),
        )
        assertNull(inheritedReminderMinutes(history))
    }

    @Test
    fun `最近一行从未设过提醒则不继承`() {
        val history = listOf(
            row(1, "2026-03-01", null),
            row(2, "2026-03-02", null),
        )
        assertNull(inheritedReminderMinutes(history))
    }

    @Test
    fun `同一天有多行时取 taskId 大者`() {
        val history = listOf(
            row(1, "2026-03-02", 8 * 60),
            row(2, "2026-03-02", null),
        )
        assertNull(inheritedReminderMinutes(history))
    }
}
