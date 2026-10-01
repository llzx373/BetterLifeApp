package com.betterlife.app.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N2c 挽回通知决策：满 3 天触发、2 天不触发、7 天频控、打开 App（写当天日期）即重置、
 * 从未记录活跃日期不发、开关关闭不发。
 */
class ReengageDecisionTest {

    @Test
    fun `满 3 天未打开且从未发过则触发`() {
        assertTrue(decideReengageNotify("2026-09-28", "", "2026-10-01", enabled = true))
    }

    @Test
    fun `超过 3 天同样触发`() {
        assertTrue(decideReengageNotify("2026-09-01", "", "2026-10-01", enabled = true))
    }

    @Test
    fun `只隔 2 天不触发`() {
        assertFalse(decideReengageNotify("2026-09-29", "", "2026-10-01", enabled = true))
    }

    @Test
    fun `打开 App 当天计时重置不触发`() {
        assertFalse(decideReengageNotify("2026-10-01", "", "2026-10-01", enabled = true))
    }

    @Test
    fun `上次发送距今不足 7 天被频控`() {
        assertFalse(decideReengageNotify("2026-09-01", "2026-09-28", "2026-10-01", enabled = true))
    }

    @Test
    fun `上次发送满 7 天可再发`() {
        assertTrue(decideReengageNotify("2026-09-01", "2026-09-24", "2026-10-01", enabled = true))
    }

    @Test
    fun `开关关闭则不发`() {
        assertFalse(decideReengageNotify("2026-09-01", "", "2026-10-01", enabled = false))
    }

    @Test
    fun `从未记录活跃日期不发`() {
        // 升级前的老用户还没打开过新版：等第一次打开落日期，不补发
        assertFalse(decideReengageNotify("", "", "2026-10-01", enabled = true))
    }

    @Test
    fun `非法日期不发`() {
        assertFalse(decideReengageNotify("not-a-date", "", "2026-10-01", enabled = true))
        assertFalse(decideReengageNotify("2026-09-01", "", "not-a-date", enabled = true))
        // 上次发送日期非法视为从未发过，不挡触发
        assertTrue(decideReengageNotify("2026-09-01", "not-a-date", "2026-10-01", enabled = true))
    }
}
