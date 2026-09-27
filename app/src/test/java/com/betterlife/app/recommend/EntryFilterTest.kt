package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 条目筛选：维度间 AND、维度内 OR、空维度不约束（纯函数） */
class EntryFilterTest {

    private fun entry(
        id: String,
        ratio: String = "高",
        grade: String = "A",
        lens: String = "金钱",
    ) = EntryDto(id = id, sec = 1, n = 1, title = "t$id", ratio = ratio, grade = grade, lens = lens)

    private val entries = listOf(
        entry("01-01", ratio = "极高", grade = "A", lens = "死亡率"),
        entry("01-02", ratio = "高", grade = "B", lens = "金钱"),
        entry("01-03", ratio = "一般", grade = "C", lens = "时间"),
        entry("01-04", ratio = "高", grade = "A", lens = "自由"),
    )

    @Test
    fun `空筛选不过滤任何条目`() {
        assertEquals(entries, entries.applyFilter(EntryFilter()))
        assertTrue(EntryFilter().isEmpty)
    }

    @Test
    fun `单维度单值过滤`() {
        val result = entries.applyFilter(EntryFilter(ratios = setOf("高")))
        assertEquals(listOf("01-02", "01-04"), result.map { it.id })
    }

    @Test
    fun `维度内多值取并集`() {
        val result = entries.applyFilter(EntryFilter(grades = setOf("A", "C")))
        assertEquals(listOf("01-01", "01-03", "01-04"), result.map { it.id })
    }

    @Test
    fun `维度之间取交集`() {
        val result = entries.applyFilter(
            EntryFilter(ratios = setOf("高", "极高"), grades = setOf("A")),
        )
        assertEquals(listOf("01-01", "01-04"), result.map { it.id })
    }

    @Test
    fun `三维度同时约束`() {
        val result = entries.applyFilter(
            EntryFilter(ratios = setOf("高"), grades = setOf("A"), lenses = setOf("自由")),
        )
        assertEquals(listOf("01-04"), result.map { it.id })
    }

    @Test
    fun `无匹配返回空列表`() {
        val result = entries.applyFilter(
            EntryFilter(ratios = setOf("一般"), grades = setOf("A")),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `空字段不被筛选值命中`() {
        val blank = entry("01-05", ratio = "", grade = "", lens = "")
        val result = (entries + blank).applyFilter(EntryFilter(lenses = setOf("金钱")))
        assertEquals(listOf("01-02"), result.map { it.id })
    }

    @Test
    fun `开关切换增删筛选值`() {
        var f = EntryFilter()
        f = f.toggleRatio("高")
        assertEquals(setOf("高"), f.ratios)
        f = f.toggleRatio("极高")
        assertEquals(setOf("高", "极高"), f.ratios)
        f = f.toggleRatio("高")
        assertEquals(setOf("极高"), f.ratios)
        assertEquals(1, f.activeCount)
        assertFalse(f.isEmpty)
    }

    @Test
    fun `matchesFilter 与 applyFilter 一致`() {
        val f = EntryFilter(ratios = setOf("高", "一般"), lenses = setOf("时间", "自由"))
        entries.forEach { e ->
            assertEquals(e in entries.applyFilter(f), e.matchesFilter(f))
        }
    }
}
