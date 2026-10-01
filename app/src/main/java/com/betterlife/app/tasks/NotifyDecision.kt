// N2b：HC 自动核销后的通知决策。纯 Kotlin，不依赖 Android，可单测。
//
// 防打扰铁律（与每日汇总提醒互斥）：
// - 自动核销后若无未完成项，当天只发一条报喜，不再发「你还有 N 件事没做」式的汇总；
// - 若还有未完成项，报喜证据合并进每日汇总文案，不单独报喜；
// - 同日同事件不重复发（当日已发过报喜则由 DataStore 标记抑制，见 SettingsStore）。
//
// N2c：3 日未打开挽回通知的决策（decideReengageNotify）也在本文件，同为纯函数。
package com.betterlife.app.tasks

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 核销后的通知动作 */
enum class AutoNotifyType {
    /** 只发报喜（全部完成、今天还没报喜过） */
    PRAISE,

    /** 发每日汇总（本轮无核销走原文案；有核销且有未完成时把证据并进文案） */
    SUMMARY,

    /** 什么都不发（同日重发抑制） */
    NONE,
}

/**
 * 决定本轮自动核销之后发什么通知。
 *
 * @param completions 本轮核销的达标证据文案列表（如「今日步数 9234 ≥ 7000」），空 = 没核销
 * @param undoneCount 核销后今日仍未完成的任务数
 * @param praiseEnabled 设置页「自动打卡报喜」开关
 * @param praiseSentToday 今天是否已发过报喜（同日同事件不重复发）
 */
fun decideAutoNotify(
    completions: List<String>,
    undoneCount: Int,
    praiseEnabled: Boolean,
    praiseSentToday: Boolean,
): AutoNotifyType = when {
    // 本轮没核销到任何东西：维持每日汇总的原行为
    completions.isEmpty() -> AutoNotifyType.SUMMARY
    // 还有未完成项：不单独报喜，证据合并进汇总（由调用方拼文案）
    undoneCount > 0 -> AutoNotifyType.SUMMARY
    // 全部完成但开关关了：退回原有「全部完成」汇总
    !praiseEnabled -> AutoNotifyType.SUMMARY
    // 全部完成但今天已经报喜过：同日同事件不重复发
    praiseSentToday -> AutoNotifyType.NONE
    else -> AutoNotifyType.PRAISE
}

// N2c 挽回通知的发送窗口：满 3 天没打开才发，每 7 天最多一条
private const val REENGAGE_AFTER_DAYS = 3L
private const val REENGAGE_MIN_INTERVAL_DAYS = 7L

/**
 * N2c：决定今天是否发「久未打开」挽回通知。
 *
 * @param lastActiveDate 最近一次打开 App 的日期（yyyy-MM-dd），空 = 从未记录过
 * @param lastSentDate 上次发挽回通知的日期（yyyy-MM-dd），空 = 从未发过
 * @param today 今天（yyyy-MM-dd）
 * @param enabled 设置页「久未打开提醒」开关
 */
fun decideReengageNotify(
    lastActiveDate: String,
    lastSentDate: String,
    today: String,
    enabled: Boolean,
): Boolean {
    if (!enabled) return false
    val todayD = parseDate(today) ?: return false
    // 从未记录过活跃日期（升级前的老用户还没打开过新版）：不发，等第一次打开落日期。
    // 打开 App 写入当天日期后，间隔归零 < 3 天，计时即自然重置
    val lastActive = parseDate(lastActiveDate) ?: return false
    if (ChronoUnit.DAYS.between(lastActive, todayD) < REENGAGE_AFTER_DAYS) return false
    val lastSent = parseDate(lastSentDate) ?: return true
    return ChronoUnit.DAYS.between(lastSent, todayD) >= REENGAGE_MIN_INTERVAL_DAYS
}

private fun parseDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
