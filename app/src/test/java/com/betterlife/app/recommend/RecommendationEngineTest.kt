package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RuleDto
import com.betterlife.app.data.RulesFile
import com.betterlife.app.data.Smoking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    private fun entry(
        id: String,
        sec: Int,
        lens: String = "死亡率",
        ratio: String = "高",
        grade: String = "A",
        cs: Int = 0,
        todo: Boolean = false,
    ) = EntryDto(
        id = id, sec = sec, n = id.substringAfter('-').toInt(), title = "t$id",
        secKey = "s$sec", lens = lens, ratio = ratio, grade = grade, cs = cs, todo = todo,
    )

    private fun rule(
        cond: Map<String, List<String>> = emptyMap(),
        boost: List<String> = emptyList(),
        boostSecs: List<String> = emptyList(),
        exclude: List<String> = emptyList(),
        weight: Int = 10,
    ) = RuleDto(cond, boost, boostSecs, exclude, weight, "reason")

    private val engine = RecommendationEngine()

    // e1: 吸烟者加分目标；e2: 被吸烟者规则排除；e3: todo；e4: 节2 内；e5: 时间口径
    private val entries = listOf(
        entry("01-01", 1, lens = "死亡率", ratio = "极高"),
        entry("01-02", 1, lens = "死亡率", ratio = "高"),
        entry("02-01", 2, lens = "金钱", ratio = "极高", grade = "B", todo = true),
        entry("02-02", 2, lens = "金钱", ratio = "高", grade = "C", cs = 3),
        entry("03-01", 3, lens = "时间", ratio = "一般", grade = "B", cs = 1),
    )

    private val rules = RulesFile(
        rules = listOf(
            rule(cond = mapOf("smoking" to listOf("yes")), boost = listOf("01-01"), weight = 100),
            rule(boostSecs = listOf("s2"), weight = 10),
            rule(cond = mapOf("smoking" to listOf("yes")), exclude = listOf("01-02"), weight = 5),
        )
    )

    private val smoker = Profile(smoking = Smoking.YES)
    private val nonSmoker = Profile(smoking = Smoking.NO)

    @Test
    fun `命中规则给条目加分`() {
        val result = engine.recommend(smoker, entries, rules, emptySet())
        val e1 = result["死亡率"]!!.first { it.entry.id == "01-01" }
        assertEquals(100, e1.score)
        val e4 = result["金钱"]!!.first { it.entry.id == "02-02" }
        assertEquals(10, e4.score) // 普惠节加分
    }

    @Test
    fun `命中规则的 exclude 直接剔除条目`() {
        val result = engine.recommend(smoker, entries, rules, emptySet())
        val allIds = result.values.flatten().map { it.entry.id }
        assertFalse("01-02 应被剔除", "01-02" in allIds)
    }

    @Test
    fun `todo 条目不进入推荐`() {
        val result = engine.recommend(smoker, entries, rules, emptySet())
        val allIds = result.values.flatten().map { it.entry.id }
        assertFalse("02-01 是 todo 条目", "02-01" in allIds)
    }

    @Test
    fun `excludedIds 剔除本地状态条目`() {
        val result = engine.recommend(nonSmoker, entries, rules, setOf("01-01"))
        val allIds = result.values.flatten().map { it.entry.id }
        assertFalse("01-01" in allIds)
    }

    @Test
    fun `按 lens 分组且组间顺序固定`() {
        val result = engine.recommend(smoker, entries, rules, emptySet())
        assertEquals(listOf("死亡率", "金钱", "时间"), result.keys.toList())
    }

    @Test
    fun `组内按 score ratio grade cs 排序`() {
        val orderingEntries = listOf(
            entry("01-01", 1, ratio = "高", grade = "B", cs = 0),
            entry("01-02", 1, ratio = "极高", grade = "C", cs = 9),
            entry("01-03", 1, ratio = "高", grade = "A", cs = 5),
        )
        val result = engine.recommend(nonSmoker, orderingEntries, RulesFile(), emptySet())
        val ids = result["死亡率"]!!.map { it.entry.id }
        assertEquals(listOf("01-02", "01-03", "01-01"), ids)
    }

    @Test
    fun `每组截取 topN`() {
        val many = (1..8).map { entry("01-%02d".format(it), 1, ratio = "高") }
        val result = engine.recommend(nonSmoker, many, RulesFile(), emptySet(), topN = 3)
        assertEquals(3, result["死亡率"]!!.size)
    }

    @Test
    fun `非吸烟者规则不命中时 score 为 0 且条目保留`() {
        val result = engine.recommend(nonSmoker, entries, rules, emptySet())
        val deathIds = result["死亡率"]!!.map { it.entry.id }
        assertTrue("01-01" in deathIds && "01-02" in deathIds)
        assertEquals(0, result["死亡率"]!!.first { it.entry.id == "01-01" }.score)
    }
}
