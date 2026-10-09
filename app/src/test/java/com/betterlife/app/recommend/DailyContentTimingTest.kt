package com.betterlife.app.recommend

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 每日一条锚点：最近一个 08:07，已过点顺延明天（自续链每轮重锚的依据） */
class DailyContentTimingTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定 now 的构造入口，不依赖系统时钟 */
    private fun millis(date: String, time: String): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `08-07 前则当天触发`() {
        val now = millis("2026-03-05", "07:00")
        assertEquals(millis("2026-03-05", "08:07"), nextDailyContentMillis(now, zone))
    }

    @Test
    fun `已过 08-07 则顺延到明天`() {
        val now = millis("2026-03-05", "20:00")
        assertEquals(millis("2026-03-06", "08:07"), nextDailyContentMillis(now, zone))
    }

    @Test
    fun `恰好 08-07-00 也算已过点顺延明天`() {
        // 锚点必须严格晚于 now：worker 在 08:07 触发后重锚，不能又排回同一时刻
        val now = millis("2026-03-05", "08:07")
        assertEquals(millis("2026-03-06", "08:07"), nextDailyContentMillis(now, zone))
    }

    @Test
    fun `DST 拨快次日仍锚定墙钟 08-07`() {
        // 美东 2026-03-08 凌晨拨快 1 小时：真实间隔不足 24h，墙钟仍 08:07
        val usZone = ZoneId.of("America/New_York")
        fun usMillis(date: String, time: String): Long =
            LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(usZone).toInstant().toEpochMilli()
        val now = usMillis("2026-03-07", "12:00")
        assertEquals(usMillis("2026-03-08", "08:07"), nextDailyContentMillis(now, usZone))
    }
}
