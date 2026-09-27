package com.betterlife.app.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自定义任务 id 的 "custom:" 前缀判断：决定标题解析与习惯移除时的清理行为 */
class CustomEntryIdTest {

    @Test
    fun `custom 前缀识别为自定义任务`() {
        assertTrue(TaskManager.isCustomEntryId("custom:3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
    }

    @Test
    fun `条目库 id 不是自定义任务`() {
        assertFalse(TaskManager.isCustomEntryId("02-01"))
        assertFalse(TaskManager.isCustomEntryId("11-03"))
    }

    @Test
    fun `空串与相似前缀不误判`() {
        assertFalse(TaskManager.isCustomEntryId(""))
        assertFalse(TaskManager.isCustomEntryId("custom"))
        assertFalse(TaskManager.isCustomEntryId("customx:abc"))
    }
}
