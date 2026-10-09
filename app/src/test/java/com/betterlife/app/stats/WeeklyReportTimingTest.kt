package com.betterlife.app.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 周报锚点：最近一个周日 20:07，已过点顺延下周日（自续链每轮重锚的依据） */
class WeeklyReportTimingTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定 now 的构造入口，不依赖系统时钟 */
    private fun millis(date: String, time: String): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `周日 20-07 前则当天触发`() {
        // 2026-03-08 是周日
        val now = millis("2026-03-08", "09:00")
        assertEquals(millis("2026-03-08", "20:07"), nextWeeklyReportMillis(now, zone))
    }

    @Test
    fun `周日已过 20-07 则顺延到下周日`() {
        val now = millis("2026-03-08", "20:08")
        assertEquals(millis("2026-03-15", "20:07"), nextWeeklyReportMillis(now, zone))
    }

    @Test
    fun `恰好 20-07-00 也算已过点顺延下周`() {
        // 锚点必须严格晚于 now：worker 在 20:07 触发后重锚，不能又排回同一时刻
        val now = millis("2026-03-08", "20:07")
        assertEquals(millis("2026-03-15", "20:07"), nextWeeklyReportMillis(now, zone))
    }

    @Test
    fun `非周日则锚到最近的周日`() {
        // 2026-03-09 是周一：设备错过周日窗口后重锚，回到本周日（3/15）而非滑动漂移
        val now = millis("2026-03-09", "10:00")
        assertEquals(millis("2026-03-15", "20:07"), nextWeeklyReportMillis(now, zone))
    }

    @Test
    fun `DST 拨回周的周日仍锚定墙钟 20-07`() {
        // 美东 2026-11-01（周日）凌晨拨回 1 小时；周六中午排的锚点必须落在周日墙钟 20:07
        val usZone = ZoneId.of("America/New_York")
        fun usMillis(date: String, time: String): Long =
            LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(usZone).toInstant().toEpochMilli()
        val now = usMillis("2026-10-31", "12:00")
        assertEquals(usMillis("2026-11-01", "20:07"), nextWeeklyReportMillis(now, usZone))
    }
}
