package com.betterlife.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/** 搜索历史:新词置顶去重、超上限截断、空白不入(纯函数,SettingsStore 的写回也套同一规则) */
class SearchHistoryTest {

    @Test
    fun `新搜索置顶并去重`() {
        val old = listOf("戒烟", "体检", "午睡")
        assertEquals(listOf("体检", "戒烟", "午睡"), updateHistory(old, "体检"))
        assertEquals(listOf("跑步", "戒烟", "体检", "午睡"), updateHistory(old, "跑步"))
    }

    @Test
    fun `历史超过上限截断最旧`() {
        val old = (1..10).map { "词$it" }
        val updated = updateHistory(old, "新词")
        assertEquals(10, updated.size)
        assertEquals("新词", updated.first())
        assertEquals(listOf("新词") + (1..9).map { "词$it" }, updated)
    }

    @Test
    fun `空白query不入历史`() {
        val old = listOf("戒烟")
        assertEquals(old, updateHistory(old, ""))
        assertEquals(old, updateHistory(old, "   "))
        assertEquals(emptyList<String>(), updateHistory(emptyList(), "  "))
    }
}
