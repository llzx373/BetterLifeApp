package com.betterlife.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private fun keys(input: AchievementInput) = evaluateAchievements(input).map { it.key }

    @Test
    fun `什么都没有时返回空列表`() {
        assertTrue(evaluateAchievements(AchievementInput(0, 0, 0)).isEmpty())
    }

    @Test
    fun `连签阈值含边界`() {
        assertTrue(AchievementKeys.STREAK_7 !in keys(AchievementInput(bestStreak = 6, totalCompletions = 0, perfectWeeks = 0)))
        assertTrue(AchievementKeys.STREAK_7 in keys(AchievementInput(bestStreak = 7, totalCompletions = 0, perfectWeeks = 0)))
        assertTrue(AchievementKeys.STREAK_30 !in keys(AchievementInput(bestStreak = 29, totalCompletions = 0, perfectWeeks = 0)))
        assertTrue(AchievementKeys.STREAK_30 in keys(AchievementInput(bestStreak = 30, totalCompletions = 0, perfectWeeks = 0)))
        assertTrue(AchievementKeys.STREAK_100 !in keys(AchievementInput(bestStreak = 99, totalCompletions = 0, perfectWeeks = 0)))
        assertTrue(AchievementKeys.STREAK_100 in keys(AchievementInput(bestStreak = 100, totalCompletions = 0, perfectWeeks = 0)))
    }

    @Test
    fun `高阈值达成时低阈值一并达成`() {
        val result = keys(AchievementInput(bestStreak = 100, totalCompletions = 0, perfectWeeks = 0))
        assertTrue(
            AchievementKeys.STREAK_7 in result &&
                AchievementKeys.STREAK_30 in result &&
                AchievementKeys.STREAK_100 in result,
        )
    }

    @Test
    fun `累计阈值含边界`() {
        listOf(10, 50, 100, 500, 1000).forEach { threshold ->
            val below = keys(AchievementInput(bestStreak = 0, totalCompletions = threshold - 1, perfectWeeks = 0))
            val exact = keys(AchievementInput(bestStreak = 0, totalCompletions = threshold, perfectWeeks = 0))
            assertTrue("total_$threshold 不应在 ${threshold - 1} 时达成", "total_$threshold" !in below)
            assertTrue("total_$threshold 应在 $threshold 时达成", "total_$threshold" in exact)
        }
    }

    @Test
    fun `完美周有一个即达成`() {
        assertTrue(AchievementKeys.PERFECT_WEEK !in keys(AchievementInput(0, 0, perfectWeeks = 0)))
        assertTrue(AchievementKeys.PERFECT_WEEK in keys(AchievementInput(0, 0, perfectWeeks = 1)))
    }

    @Test
    fun `value 携带达成时的实际数字 顺序为连签 累计 完美周`() {
        val result = evaluateAchievements(AchievementInput(bestStreak = 30, totalCompletions = 60, perfectWeeks = 2))
        assertEquals(30, result.first { it.key == AchievementKeys.STREAK_30 }.value)
        assertEquals(60, result.first { it.key == AchievementKeys.TOTAL_50 }.value)
        assertEquals(2, result.first { it.key == AchievementKeys.PERFECT_WEEK }.value)
        assertEquals(
            listOf(
                AchievementKeys.STREAK_7, AchievementKeys.STREAK_30,
                AchievementKeys.TOTAL_10, AchievementKeys.TOTAL_50,
                AchievementKeys.PERFECT_WEEK,
            ),
            result.map { it.key },
        )
    }
}
