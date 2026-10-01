package com.betterlife.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * N4 周报：周日才触发、0 打卡周不发、同周频控、环比文案、下一次触发时刻计算。
 * 参考日期：2026-09-28 是周一，2026-10-04 是周日，2026-10-03 是周六。
 */
class WeeklyReportTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    // ---------- decideWeeklyReport ----------

    @Test
    fun `周日且有打卡且从未发过则触发`() {
        assertTrue(decideWeeklyReport("2026-10-04", doneThisWeek = 5, enabled = true, lastSentWeek = ""))
    }

    @Test
    fun `非周日不触发`() {
        assertFalse(decideWeeklyReport("2026-10-03", doneThisWeek = 5, enabled = true, lastSentWeek = ""))
        assertFalse(decideWeeklyReport("2026-10-05", doneThisWeek = 5, enabled = true, lastSentWeek = ""))
    }

    @Test
    fun `本周 0 打卡不发`() {
        assertFalse(decideWeeklyReport("2026-10-04", doneThisWeek = 0, enabled = true, lastSentWeek = ""))
    }

    @Test
    fun `开关关闭则不发`() {
        assertFalse(decideWeeklyReport("2026-10-04", doneThisWeek = 5, enabled = false, lastSentWeek = ""))
    }

    @Test
    fun `同周已发过被频控`() {
        // 2026-09-28 是触发日（2026-10-04）所在周的周一
        assertFalse(decideWeeklyReport("2026-10-04", doneThisWeek = 5, enabled = true, lastSentWeek = "2026-09-28"))
    }

    @Test
    fun `下周日换了新的一周可再发`() {
        assertTrue(decideWeeklyReport("2026-10-11", doneThisWeek = 5, enabled = true, lastSentWeek = "2026-09-28"))
    }

    @Test
    fun `非法日期不发`() {
        assertFalse(decideWeeklyReport("not-a-date", doneThisWeek = 5, enabled = true, lastSentWeek = ""))
    }

    // ---------- weeklyReportText ----------

    @Test
    fun `环比上升报多出的次数`() {
        assertEquals(
            "这周你打卡了 10 次，比上周多 4 次，最长连签 12 天",
            weeklyReportText(doneThisWeek = 10, donePrevWeek = 6, currentStreak = 12),
        )
    }

    @Test
    fun `环比持平报持平`() {
        assertEquals(
            "这周你打卡了 5 次，与上周持平",
            weeklyReportText(doneThisWeek = 5, donePrevWeek = 5, currentStreak = 0),
        )
    }

    @Test
    fun `环比下滑不报数字保持正向`() {
        assertEquals(
            "这周你打卡了 3 次，最长连签 2 天",
            weeklyReportText(doneThisWeek = 3, donePrevWeek = 7, currentStreak = 2),
        )
    }

    @Test
    fun `无连签时省略连签段`() {
        assertEquals(
            "这周你打卡了 1 次，比上周多 1 次",
            weeklyReportText(doneThisWeek = 1, donePrevWeek = 0, currentStreak = 0),
        )
    }

    // ---------- nextWeeklyReportMillis ----------

    private fun millis(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun dateTimeOf(epochMillis: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone).toString()

    @Test
    fun `周六的下一个触发是次日周日 20 点 07 分`() {
        assertEquals(
            "2026-10-04T20:07",
            dateTimeOf(nextWeeklyReportMillis(millis("2026-10-03T12:00:00"), zone)),
        )
    }

    @Test
    fun `周日没到点则当天触发`() {
        assertEquals(
            "2026-10-04T20:07",
            dateTimeOf(nextWeeklyReportMillis(millis("2026-10-04T20:06:59"), zone)),
        )
    }

    @Test
    fun `周日整点已到则顺延到下周日`() {
        assertEquals(
            "2026-10-11T20:07",
            dateTimeOf(nextWeeklyReportMillis(millis("2026-10-04T20:07:00"), zone)),
        )
    }

    @Test
    fun `周日过点则顺延到下周日`() {
        assertEquals(
            "2026-10-11T20:07",
            dateTimeOf(nextWeeklyReportMillis(millis("2026-10-04T21:30:00"), zone)),
        )
    }
}
