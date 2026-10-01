package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RuleDto
import com.betterlife.app.data.RulesFile
import com.betterlife.app.data.Smoking
import com.betterlife.app.data.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** N1:空档案(所有 when 字段未知)只有 {} 普惠规则生效 */
class EmptyProfileEngineTest {

    private fun entry(id: String, sec: Int, ratio: String = "高", grade: String = "A") = EntryDto(
        id = id, sec = sec, n = id.substringAfter('-').toInt(), title = "t$id",
        secKey = "s$sec", ratio = ratio, grade = grade,
    )

    private val entries = listOf(
        entry("01-01", 1, ratio = "极高"),
        entry("01-02", 1),
        entry("02-01", 2, grade = "B"),
    )

    private val rules = RulesFile(
        rules = listOf(
            // 普惠规则:给节2 加分
            RuleDto(emptyMap(), emptyList(), listOf("s2"), emptyList(), 10, "普惠"),
            // 带字段规则:吸烟者加 01-01、排除 01-02 —— 空档案一律不命中
            RuleDto(mapOf("smoking" to listOf("yes")), listOf("01-01"), emptyList(), listOf("01-02"), 100, "吸烟"),
        ),
    )

    private val engine = RecommendationEngine()

    @Test
    fun `空档案的 matches 只命中空条件`() {
        assertTrue(Profile.EMPTY.matches(emptyMap()))
        assertFalse(Profile.EMPTY.matches(mapOf("smoking" to listOf("yes"))))
        // 布尔字段在规则文件里是字符串 "true"/"false",空档案同样不命中
        assertFalse(Profile.EMPTY.matches(mapOf("sleepShort" to listOf("false"))))
        assertFalse(Profile.EMPTY.matches(mapOf("chronic" to listOf("none"))))
    }

    @Test
    fun `空档案只出普惠规则命中的加分`() {
        val result = engine.recommend(Profile.EMPTY, entries, rules, emptySet())
        val all = result.values.flatten()
        // 三条都在(普惠规则不排除任何条目)
        assertEquals(setOf("01-01", "01-02", "02-01"), all.map { it.entry.id }.toSet())
        // 只有节2 的 02-01 拿到普惠加分;带字段的规则不生效
        assertEquals(10, all.first { it.entry.id == "02-01" }.score)
        assertEquals(0, all.first { it.entry.id == "01-01" }.score)
    }

    @Test
    fun `空档案下带字段规则的 exclude 不生效`() {
        // 吸烟者档案会剔除 01-02;空档案不命中该规则,01-02 保留
        val emptyResult = engine.recommend(Profile.EMPTY, entries, rules, emptySet())
        assertTrue("01-02" in emptyResult.values.flatten().map { it.entry.id })
        val smokerResult = engine.recommend(Profile(smoking = Smoking.YES), entries, rules, emptySet())
        assertFalse("01-02" in smokerResult.values.flatten().map { it.entry.id })
    }

    @Test
    fun `部分已知档案的未知字段不命中`() {
        val partial = Profile(smoking = Smoking.YES, knownFields = setOf("smoking"))
        assertTrue(partial.matches(mapOf("smoking" to listOf("yes"))))
        assertFalse(partial.matches(mapOf("ageRange" to listOf("26-35"))))
        // 多条件规则里有一个未知字段就整体不命中
        assertFalse(partial.matches(mapOf("smoking" to listOf("yes"), "gender" to listOf("male"))))
    }

    @Test
    fun `空档案不落库`() {
        var threw = false
        try {
            Profile.EMPTY.toEntity()
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("空档案/部分档案落库会丢 knownFields,必须拒绝", threw)
    }
}
