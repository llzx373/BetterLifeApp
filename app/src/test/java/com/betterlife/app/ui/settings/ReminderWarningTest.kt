package com.betterlife.app.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提醒开着但通知权限被收回时,设置页要显示警告行(而不是静默失效) */
class ReminderWarningTest {

    @Test
    fun `提醒开启且通知权限在时不显示警告`() {
        assertFalse(reminderWarningNeeded(reminderEnabled = true, notificationsEnabled = true))
    }

    @Test
    fun `提醒开启但通知权限被收回时显示警告`() {
        assertTrue(reminderWarningNeeded(reminderEnabled = true, notificationsEnabled = false))
    }

    @Test
    fun `提醒关闭时不显示警告`() {
        assertFalse(reminderWarningNeeded(reminderEnabled = false, notificationsEnabled = false))
    }
}
