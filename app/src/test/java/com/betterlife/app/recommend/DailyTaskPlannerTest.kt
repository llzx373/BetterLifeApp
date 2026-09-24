package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RuleDto
import com.betterlife.app.data.RulesFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyTaskPlannerTest {

    private fun entry(id: String, sec: Int, ratio: String = "高", grade: String = "A", todo: Boolean = false) =
        EntryDto(
            id = id, sec = sec, n = id.substringAfter('-').toInt(), title = "t$id",
            ratio = ratio, grade = grade, todo = todo,
        )

    private val entries = listOf(
        entry("01-01", 1, ratio = "极高"),
        entry("01-02", 1, ratio = "高"),
        entry("02-01", 2, ratio = "高", grade = "B"),
        entry("02-02", 2, todo = true),
        entry("03-01", 3, ratio = "一般"),
    )

    private fun rules(
        boostSecs: List<Int> = listOf(1, 2),
        weight: Int = 10,
        exclude: List<String> = emptyList(),
        extraRules: List<RuleDto> = emptyList(),
    ) = RulesFile(
        dailyEntryIds = listOf("01-01", "01-02", "02-01", "02-02", "03-01"),
        rules = listOf(RuleDto(emptyMap(), emptyList(), boostSecs, exclude, weight, "r")) + extraRules,
    )

    private val profile = Profile()
    private val date = LocalDate.of(2025, 1, 15)

    @Test
    fun `同一日期结果确定可复现`() {
        val planner = DailyTaskPlanner(maxDaily = 3)
        val a = planner.plan(date, profile, entries, rules(), emptySet())
        val b = planner.plan(date, profile, entries, rules(), emptySet())
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun `最多返回 maxDaily 条`() {
        val planner = DailyTaskPlanner(maxDaily = 2)
        val result = planner.plan(date, profile, entries, rules(), emptySet())
        assertEquals(2, result.size)
    }

    @Test
    fun `todo 条目与未被 boost 的条目不入选`() {
        val planner = DailyTaskPlanner(maxDaily = 10)
        val ids = planner.plan(date, profile, entries, rules(), emptySet()).map { it.id }
        assertFalse("02-02 是 todo", "02-02" in ids)
        assertFalse("03-01 所在节未被 boost", "03-01" in ids)
        assertTrue("01-01" in ids && "01-02" in ids && "02-01" in ids)
    }

    @Test
    fun `命中规则的 exclude 生效`() {
        val planner = DailyTaskPlanner(maxDaily = 10)
        val ids = planner.plan(date, profile, entries, rules(exclude = listOf("01-01")), emptySet()).map { it.id }
        assertFalse("01-01" in ids)
    }

    @Test
    fun `excludedIds 生效`() {
        val planner = DailyTaskPlanner(maxDaily = 10)
        val ids = planner.plan(date, profile, entries, rules(), setOf("01-02")).map { it.id }
        assertFalse("01-02" in ids)
    }

    @Test
    fun `weight 为 0 的规则不算入选依据`() {
        val planner = DailyTaskPlanner(maxDaily = 10)
        // 普惠规则 weight=0 且只 boost 节1 → 所有候选都没有正权重 boost
        val result = planner.plan(date, profile, entries, rules(boostSecs = listOf(1), weight = 0), emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `候选不足时不报错`() {
        val planner = DailyTaskPlanner(maxDaily = 3)
        val result = planner.plan(date, profile, entries, rules(exclude = listOf("01-01", "01-02", "02-01")), emptySet())
        assertTrue(result.isEmpty())
    }
}
