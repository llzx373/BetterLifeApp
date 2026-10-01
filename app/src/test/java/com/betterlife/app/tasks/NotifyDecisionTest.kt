package com.betterlife.app.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * N2b 报喜通知决策：核销>0 & 无未完成 → 只报喜；有未完成 → 合并进汇总；
 * 同日重发抑制；开关关闭退回原汇总行为。
 */
class NotifyDecisionTest {

    private val evidences = listOf("今日步数 9234 ≥ 7000")

    @Test
    fun `核销且无未完成且未发过则只报喜`() {
        assertEquals(
            AutoNotifyType.PRAISE,
            decideAutoNotify(evidences, undoneCount = 0, praiseEnabled = true, praiseSentToday = false),
        )
    }

    @Test
    fun `核销但有未完成则合并进汇总不单独报喜`() {
        assertEquals(
            AutoNotifyType.SUMMARY,
            decideAutoNotify(evidences, undoneCount = 2, praiseEnabled = true, praiseSentToday = false),
        )
    }

    @Test
    fun `核销且无未完成但今天已发过则抑制重发`() {
        assertEquals(
            AutoNotifyType.NONE,
            decideAutoNotify(evidences, undoneCount = 0, praiseEnabled = true, praiseSentToday = true),
        )
    }

    @Test
    fun `本轮无核销则维持每日汇总原行为`() {
        assertEquals(
            AutoNotifyType.SUMMARY,
            decideAutoNotify(emptyList(), undoneCount = 0, praiseEnabled = true, praiseSentToday = false),
        )
        assertEquals(
            AutoNotifyType.SUMMARY,
            decideAutoNotify(emptyList(), undoneCount = 3, praiseEnabled = true, praiseSentToday = true),
        )
    }

    @Test
    fun `开关关闭则全部完成也走原汇总不发报喜`() {
        assertEquals(
            AutoNotifyType.SUMMARY,
            decideAutoNotify(evidences, undoneCount = 0, praiseEnabled = false, praiseSentToday = false),
        )
    }

    @Test
    fun `有未完成时已发过报喜仍发汇总`() {
        // 抑制只针对报喜本身；还有未完成时汇总是另一条通知，照发（证据并进文案）
        assertEquals(
            AutoNotifyType.SUMMARY,
            decideAutoNotify(evidences, undoneCount = 1, praiseEnabled = true, praiseSentToday = true),
        )
    }

    @Test
    fun `多条核销证据同样只发一条报喜`() {
        assertEquals(
            AutoNotifyType.PRAISE,
            decideAutoNotify(
                listOf("今日步数 9234 ≥ 7000", "昨晚睡眠 7.5 小时 ≥ 7 小时"),
                undoneCount = 0, praiseEnabled = true, praiseSentToday = false,
            ),
        )
    }
}
