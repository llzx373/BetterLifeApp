package com.betterlife.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY: LocalDate = LocalDate.parse("2026-03-11") // 周三

private fun daily(entryId: String, date: String, done: Boolean, note: String? = null) =
    StatsTaskRow(entryId, StatsRowType.DAILY, date, done, note)

private fun weekly(entryId: String, date: String, note: String? = null) =
    StatsTaskRow(entryId, StatsRowType.WEEKLY, date, done = true, note = note)

private fun entry(id: String, lens: String = "时间") = StatsEntry(id, "条目$id", lens)

class StatsCalculatorTest {

    @Test
    fun `空数据产出空结果`() {
        val result = computeStats(emptyList(), emptyMap(), emptyMap(), TODAY)
        assertTrue(result.dailyStreaks.isEmpty())
        assertEquals(8, result.weeklyTrend.size)
        assertTrue(result.weeklyTrend.all { it.planned == 0 && it.rate == null })
        assertEquals(0, result.perfectWeeks)
        assertEquals(0, result.totalCompletions)

        val report = periodReport(emptyList(), emptyMap(), TODAY, TODAY)
        assertEquals(0, report.totalDone)
        assertNull(report.completionRate)
        assertTrue(report.notes.isEmpty())
    }

    @Test
    fun `趋势按 ISO 周聚合且本周是第一周窗口的最后一周`() {
        // 本周一 2026-03-09：3 个每日任务完成 2 个
        val rows = listOf(
            daily("a", "2026-03-09", true),
            daily("b", "2026-03-09", true),
            daily("a", "2026-03-10", false),
            // 上周（03-02 ~ 03-08）：1/2
            daily("a", "2026-03-04", true),
            daily("b", "2026-03-05", false),
        )
        val result = computeStats(rows, emptyMap(), emptyMap(), TODAY)
        val trend = result.weeklyTrend
        assertEquals(LocalDate.parse("2026-03-09"), trend.last().weekStart)
        assertEquals(2, trend.last().done)
        assertEquals(3, trend.last().planned)
        val lastWeek = trend[trend.size - 2]
        assertEquals(LocalDate.parse("2026-03-02"), lastWeek.weekStart)
        assertEquals(1, lastWeek.done)
        assertEquals(2, lastWeek.planned)
        assertEquals(0.5f, lastWeek.rate!!)
    }

    @Test
    fun `没有记录的周 planned 为 0 且 rate 为 null`() {
        val result = computeStats(emptyList(), emptyMap(), emptyMap(), TODAY)
        assertNull(result.weeklyTrend.first().rate)
    }

    @Test
    fun `完美周只统计已全部完成的已结束周`() {
        val rows = listOf(
            // 上上周（02-23 ~ 03-01）全完成 → 完美
            daily("a", "2026-02-25", true),
            daily("b", "2026-02-26", true),
            // 上周有一个没完成 → 不算
            daily("a", "2026-03-03", true),
            daily("b", "2026-03-03", false),
            // 本周目前全完成，但周还没结束 → 不算
            daily("a", "2026-03-10", true),
        )
        val result = computeStats(rows, emptyMap(), emptyMap(), TODAY)
        assertEquals(1, result.perfectWeeks)
    }

    @Test
    fun `连签含当前值与历史最长值 请假搭桥不断签`() {
        val rows = listOf(
            // 历史最长 3 天（1 月）
            daily("a", "2026-01-01", true),
            daily("a", "2026-01-02", true),
            daily("a", "2026-01-03", true),
            // 当前：03-08 打卡、03-09 请假、03-10 打卡 → 当前连签 2（请假搭桥）
            daily("a", "2026-03-08", true),
            daily("a", "2026-03-10", true),
        )
        val leaves = mapOf("a" to setOf("2026-03-09"))
        val result = computeStats(rows, mapOf("a" to entry("a")), leaves, TODAY)
        val streak = result.dailyStreaks.single()
        assertEquals(2, streak.current)
        assertEquals(3, streak.max)
        assertEquals(3, result.bestStreak)
    }

    @Test
    fun `maxStreak 处理空集与断裂`() {
        assertEquals(0, maxStreak(emptySet(), emptySet()))
        assertEquals(1, maxStreak(setOf("2026-01-01"), emptySet()))
        // 01-01, 01-02 与 01-05 之间断了两天（无请假）→ 最长 2
        assertEquals(
            2,
            maxStreak(setOf("2026-01-01", "2026-01-02", "2026-01-05"), emptySet()),
        )
        // 断开的两天是请假 → 桥接成 3
        assertEquals(
            3,
            maxStreak(
                setOf("2026-01-01", "2026-01-02", "2026-01-05"),
                setOf("2026-01-03", "2026-01-04"),
            ),
        )
    }

    @Test
    fun `周期报告按区间过滤并统计口径分布`() {
        val entries = mapOf(
            "life1" to entry("life1", "死亡率"),
            "money1" to entry("money1", "金钱"),
            "custom:1" to StatsEntry("custom:1", "自定义", ""),
        )
        val start = LocalDate.parse("2026-03-09")
        val end = LocalDate.parse("2026-03-15")
        val rows = listOf(
            daily("life1", "2026-03-09", true),
            daily("life1", "2026-03-10", true),
            daily("money1", "2026-03-10", false),
            weekly("money1", "2026-03-11"),
            weekly("custom:1", "2026-03-11"),
            // 区间外，不应计入
            daily("life1", "2026-03-01", true),
            // ONCE 没有日期，不进周期报告
            StatsTaskRow("life1", StatsRowType.ONCE, null, done = true),
        )
        val report = periodReport(rows, entries, start, end)
        assertEquals(4, report.totalDone) // 2 DAILY + 2 WEEKLY
        assertEquals(2, report.doneDaily)
        assertEquals(3, report.plannedDaily)
        assertEquals(2, report.doneByLens["死亡率"])
        assertEquals(1, report.doneByLens["金钱"])
        assertEquals(1, report.doneByLens[""]) // 自定义条目归到空口径
        assertEquals(2f / 3f, report.completionRate!!, 1e-6f)
    }

    @Test
    fun `周期报告提取随手记 按日期倒序`() {
        val entries = mapOf("a" to entry("a"), "b" to entry("b"))
        val rows = listOf(
            daily("a", "2026-03-09", true, note = "第一天感觉不错"),
            daily("b", "2026-03-10", true, note = "  带空白的备注  "),
            daily("a", "2026-03-10", true), // 无备注
            daily("b", "2026-03-10", false, note = "没完成的备注不算"),
            weekly("a", "2026-03-11", note = "每周习惯也能记"),
        )
        val report = periodReport(
            rows, entries,
            LocalDate.parse("2026-03-09"), LocalDate.parse("2026-03-15"),
        )
        assertEquals(3, report.notes.size)
        assertEquals("2026-03-11", report.notes[0].date)
        assertEquals("带空白的备注", report.notes[1].note)
        assertEquals(entries["b"]!!.title, report.notes[1].entryTitle)
        assertEquals("第一天感觉不错", report.notes[2].note)
    }

    @Test
    fun `累计完成包含全部 done 行`() {
        val rows = listOf(
            daily("a", "2026-03-09", true),
            daily("a", "2026-03-10", false),
            weekly("a", "2026-03-11"),
            StatsTaskRow("a", StatsRowType.ONCE, null, done = true),
        )
        val result = computeStats(rows, emptyMap(), emptyMap(), TODAY)
        assertEquals(3, result.totalCompletions)
    }

    @Test
    fun `统计区间为空时月报告正确`() {
        val period = StatsPeriod.THIS_MONTH
        val (start, end) = period.range(TODAY)
        assertEquals(LocalDate.parse("2026-03-01"), start)
        assertEquals(LocalDate.parse("2026-03-31"), end)

        val (lastStart, lastEnd) = StatsPeriod.LAST_MONTH.range(TODAY)
        assertEquals(LocalDate.parse("2026-02-01"), lastStart)
        assertEquals(LocalDate.parse("2026-02-28"), lastEnd)

        val (lastWeekStart, lastWeekEnd) = StatsPeriod.LAST_WEEK.range(TODAY)
        assertEquals(LocalDate.parse("2026-03-02"), lastWeekStart)
        assertEquals(LocalDate.parse("2026-03-08"), lastWeekEnd)
    }
}
