package com.betterlife.app.tasks

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyGraduationTest {

    // 周三，避免周一/周日边界歧义
    private val today = LocalDate.of(2026, 9, 30)

    private fun weekDates(weeksAgo: Int, count: Int): List<String> {
        val monday = weekRange(today).first.minusWeeks(weeksAgo.toLong())
        return (0 until count).map { monday.plusDays(it.toLong()).toString() }
    }

    @Test
    fun `timesPerWeek 非法时返回 0`() {
        assertEquals(0, consecutiveReachedWeeks(weekDates(1, 3), 0, today))
    }

    @Test
    fun `无任何打卡返回 0`() {
        assertEquals(0, consecutiveReachedWeeks(emptyList(), 2, today))
    }

    @Test
    fun `本周未达标不算断签_从上周起算`() {
        val dates = weekDates(1, 2) + weekDates(2, 2)
        assertEquals(2, consecutiveReachedWeeks(dates, 2, today))
    }

    @Test
    fun `本周已达标计入连续数`() {
        val dates = weekDates(0, 2) + weekDates(1, 2)
        assertEquals(2, consecutiveReachedWeeks(dates, 2, today))
    }

    @Test
    fun `中间断一周则链条截断`() {
        val dates = weekDates(1, 2) + weekDates(3, 2) // 上周达标,前第二周断,前第三周达标
        assertEquals(1, consecutiveReachedWeeks(dates, 2, today))
    }

    @Test
    fun `某周打卡数不足 timesPerWeek 即断签`() {
        val dates = weekDates(1, 1) + weekDates(2, 2)
        assertEquals(0, consecutiveReachedWeeks(dates, 2, today))
    }

    @Test
    fun `超出 timesPerWeek 的打卡仍算达标`() {
        val dates = weekDates(1, 5) + weekDates(2, 3)
        assertEquals(2, consecutiveReachedWeeks(dates, 2, today))
    }

    @Test
    fun `非法日期串被忽略`() {
        val dates = weekDates(1, 2) + listOf("不是日期", "2026-13-40")
        assertEquals(1, consecutiveReachedWeeks(dates, 2, today))
    }
}
