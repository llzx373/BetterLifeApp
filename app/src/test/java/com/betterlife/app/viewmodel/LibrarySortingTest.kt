package com.betterlife.app.viewmodel

import com.betterlife.app.data.EntriesData
import com.betterlife.app.data.EntriesFile
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.RulesFile
import org.junit.Assert.assertEquals
import org.junit.Test

/** 条目库的目录着色与章内排序（纯函数，UI 只负责渲染） */
class LibrarySortingTest {

    private fun entry(
        id: String,
        sec: Int = 1,
        ratio: String = "高",
        grade: String = "A",
        lens: String = "金钱",
    ) = EntryDto(
        id = id, sec = sec, n = id.substringAfter('-').toInt(), title = "t$id",
        ratio = ratio, grade = grade, lens = lens,
    )

    private fun dataOf(entries: List<EntryDto>) =
        EntriesData(EntriesFile(entries = entries), RulesFile())

    @Test
    fun `主导口径取该章出现最多的口径`() {
        val data = dataOf(
            listOf(
                entry("01-01", lens = "金钱"),
                entry("01-02", lens = "金钱"),
                entry("01-03", lens = "时间"),
            ),
        )
        assertEquals("金钱", dominantLens(data, 1))
    }

    @Test
    fun `主导口径并列时按展示顺序取靠前的`() {
        val data = dataOf(
            listOf(
                entry("01-01", lens = "自由"),
                entry("01-02", lens = "死亡率"),
            ),
        )
        assertEquals("死亡率", dominantLens(data, 1))
    }

    @Test
    fun `空口径不参与主导口径统计`() {
        val data = dataOf(
            listOf(
                entry("01-01", lens = ""),
                entry("01-02", lens = ""),
                entry("01-03", lens = "时间"),
            ),
        )
        assertEquals("时间", dominantLens(data, 1))
    }

    @Test
    fun `按性价比排序时极高排在前面`() {
        val list = listOf(
            entry("01-01", ratio = "一般"),
            entry("01-02", ratio = "极高"),
        )
        assertEquals(listOf("01-02", "01-01"), sortEntries(list, EntrySort.RATIO).map { it.id })
    }

    @Test
    fun `按证据等级排序时 A 排在前面`() {
        val list = listOf(
            entry("01-01", grade = "C"),
            entry("01-02", grade = "A"),
        )
        assertEquals(listOf("01-02", "01-01"), sortEntries(list, EntrySort.GRADE).map { it.id })
    }

    @Test
    fun `按原书顺序排序时按条目编号`() {
        val list = listOf(entry("01-03"), entry("01-01"), entry("01-02"))
        assertEquals(listOf("01-01", "01-02", "01-03"), sortEntries(list, EntrySort.ORDER).map { it.id })
    }

    @Test
    fun `首屏填充与切换排序结果一致`() {
        val data = dataOf(
            listOf(
                entry("01-01", ratio = "一般"),
                entry("01-02", ratio = "极高"),
                entry("01-03", ratio = "高"),
            ),
        )
        assertEquals(
            sortEntries(data.bySection[1].orEmpty(), EntrySort.RATIO).map { it.id },
            sortedSectionEntries(data, 1, EntrySort.RATIO).map { it.id },
        )
    }

    @Test
    fun `首屏默认按性价比排序而非原书序`() {
        val data = dataOf(
            listOf(
                entry("01-01", ratio = "一般"),
                entry("01-02", ratio = "极高"),
            ),
        )
        assertEquals(
            listOf("01-02", "01-01"),
            sortedSectionEntries(data, 1, EntrySort.RATIO).map { it.id },
        )
    }
}
