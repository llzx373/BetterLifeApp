package com.betterlife.app.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 连签计算：打卡计数、请假搭桥（不断签不计数）、其他日子断签 */
class StreakTest {

    private val today: LocalDate = LocalDate.parse("2026-03-05")

    private fun streak(done: Set<String>, leave: Set<String> = emptySet()): Int =
        computeStreak(done, leave, today)

    @Test
    fun `连续打卡到今天则全计`() {
        assertEquals(3, streak(setOf("2026-03-03", "2026-03-04", "2026-03-05")))
    }

    @Test
    fun `今天没打卡则从昨天往前数`() {
        assertEquals(2, streak(setOf("2026-03-03", "2026-03-04")))
    }

    @Test
    fun `今天没打卡且昨天也没打卡则为零`() {
        assertEquals(0, streak(setOf("2026-03-02")))
    }

    @Test
    fun `中间断档则连签中止`() {
        // 03-02 缺卡：连签只数到 03-03 ~ 03-05 这三天，够不到 03-01
        assertEquals(3, streak(setOf("2026-03-01", "2026-03-03", "2026-03-04", "2026-03-05")))
    }

    @Test
    fun `请假搭起单个断档`() {
        // 03-03 请假：03-01 ~ 03-05 视为连续
        assertEquals(
            4,
            streak(
                done = setOf("2026-03-01", "2026-03-02", "2026-03-04", "2026-03-05"),
                leave = setOf("2026-03-03"),
            ),
        )
    }

    @Test
    fun `请假可连续搭多个断档但不计入天数`() {
        // 03-03、03-04 都请假：连上 03-02，但请假日本身不计数
        assertEquals(
            2,
            streak(
                done = setOf("2026-03-02", "2026-03-05"),
                leave = setOf("2026-03-03", "2026-03-04"),
            ),
        )
    }

    @Test
    fun `连签起点的请假继续往前搭`() {
        // 从今天数到 03-03 后，03-02 请假继续搭到 03-01
        assertEquals(
            4,
            streak(
                done = setOf("2026-03-01", "2026-03-03", "2026-03-04", "2026-03-05"),
                leave = setOf("2026-03-02"),
            ),
        )
    }

    @Test
    fun `今天请假算桥不影响连签`() {
        assertEquals(
            2,
            streak(
                done = setOf("2026-03-03", "2026-03-04"),
                leave = setOf("2026-03-05"),
            ),
        )
    }

    @Test
    fun `今天请假但此前已断则为零`() {
        // 03-04 既没打卡也没请假，从 03-04（今天请假日的前一步）断掉
        assertEquals(0, streak(done = setOf("2026-03-02"), leave = setOf("2026-03-05")))
    }

    @Test
    fun `只有请假没有打卡则为零`() {
        assertEquals(0, streak(done = emptySet(), leave = setOf("2026-03-04", "2026-03-05")))
    }

    @Test
    fun `请假落在断档之外不改变结果`() {
        // 03-01 请假，但 03-02 断档在前，连签从 03-03 算起
        assertEquals(
            3,
            streak(
                done = setOf("2026-03-03", "2026-03-04", "2026-03-05"),
                leave = setOf("2026-03-01"),
            ),
        )
    }
}
