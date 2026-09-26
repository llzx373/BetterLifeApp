package com.betterlife.app.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 单任务提醒触发时刻：目标时间晚于当前→今天触发；已过→顺延明天 */
class TaskReminderTimeTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定 now 的构造入口，不依赖系统时钟 */
    private fun millis(date: String, time: String): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `目标时间晚于当前则今天触发`() {
        val now = millis("2026-03-05", "08:00")
        val trigger = nextTriggerMillis(now, 9 * 60 + 30, zone)
        assertEquals(millis("2026-03-05", "09:30"), trigger)
    }

    @Test
    fun `已过时间则顺延到明天`() {
        val now = millis("2026-03-05", "20:00")
        val trigger = nextTriggerMillis(now, 7 * 60 + 15, zone)
        assertEquals(millis("2026-03-06", "07:15"), trigger)
    }

    @Test
    fun `跨午夜边界正确`() {
        // 23:50 设 00:05：目标时间已过（今天 00:05 早就过了），顺延到明天 00:05，
        // 也就是 15 分钟后，而不是再等近 24 小时
        val now = millis("2026-03-05", "23:50")
        val trigger = nextTriggerMillis(now, 5, zone)
        assertEquals(millis("2026-03-06", "00:05"), trigger)
    }
}
