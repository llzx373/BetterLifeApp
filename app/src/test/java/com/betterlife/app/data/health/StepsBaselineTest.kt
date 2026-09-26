package com.betterlife.app.data.health

import org.junit.Assert.assertEquals
import org.junit.Test

/** 传感器「开机累计值 → 今日步数」的换算：首次、同日、跨天、重启清零 */
class StepsBaselineTest {

    @Test
    fun `首次使用从当前读数起算今日从零开始`() {
        val (next, steps) = computeTodaySteps(prev = null, counter = 5000, today = "2026-09-25")

        assertEquals(0L, steps)
        assertEquals(StepsBaseline("2026-09-25", baseline = 5000, lastCounter = 5000), next)
    }

    @Test
    fun `同一天步数等于累计减基线`() {
        val prev = StepsBaseline("2026-09-25", baseline = 5000, lastCounter = 5100)

        val (next, steps) = computeTodaySteps(prev, counter = 5300, today = "2026-09-25")

        assertEquals(300L, steps)
        assertEquals(5000L, next.baseline) // 基线不动
        assertEquals(5300L, next.lastCounter)
    }

    @Test
    fun `跨天以昨日最后读数为零点基线`() {
        // 昨晚睡前计数器 8000；今晨读到 8300 → 今天走了 300（夜里走的也算今天的）
        val prev = StepsBaseline("2026-09-24", baseline = 2000, lastCounter = 8000)

        val (next, steps) = computeTodaySteps(prev, counter = 8300, today = "2026-09-25")

        assertEquals(300L, steps)
        assertEquals(StepsBaseline("2026-09-25", baseline = 8000, lastCounter = 8300), next)
    }

    @Test
    fun `重启后计数器清零则开机累计即今日步数`() {
        // 同一天但读数变小了 → 重启过；今日步数近似为开机以来的累计
        val prev = StepsBaseline("2026-09-25", baseline = 5000, lastCounter = 7000)

        val (next, steps) = computeTodaySteps(prev, counter = 120, today = "2026-09-25")

        assertEquals(120L, steps)
        assertEquals(0L, next.baseline)
    }

    @Test
    fun `跨天且重启优先按重启处理不出负数`() {
        // 昨天读到 8000，重启后今天只累计到 50：若按跨天基线会算出负数
        val prev = StepsBaseline("2026-09-24", baseline = 2000, lastCounter = 8000)

        val (_, steps) = computeTodaySteps(prev, counter = 50, today = "2026-09-25")

        assertEquals(50L, steps)
    }
}
