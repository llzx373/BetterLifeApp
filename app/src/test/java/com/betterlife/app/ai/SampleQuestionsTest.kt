package com.betterlife.app.ai

import com.betterlife.app.data.Alcohol
import com.betterlife.app.data.Chronic
import com.betterlife.app.data.Exercise
import com.betterlife.app.data.Profile
import com.betterlife.app.data.Smoking
import com.betterlife.app.data.SugaryDrinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N3b 示例问题：各档案字段 → 对应模板；空档案/无命中 → 通用兜底；命中不足 3 条补齐、超出截断。
 */
class SampleQuestionsTest {

    @Test
    fun `空档案走通用兜底`() {
        assertEquals(SampleQuestions.GENERIC, SampleQuestions.forProfile(Profile.EMPTY))
    }

    @Test
    fun `无命中字段的完整档案走通用兜底`() {
        val profile = Profile().copy(exercise = Exercise.OK)
        assertEquals(SampleQuestions.GENERIC, SampleQuestions.forProfile(profile))
    }

    @Test
    fun `吸烟出戒烟模板`() {
        val questions = SampleQuestions.forProfile(Profile(smoking = Smoking.YES, exercise = Exercise.OK))
        assertEquals("想戒烟,第一步做什么?", questions.first())
    }

    @Test
    fun `已戒烟出恢复模板`() {
        val questions = SampleQuestions.forProfile(Profile(smoking = Smoking.QUIT, exercise = Exercise.OK))
        assertEquals("戒烟之后,身体多久能恢复?", questions.first())
    }

    @Test
    fun `各慢病出对应模板`() {
        assertEquals(
            "有高血压,日常最该注意什么?",
            SampleQuestions.forProfile(Profile(chronic = setOf(Chronic.HYPERTENSION), exercise = Exercise.OK)).first(),
        )
        assertEquals(
            "有糖尿病,吃饭先改什么?",
            SampleQuestions.forProfile(Profile(chronic = setOf(Chronic.DIABETES), exercise = Exercise.OK)).first(),
        )
        assertEquals(
            "肾不太好,饮食上先避开什么?",
            SampleQuestions.forProfile(Profile(chronic = setOf(Chronic.KIDNEY), exercise = Exercise.OK)).first(),
        )
        assertEquals(
            "心脏不太好,运动上要注意什么?",
            SampleQuestions.forProfile(Profile(chronic = setOf(Chronic.HEART), exercise = Exercise.OK)).first(),
        )
        assertEquals(
            "有慢性病,日常先盯哪几件事?",
            SampleQuestions.forProfile(Profile(chronic = setOf(Chronic.OTHER), exercise = Exercise.OK)).first(),
        )
    }

    @Test
    fun `其余命中字段各出对应模板`() {
        val cases = listOf(
            Profile(alcohol = Alcohol.OFTEN, exercise = Exercise.OK) to "经常喝酒,怎么把伤害降下来?",
            Profile(betelNut = true, exercise = Exercise.OK) to "嚼槟榔,怎么戒掉?",
            Profile(sugaryDrinks = SugaryDrinks.DAILY, exercise = Exercise.OK) to "每天都喝含糖饮料,怎么减?",
            Profile(sleepShort = true, exercise = Exercise.OK) to "睡不够,怎么把睡眠补回来?",
            Profile(exercise = Exercise.NONE) to "平时不运动,从哪件小事开始?",
            Profile(pregnant = true, exercise = Exercise.OK) to "孕期睡眠不好,怎么办?",
            Profile(financialStress = true, exercise = Exercise.OK) to "压力大到影响生活,先做什么?",
        )
        cases.forEach { (profile, expected) ->
            assertTrue(expected, expected in SampleQuestions.forProfile(profile))
        }
    }

    @Test
    fun `命中不足三条时用通用问题补齐`() {
        val questions = SampleQuestions.forProfile(Profile(smoking = Smoking.YES, exercise = Exercise.OK))
        assertEquals(3, questions.size)
        assertEquals("想戒烟,第一步做什么?", questions[0])
        assertEquals(SampleQuestions.GENERIC.take(2), questions.drop(1))
    }

    @Test
    fun `多个命中按优先级截断到三条`() {
        val profile = Profile(
            smoking = Smoking.YES,
            chronic = setOf(Chronic.DIABETES),
            alcohol = Alcohol.OFTEN,
            sleepShort = true,
        )
        assertEquals(
            listOf("想戒烟,第一步做什么?", "有糖尿病,吃饭先改什么?", "经常喝酒,怎么把伤害降下来?"),
            SampleQuestions.forProfile(profile),
        )
    }

    @Test
    fun `多慢病按枚举声明顺序排列且去重稳定`() {
        val profile = Profile(chronic = setOf(Chronic.DIABETES, Chronic.HYPERTENSION), exercise = Exercise.OK)
        assertEquals(
            listOf("有高血压,日常最该注意什么?", "有糖尿病,吃饭先改什么?") + SampleQuestions.GENERIC.take(1),
            SampleQuestions.forProfile(profile),
        )
    }
}
