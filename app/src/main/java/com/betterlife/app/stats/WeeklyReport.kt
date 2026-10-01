// N4：每周日晚周报推送的决策与文案。纯 Kotlin，不依赖 Android，可单测。
//
// 内容口径与统计页一致：汇报本周（周一~周日，触发日周日即本周最后一天）的打卡次数，
// 环比上一周，附当前最长连签。
// 0 打卡周不发——没有正向内容可说的总结推送是反效果（写进 decideWeeklyReport）。
// 中文模板直接写在这里：本包不在 UiNoChineseLiteralTest 扫描范围（只扫 ui/ 包），
// 且这不是 UI 文案而是通知正文，与 StatsPrompt 的中文提示词同属领域文案。
package com.betterlife.app.stats

import com.betterlife.app.tasks.weekRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 周报触发时刻：每周日 20:07（避开整点） */
const val WEEKLY_REPORT_HOUR = 20
const val WEEKLY_REPORT_MINUTE = 7

/**
 * 下一次周报触发时刻（epoch 毫秒）：最近一个周日 20:07，已过点则顺延到下周日。
 * 与 TaskReminderTiming.nextTriggerMillis 同风格，提成纯函数便于单测。
 */
fun nextWeeklyReportMillis(nowMillis: Long, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    var target = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        .withHour(WEEKLY_REPORT_HOUR).withMinute(WEEKLY_REPORT_MINUTE)
        .withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusWeeks(1)
    return target.toInstant().toEpochMilli()
}

/**
 * 决定本轮是否发周报。
 *
 * @param today 今天（yyyy-MM-dd）
 * @param doneThisWeek 本周（周一~周日）打卡次数
 * @param enabled 设置页「每周总结」开关
 * @param lastSentWeek 上次已发周报所属的周一日期（yyyy-MM-dd），空 = 从未发过（同周频控依据）
 */
fun decideWeeklyReport(
    today: String,
    doneThisWeek: Int,
    enabled: Boolean,
    lastSentWeek: String,
): Boolean {
    if (!enabled) return false
    val todayD = runCatching { LocalDate.parse(today) }.getOrNull() ?: return false
    if (todayD.dayOfWeek != DayOfWeek.SUNDAY) return false
    // 上周（触发日本周）0 打卡：没有正向内容可说的总结推送是反效果，不打扰
    if (doneThisWeek <= 0) return false
    // 同周不重复发：worker 重试或周期 work 在同一周日重复触发时抑制
    return weekRange(todayD).first.toString() != lastSentWeek
}

/**
 * 周报一句话文案：打卡次数 + 环比 + 当前最长连签。
 * 环比下滑时不报数字（正向强化 > 负罪感），只报本周次数；连签为 0 时省略连签段。
 *
 * @param doneThisWeek 本周打卡次数
 * @param donePrevWeek 上一周打卡次数（环比基线）
 * @param currentStreak 当前最长连签天数
 */
fun weeklyReportText(doneThisWeek: Int, donePrevWeek: Int, currentStreak: Int): String {
    val compare = when {
        doneThisWeek > donePrevWeek -> "，比上周多 ${doneThisWeek - donePrevWeek} 次"
        doneThisWeek == donePrevWeek -> "，与上周持平"
        else -> ""
    }
    val streak = if (currentStreak > 0) "，最长连签 $currentStreak 天" else ""
    return "这周你打卡了 $doneThisWeek 次$compare$streak"
}
