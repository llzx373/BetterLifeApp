package com.betterlife.app.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 设为/转为每日习惯时今天是否立即补行的判断（TaskManager.ensureTodayDailyRow 用） */
class DailyRowMaterializationTest {

    @Test
    fun `今天已有安排则补行`() {
        assertTrue(shouldMaterializeDailyRow(todayRowCount = 3, otherDailyHabitCount = 2))
    }

    @Test
    fun `今天没安排且没有其他每日习惯则补行`() {
        // 唯一的每日习惯：不补行就要等下次 ensureTodayTasks 才出现，看起来像「消失」
        assertTrue(shouldMaterializeDailyRow(todayRowCount = 0, otherDailyHabitCount = 0))
    }

    @Test
    fun `今天没安排且还有其他每日习惯则留给统一规划`() {
        // 抢先插行会让 ensureTodayTasks 误判「今天已规划」而漏掉其他习惯
        assertFalse(shouldMaterializeDailyRow(todayRowCount = 0, otherDailyHabitCount = 1))
    }
}
