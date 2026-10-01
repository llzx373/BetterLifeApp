package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RuleDto
import com.betterlife.app.data.RulesFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailySeedPickerTest {

    private fun entry(
        id: String,
        sec: Int,
        ratio: String = "高",
        grade: String = "A",
        todo: Boolean = false,
        removed: Boolean = false,
    ) = EntryDto(
        id = id, sec = sec, n = id.substringAfter('-').toInt(), title = "t$id",
        secKey = "s$sec", ratio = ratio, grade = grade, todo = todo, removed = removed,
    )

    private val entries = listOf(
        entry("01-01", 1, ratio = "极高"),
        entry("01-02", 1, ratio = "高"),
        entry("01-03", 1, removed = true),
        entry("02-01", 2, ratio = "高", grade = "B"),
        entry("02-02", 2, todo = true),
        entry("03-01", 3, ratio = "一般"),
    )

    private fun rules(
        boostSecs: List<String> = listOf("s1", "s2"),
        weight: Int = 10,
        exclude: List<String> = emptyList(),
    ) = RulesFile(
        seedEntryIds = listOf("01-01", "01-02", "01-03", "02-01", "02-02", "03-01"),
        rules = listOf(RuleDto(emptyMap(), emptyList(), boostSecs, exclude, weight, "r")),
    )

    private val picker = DailySeedPicker()
    private val profile = Profile()

    @Test
    fun `结果确定可复现`() {
        val a = picker.pickSeeds(profile, entries, rules(), emptySet())
        val b = picker.pickSeeds(profile, entries, rules(), emptySet())
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun `默认按稳定排序取前 3 条`() {
        // 有效候选 01-01(极高) > 01-02(高A) > 02-01(高B),排序规则同推荐引擎组内序
        val ids = picker.pickSeeds(profile, entries, rules(), emptySet()).map { it.id }
        assertEquals(listOf("01-01", "01-02", "02-01"), ids)
    }

    @Test
    fun `count 决定播种条数`() {
        val ids = picker.pickSeeds(profile, entries, rules(), emptySet(), count = 2).map { it.id }
        assertEquals(listOf("01-01", "01-02"), ids)
    }

    @Test
    fun `todo 与已下架及未被 boost 的条目不入选`() {
        val ids = picker.pickSeeds(profile, entries, rules(), emptySet(), count = 10).map { it.id }
        assertFalse("02-02 是 todo", "02-02" in ids)
        assertFalse("01-03 已下架", "01-03" in ids)
        assertFalse("03-01 所在节未被 boost", "03-01" in ids)
        assertTrue("01-01" in ids && "01-02" in ids && "02-01" in ids)
    }

    @Test
    fun `命中规则的 exclude 生效`() {
        val ids = picker.pickSeeds(profile, entries, rules(exclude = listOf("01-01")), emptySet(), count = 10)
            .map { it.id }
        assertFalse("01-01" in ids)
    }

    @Test
    fun `excludedIds 生效`() {
        val ids = picker.pickSeeds(profile, entries, rules(), setOf("01-02"), count = 10).map { it.id }
        assertFalse("01-02" in ids)
    }

    @Test
    fun `weight 为 0 的规则不算入选依据,命中为空时落回种子池硬排`() {
        // 普惠规则 weight=0 且只 boost 节1 → 没有正权重 boost,触发空命中兜底:
        // 种子池按 ratio/grade/cs 稳定排序取前 3(01-03 已下架、02-02 todo 仍被剔除)
        val result = picker.pickSeeds(profile, entries, rules(boostSecs = listOf("s1"), weight = 0), emptySet())
        assertEquals(listOf("01-01", "01-02", "02-01"), result.map { it.id })
    }

    @Test
    fun `空档案命中普惠规则时正常按命中挑选`() {
        // when = {} 的普惠规则天然命中空档案,不触发兜底
        val ids = picker.pickSeeds(Profile.EMPTY, entries, rules(), emptySet()).map { it.id }
        assertEquals(listOf("01-01", "01-02", "02-01"), ids)
    }

    @Test
    fun `空档案无普惠 boost 时落回种子池硬排取前 3`() {
        // 规则带字段条件,空档案一律不命中 → 种子池 ∩ 档案命中为空 → 兜底硬排
        val fieldRules = RulesFile(
            seedEntryIds = listOf("01-01", "01-02", "01-03", "02-01", "02-02", "03-01"),
            rules = listOf(
                RuleDto(mapOf("smoking" to listOf("yes")), emptyList(), listOf("s1", "s2"), emptyList(), 10, "r"),
            ),
        )
        val ids = picker.pickSeeds(Profile.EMPTY, entries, fieldRules, emptySet()).map { it.id }
        assertEquals(listOf("01-01", "01-02", "02-01"), ids)
    }

    @Test
    fun `兜底仍遵守 excludedIds`() {
        val fieldRules = RulesFile(
            seedEntryIds = listOf("01-01", "01-02", "02-01"),
            rules = listOf(
                RuleDto(mapOf("smoking" to listOf("yes")), emptyList(), listOf("s1", "s2"), emptyList(), 10, "r"),
            ),
        )
        val ids = picker.pickSeeds(Profile.EMPTY, entries, fieldRules, setOf("01-01")).map { it.id }
        assertEquals(listOf("01-02", "02-01"), ids)
    }

    @Test
    fun `候选不足时有多少挑多少`() {
        val result = picker.pickSeeds(
            profile, entries, rules(exclude = listOf("01-01", "01-02")), emptySet(),
        )
        assertEquals(listOf("02-01"), result.map { it.id })
    }
}
