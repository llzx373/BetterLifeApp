package com.betterlife.app.data.health

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** 运动 session 只按与「今天」的交集计时，跨午夜部分不给今天（防误核销） */
class ExerciseMinutesClipTest {

    private fun at(iso: String): Instant = Instant.parse(iso)

    // 今天的窗口：[2026-03-05T00:00Z, 2026-03-05T23:00Z)
    private val dayStart = at("2026-03-05T00:00:00Z")
    private val dayEnd = at("2026-03-05T23:00:00Z")

    @Test
    fun `完全落在今天内的 session 计全长`() {
        val millis = exerciseOverlapMillis(
            at("2026-03-05T08:00:00Z"), at("2026-03-05T09:30:00Z"), dayStart, dayEnd,
        )
        assertEquals(90 * 60_000L, millis)
    }

    @Test
    fun `跨午夜从昨晚开始的 session 只计今天部分`() {
        // 昨晚 23:00 → 今天 01:00，两小时只给今天计 60 分钟
        val millis = exerciseOverlapMillis(
            at("2026-03-04T23:00:00Z"), at("2026-03-05T01:00:00Z"), dayStart, dayEnd,
        )
        assertEquals(60 * 60_000L, millis)
    }

    @Test
    fun `超出当前时刻的部分不计入（session 未结束）`() {
        // 今天 22:00 开始、记录端点晚于 now（窗口终点 = 查询时刻）：只计到窗口终点
        val millis = exerciseOverlapMillis(
            at("2026-03-05T22:00:00Z"), at("2026-03-05T23:30:00Z"), dayStart, dayEnd,
        )
        assertEquals(60 * 60_000L, millis)
    }

    @Test
    fun `完全在昨天无交集计零`() {
        val millis = exerciseOverlapMillis(
            at("2026-03-04T10:00:00Z"), at("2026-03-04T11:00:00Z"), dayStart, dayEnd,
        )
        assertEquals(0L, millis)
    }

    @Test
    fun `端点恰好贴边界按零交集处理`() {
        // 在窗口起点整结束：交集长度为 0
        val millis = exerciseOverlapMillis(
            at("2026-03-04T23:00:00Z"), at("2026-03-05T00:00:00Z"), dayStart, dayEnd,
        )
        assertEquals(0L, millis)
    }
}
