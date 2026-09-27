package com.betterlife.app.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 周区间：周一为起点、周日为终点；每周习惯的进度按打卡日是否落在区间内统计 */
class WeekRangeTest {

    @Test
    fun `周一当天区间从当天开始`() {
        // 2026-03-02 是周一
        val (start, end) = weekRange(LocalDate.parse("2026-03-02"))
        assertEquals(LocalDate.parse("2026-03-02"), start)
        assertEquals(LocalDate.parse("2026-03-08"), end)
    }

    @Test
    fun `周中日期回溯到本周一`() {
        // 2026-03-04 是周三
        val (start, end) = weekRange(LocalDate.parse("2026-03-04"))
        assertEquals(LocalDate.parse("2026-03-02"), start)
        assertEquals(LocalDate.parse("2026-03-08"), end)
    }

    @Test
    fun `周日仍属于本周而不是下周`() {
        // 2026-03-08 是周日
        val (start, end) = weekRange(LocalDate.parse("2026-03-08"))
        assertEquals(LocalDate.parse("2026-03-02"), start)
        assertEquals(LocalDate.parse("2026-03-08"), end)
    }

    @Test
    fun `跨周区间不相交即进度天然归零`() {
        val thisWeek = weekRange(LocalDate.parse("2026-03-08"))
        val nextWeek = weekRange(LocalDate.parse("2026-03-09"))
        assertEquals(thisWeek.second.plusDays(1), nextWeek.first)
    }

    @Test
    fun `跨月跨年的周也正确`() {
        // 2026-01-01 是周四，本周一在 2025-12-29
        val (start, end) = weekRange(LocalDate.parse("2026-01-01"))
        assertEquals(LocalDate.parse("2025-12-29"), start)
        assertEquals(LocalDate.parse("2026-01-04"), end)
    }
}
