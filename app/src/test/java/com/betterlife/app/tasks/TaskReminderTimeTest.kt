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

    // ---- 指定日期（一次性待办截止日）维度 ----

    @Test
    fun `截止日就是今天且时间未到则今天触发`() {
        val now = millis("2026-03-05", "08:00")
        val trigger = nextTriggerMillis(now, 9 * 60 + 30, zone, LocalDate.parse("2026-03-05"))
        assertEquals(millis("2026-03-05", "09:30"), trigger)
    }

    @Test
    fun `截止日就是今天但时间已过则原样返回过去时刻`() {
        // 返回过去时刻：work 立即执行，由 worker 自查决定是否跳过
        val now = millis("2026-03-05", "20:00")
        val trigger = nextTriggerMillis(now, 9 * 60 + 30, zone, LocalDate.parse("2026-03-05"))
        assertEquals(millis("2026-03-05", "09:30"), trigger)
    }

    @Test
    fun `截止日是明天则明天触发不顺延`() {
        val now = millis("2026-03-05", "20:00")
        val trigger = nextTriggerMillis(now, 7 * 60 + 15, zone, LocalDate.parse("2026-03-06"))
        assertEquals(millis("2026-03-06", "07:15"), trigger)
    }

    @Test
    fun `截止日已过则原样返回过去时刻`() {
        val now = millis("2026-03-05", "20:00")
        val trigger = nextTriggerMillis(now, 9 * 60, zone, LocalDate.parse("2026-03-01"))
        assertEquals(millis("2026-03-01", "09:00"), trigger)
    }

    // ---- 撤销打卡后的补排判断（shouldRescheduleReminder）----

    @Test
    fun `撤销补排-没设过提醒不补排`() {
        val now = millis("2026-03-05", "08:00")
        assertEquals(false, shouldRescheduleReminder(null, now, zone))
    }

    @Test
    fun `撤销补排-每日任务目标时间在未来则补排`() {
        val now = millis("2026-03-05", "08:00")
        assertEquals(true, shouldRescheduleReminder(9 * 60 + 30, now, zone))
    }

    @Test
    fun `撤销补排-每日任务不设日期时总能排到未来`() {
        // 无日期语义下已过点会顺延到明天，所以恒在未来
        val now = millis("2026-03-05", "20:00")
        assertEquals(true, shouldRescheduleReminder(7 * 60, now, zone))
    }

    @Test
    fun `撤销补排-一次性待办截止日今天但时间已过不补排`() {
        val now = millis("2026-03-05", "20:00")
        assertEquals(
            false,
            shouldRescheduleReminder(9 * 60, now, zone, LocalDate.parse("2026-03-05")),
        )
    }

    @Test
    fun `撤销补排-一次性待办截止日已过不补排`() {
        val now = millis("2026-03-05", "08:00")
        assertEquals(
            false,
            shouldRescheduleReminder(9 * 60, now, zone, LocalDate.parse("2026-03-01")),
        )
    }

    @Test
    fun `撤销补排-一次性待办截止日在未来则补排`() {
        val now = millis("2026-03-05", "20:00")
        assertEquals(
            true,
            shouldRescheduleReminder(9 * 60, now, zone, LocalDate.parse("2026-03-06")),
        )
    }
}
