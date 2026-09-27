package com.betterlife.app.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自动核销规则判定：三种类型各测达标/不达标，null 数据永不匹配，非候选条目被过滤 */
class HealthAutoCompleteTest {

    private val stepsRule = HealthRule("02-11", HealthRule.TYPE_STEPS, 7000.0)
    private val exerciseRule = HealthRule("02-14", HealthRule.TYPE_EXERCISE, 30.0)
    private val sleepRule = HealthRule("02-13", HealthRule.TYPE_SLEEP, 7.0)
    private val allRules = listOf(stepsRule, exerciseRule, sleepRule)
    private val allCandidates = setOf("02-11", "02-13", "02-14")

    private fun evaluate(
        rules: List<HealthRule> = allRules,
        steps: Long? = null,
        exercise: Long? = null,
        sleep: Double? = null,
        candidates: Set<String> = allCandidates,
    ) = evaluateAutoCompletion(rules, steps, exercise, sleep, candidates)

    @Test
    fun `步数达到阈值则命中`() {
        assertEquals(setOf("02-11"), evaluate(steps = 9234))
        assertEquals(setOf("02-11"), evaluate(steps = 7000)) // 恰好等于阈值也算达标
    }

    @Test
    fun `步数低于阈值不命中`() {
        assertTrue(evaluate(steps = 6999).isEmpty())
    }

    @Test
    fun `运动分钟数达到阈值则命中`() {
        assertEquals(setOf("02-14"), evaluate(exercise = 45))
        assertEquals(setOf("02-14"), evaluate(exercise = 30))
    }

    @Test
    fun `运动分钟数低于阈值不命中`() {
        assertTrue(evaluate(exercise = 29).isEmpty())
    }

    @Test
    fun `睡眠时长达到阈值则命中`() {
        assertEquals(setOf("02-13"), evaluate(sleep = 7.5))
        assertEquals(setOf("02-13"), evaluate(sleep = 7.0))
    }

    @Test
    fun `睡眠时长低于阈值不命中`() {
        assertTrue(evaluate(sleep = 6.9).isEmpty())
    }

    @Test
    fun `数据为null时对应类型永不匹配`() {
        // 三类数据全 null：一条都不该中
        assertTrue(evaluate().isEmpty())
        // 只有步数数据：运动、睡眠规则不中
        assertEquals(setOf("02-11"), evaluate(steps = 99999))
    }

    @Test
    fun `非候选条目的规则被忽略`() {
        // 02-11 不在候选里（比如今天没排这条任务，或已经完成）
        assertTrue(evaluate(steps = 99999, candidates = setOf("02-13", "02-14")).isEmpty())
    }

    @Test
    fun `未知类型保守不匹配`() {
        val weird = HealthRule("99-01", "heart_rate", 60.0)
        assertTrue(evaluate(rules = listOf(weird), candidates = setOf("99-01")).isEmpty())
    }

    @Test
    fun `多种数据齐备时各类型独立判定`() {
        assertEquals(
            setOf("02-11", "02-13"),
            evaluate(steps = 8000, exercise = 10, sleep = 8.0),
        )
    }

    @Test
    fun `证据文案带实际数值与阈值`() {
        assertEquals(
            "今日步数 9234 ≥ 7000",
            healthEvidenceText(stepsRule, stepsToday = 9234, null, null),
        )
        assertEquals(
            "今日运动 45 分钟 ≥ 30 分钟",
            healthEvidenceText(exerciseRule, null, exerciseMinutesToday = 45, null),
        )
        assertEquals(
            "昨晚睡眠 7.5 小时 ≥ 7 小时",
            healthEvidenceText(sleepRule, null, null, sleepHoursLastNight = 7.5),
        )
    }
}
