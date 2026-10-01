package com.betterlife.app.ui

import com.betterlife.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * N7 长辈模式的 tab 构成：简化为「今日 + AI 问答」两个;条目库/统计/待办
 * 收进「我的」页。普通模式保持四个 tab 不变。
 */
class SeniorTabsTest {

    @Test
    fun normalModeHasFourTabs() {
        assertEquals(
            listOf(TodayRoute::class, TodoRoute::class, LibraryRoute::class, MineRoute::class),
            tabsFor(seniorMode = false).map { it.routeClass },
        )
    }

    @Test
    fun seniorModeHasTodayAndChatOnly() {
        val tabs = tabsFor(seniorMode = true)
        assertEquals(listOf(TodayRoute::class, ChatRoute::class), tabs.map { it.routeClass })
        assertEquals(listOf(R.string.nav_today, R.string.title_chat), tabs.map { it.labelRes })
    }
}
