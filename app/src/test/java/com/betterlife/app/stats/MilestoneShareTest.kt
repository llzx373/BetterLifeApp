package com.betterlife.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MilestoneShareTest {

    private val date = LocalDate.of(2026, 10, 1)

    @Test
    fun `只有连签里程碑出日签`() {
        assertTrue(isStreakMilestone(AchievementKeys.STREAK_7))
        assertTrue(isStreakMilestone(AchievementKeys.STREAK_30))
        assertTrue(isStreakMilestone(AchievementKeys.STREAK_100))
        assertFalse(isStreakMilestone(AchievementKeys.TOTAL_10))
        assertFalse(isStreakMilestone(AchievementKeys.TOTAL_1000))
        assertFalse(isStreakMilestone(AchievementKeys.PERFECT_WEEK))
    }

    @Test
    fun `优先从已 DONE 集合里挑`() {
        val picked = pickMilestoneQuote(
            doneEntryIds = setOf("a", "b", "c"),
            seedEntryIds = listOf("s1", "s2"),
            date = date,
        )
        assertTrue(picked in setOf("a", "b", "c"))
    }

    @Test
    fun `没有 DONE 时落回种子池`() {
        val picked = pickMilestoneQuote(
            doneEntryIds = emptySet(),
            seedEntryIds = listOf("s1", "s2", "s3"),
            date = date,
        )
        assertTrue(picked in listOf("s1", "s2", "s3"))
    }

    @Test
    fun `两边都空返回 null`() {
        assertNull(pickMilestoneQuote(emptySet(), emptyList(), date))
    }

    @Test
    fun `同一天同一候选集稳定选中同一条`() {
        val done = setOf("a", "b", "c", "d", "e")
        repeat(5) {
            assertEquals(pickMilestoneQuote(done, emptyList(), date), pickMilestoneQuote(done, emptyList(), date))
        }
        // 与集合的迭代顺序无关:换顺序构造的同内容集合选同一条
        assertEquals(
            pickMilestoneQuote(setOf("a", "b", "c", "d", "e"), emptyList(), date),
            pickMilestoneQuote(setOf("e", "d", "c", "b", "a"), emptyList(), date),
        )
    }

    @Test
    fun `不同天会轮换到不同条`() {
        val done = (1..10).map { "e$it" }.toSet()
        val picked = (0L until 30).mapNotNull { pickMilestoneQuote(done, emptyList(), date.plusDays(it)) }.toSet()
        assertTrue("30 天内应轮换到不止一条", picked.size > 1)
    }
}
