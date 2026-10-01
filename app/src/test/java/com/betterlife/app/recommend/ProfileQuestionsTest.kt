package com.betterlife.app.recommend

import com.betterlife.app.data.Profile
import com.betterlife.app.data.Smoking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** N1 渐进式档案收集:「每天一问」的挑题与有效档案重建 */
class ProfileQuestionsTest {

    @Test
    fun `从未答过时按 ORDER 挑第一题(年龄段)`() {
        assertEquals(
            "ageRange",
            ProfileQuestions.nextField(answered = emptySet(), deferred = emptySet(), askedToday = false),
        )
    }

    @Test
    fun `已答字段跳过,挑下一个未答字段`() {
        val answered = setOf("ageRange", "smoking")
        assertEquals(
            "alcohol",
            ProfileQuestions.nextField(answered, deferred = emptySet(), askedToday = false),
        )
    }

    @Test
    fun `今天已问过就不再问`() {
        assertNull(
            ProfileQuestions.nextField(answered = emptySet(), deferred = emptySet(), askedToday = true),
        )
    }

    @Test
    fun `暂不回答的字段排到队尾,次日换下一题`() {
        // ageRange 被暂缓 → 次日问 smoking,而不是再问 ageRange
        assertEquals(
            "smoking",
            ProfileQuestions.nextField(answered = emptySet(), deferred = setOf("ageRange"), askedToday = false),
        )
    }

    @Test
    fun `全部未答字段都被暂缓时轮回提问`() {
        // 只剩 ageRange 没答且被暂缓 → 还是问它(没有更可问的了)
        val answered = ProfileQuestions.ORDER.filter { it != "ageRange" }.toSet()
        assertEquals(
            "ageRange",
            ProfileQuestions.nextField(answered, deferred = setOf("ageRange"), askedToday = false),
        )
    }

    @Test
    fun `全部字段已答则不再问`() {
        assertNull(
            ProfileQuestions.nextField(
                answered = ProfileQuestions.ORDER.toSet(),
                deferred = emptySet(),
                askedToday = false,
            ),
        )
    }

    @Test
    fun `ORDER 覆盖 Profile 的全部字段`() {
        // 提问序列必须覆盖 fieldValues 的全部字段,否则那些字段永远问不到、卡片永不消失
        val fields = listOf(
            "ageRange", "gender", "smoking", "secondhandSmoke", "alcohol", "betelNut",
            "sugaryDrinks", "exercise", "sleepShort", "chronic", "occupation",
            "financialStress", "housing", "children", "hasElderly", "pregnant",
            "planningAbroad", "goals",
        )
        assertEquals(fields.toSet(), ProfileQuestions.ORDER.toSet())
        // 字段名拼错会让该题永远问不出(完整档案也取不到值);多选字段允许空选,只抽查单选/布尔
        listOf("ageRange", "smoking", "sleepShort").forEach { field ->
            assertTrue("$field 应有取值", Profile().fieldValues(field).isNotEmpty())
        }
    }

    @Test
    fun `isComplete 以已答集合覆盖全部字段为准`() {
        assertTrue(ProfileQuestions.isComplete(ProfileQuestions.ORDER.toSet()))
        assertTrue(!ProfileQuestions.isComplete(ProfileQuestions.ORDER.drop(1).toSet()))
    }

    @Test
    fun `effectiveProfile 无档案返回空档案`() {
        assertTrue(ProfileQuestions.effectiveProfile(null, emptySet()).isEmpty)
    }

    @Test
    fun `effectiveProfile 部分已答时只认已答字段`() {
        val saved = Profile(smoking = Smoking.YES) // 落库的档案,smoking 是答出来的
        val effective = ProfileQuestions.effectiveProfile(saved, setOf("smoking"))
        assertTrue(!effective.isEmpty)
        assertTrue(effective.matches(mapOf("smoking" to listOf("yes"))))
        // 未答字段(ageRange)即使实体里有默认值,也不参与规则匹配
        assertTrue(!effective.matches(mapOf("ageRange" to listOf("26-35"))))
    }

    @Test
    fun `effectiveProfile 已答覆盖全部字段时返回完整档案`() {
        val saved = Profile(smoking = Smoking.YES)
        val effective = ProfileQuestions.effectiveProfile(saved, ProfileQuestions.ORDER.toSet())
        assertEquals(saved, effective)
        assertTrue(effective.matches(mapOf("ageRange" to listOf("26-35"))))
    }
}
