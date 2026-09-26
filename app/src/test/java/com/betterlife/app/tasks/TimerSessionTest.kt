package com.betterlife.app.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计时会话的正确性只靠「endAt - 真实时钟」，不靠 tick 计数。
 * 这里的假时钟就是「切后台 90 秒再回来」的等价物：中间没有任何 tick，读数必须仍然正确。
 */
class TimerSessionTest {

    private class FakeClock(var now: Long = 0L) {
        fun clock(): () -> Long = { now }
    }

    @Test
    fun `开始后剩余时间按真实时钟递减`() {
        val clock = FakeClock()
        val session = TimerSession(durationMillis = 10 * 60_000L, clock = clock.clock())

        clock.now += 90_000L // 切后台 90 秒，期间没有任何 tick

        assertEquals(10 * 60_000L - 90_000L, session.remainingMillis())
    }

    @Test
    fun `超过结束时刻剩余为零且判为结束`() {
        val clock = FakeClock()
        val session = TimerSession(durationMillis = 60_000L, clock = clock.clock())

        clock.now += 120_000L

        assertEquals(0L, session.remainingMillis())
        assertTrue(session.isFinished)
    }

    @Test
    fun `暂停冻结剩余时间，暂停期间经过的时长不计入`() {
        val clock = FakeClock()
        val session = TimerSession(durationMillis = 60_000L, clock = clock.clock())

        clock.now += 20_000L
        session.pause()
        clock.now += 10 * 60_000L // 暂停 10 分钟

        assertEquals(40_000L, session.remainingMillis())
        assertTrue(session.isPaused)
        assertFalse(session.isFinished)
    }

    @Test
    fun `继续后从冻结的剩余时间接着走`() {
        val clock = FakeClock()
        val session = TimerSession(durationMillis = 60_000L, clock = clock.clock())

        clock.now += 20_000L
        session.pause()
        clock.now += 10 * 60_000L
        session.resume()
        clock.now += 15_000L

        assertEquals(25_000L, session.remainingMillis())
        assertFalse(session.isPaused)
    }

    @Test
    fun `重复暂停与继续幂等`() {
        val clock = FakeClock()
        val session = TimerSession(durationMillis = 60_000L, clock = clock.clock())

        session.pause()
        session.pause() // 第二次暂停不该再改冻结值
        clock.now += 5_000L
        assertEquals(60_000L, session.remainingMillis())

        session.resume()
        session.resume() // 未暂停时 resume 是空操作
        assertEquals(60_000L, session.remainingMillis())
    }

    @Test
    fun `进度从 0 走向 1`() {
        val clock = FakeClock()
        val session = TimerSession(durationMillis = 100_000L, clock = clock.clock())

        assertEquals(0f, session.progress(), 0.001f)
        clock.now += 25_000L
        assertEquals(0.25f, session.progress(), 0.001f)
        clock.now += 100_000L
        assertEquals(1f, session.progress(), 0.001f)
    }

    @Test
    fun `剩余时间格式化为分秒且秒向上取整`() {
        assertEquals("10:00", formatRemainingMillis(600_000L))
        assertEquals("8:35", formatRemainingMillis(515_000L))
        // 515_400ms = 8 分 35.4 秒，向上取整显示 8:36，避免开局就少一秒
        assertEquals("8:36", formatRemainingMillis(515_400L))
        assertEquals("0:01", formatRemainingMillis(1L))
        assertEquals("0:00", formatRemainingMillis(0L))
    }
}
