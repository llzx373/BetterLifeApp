package com.betterlife.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** N1 每日一问:withAnswer 把一题的答案写进档案 */
class ProfileAnswerTest {

    @Test
    fun `单选字段取第一个值`() {
        val p = Profile().withAnswer("ageRange", setOf("36-45"))
        assertEquals(AgeRange.A36_45, p.ageRange)
    }

    @Test
    fun `布尔字段认字符串 true`() {
        assertTrue(Profile().withAnswer("sleepShort", setOf("true")).sleepShort)
        assertFalse(Profile().withAnswer("sleepShort", setOf("false")).sleepShort)
    }

    @Test
    fun `多选字段取全集,空选等于清空`() {
        val p = Profile().withAnswer("chronic", setOf("hypertension", "kidney"))
        assertEquals(setOf(Chronic.HYPERTENSION, Chronic.KIDNEY), p.chronic)
        assertTrue(Profile().withAnswer("goals", emptySet()).goals.isEmpty())
    }

    @Test
    fun `未知字段与非法取值都不动档案`() {
        val base = Profile(smoking = Smoking.YES)
        assertEquals(base, base.withAnswer("notAField", setOf("x")))
        // 非法取值回退到该字段当前值(enumByKey 的 default 参数是当前值)
        assertEquals(base, base.withAnswer("smoking", setOf("junk")))
    }

    @Test
    fun `knownFields 原样保留`() {
        val p = Profile(knownFields = setOf("smoking")).withAnswer("smoking", setOf("yes"))
        assertEquals(setOf("smoking"), p.knownFields)
        assertEquals(Smoking.YES, p.smoking)
    }
}
