package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.db.EntryStateEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** 条目状态统计：每条只落一个桶（DONE 优先于 DISMISSED），计划状态不影响待看计数 */
class EntryStatsTest {

    private fun entry(id: String) = EntryDto(id = id, sec = 1, n = 1, title = "t$id")

    private val entries = listOf(entry("a"), entry("b"), entry("c"), entry("d"))

    @Test
    fun `空列表全是零`() {
        assertEquals(EntryStats(0, 0, 0), computeEntryStats(emptyList(), emptyMap()))
    }

    @Test
    fun `无状态全是待看`() {
        assertEquals(EntryStats(4, 0, 0), computeEntryStats(entries, emptyMap()))
    }

    @Test
    fun `完成与忽略分别计数其余待看`() {
        val states = mapOf(
            "a" to setOf(EntryStateEntity.STATE_DONE),
            "b" to setOf(EntryStateEntity.STATE_DISMISSED),
        )
        assertEquals(EntryStats(2, 1, 1), computeEntryStats(entries, states))
    }

    @Test
    fun `DONE 与 DISMISSED 同时存在时落 DONE 桶`() {
        val states = mapOf(
            "a" to setOf(EntryStateEntity.STATE_DONE, EntryStateEntity.STATE_DISMISSED),
        )
        assertEquals(EntryStats(3, 1, 0), computeEntryStats(entries, states))
    }

    @Test
    fun `计划状态不影响待看计数`() {
        val states = mapOf(
            "a" to setOf(EntryStateEntity.STATE_TODO),
            "b" to setOf(EntryStateEntity.STATE_DAILY),
            "c" to setOf(EntryStateEntity.STATE_FAVORITE),
        )
        assertEquals(EntryStats(4, 0, 0), computeEntryStats(entries, states))
    }
}
