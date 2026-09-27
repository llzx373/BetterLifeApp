package com.betterlife.app.tasks

import com.betterlife.app.data.db.DailyReminderRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 次日提醒继承：取最近一次（日期最新，并列取 taskId 大者）DAILY 行的提醒设置。
 * 语义是「继承上次设置，清除也是设置」：最近一行的 remindAtMinutes 为 null 时返回 null
 * （不往更早的历史行找旧时间），无历史行同样为 null。
 */
internal fun inheritedReminderMinutes(history: List<DailyReminderRow>): Int? =
    history.maxWithOrNull(compareBy({ it.date }, { it.taskId }))?.remindAtMinutes

/**
 * 撤销打卡后是否补排单任务提醒：设过提醒且触发时刻仍在未来才补；
 * 一次性待办的截止日已过（触发时刻在过去）不补。
 */
internal fun shouldRescheduleReminder(
    remindAtMinutes: Int?,
    nowMillis: Long,
    zone: ZoneId,
    date: LocalDate? = null,
): Boolean =
    remindAtMinutes != null && nextTriggerMillis(nowMillis, remindAtMinutes, zone, date) > nowMillis

/**
 * 单任务提醒的触发时刻计算。提成纯函数是为了能脱离 Android 环境单测。
 *
 * 不指定日期：目标时间晚于当前 → 今天触发；已经过点 → 顺延到明天同一时刻。
 * 指定日期（一次性待办的截止日）：定在该日期的目标时刻触发；
 * 那一刻已经过去就原样返回（过去的时刻），work 会立即执行、由 worker 自查决定是否跳过。
 *
 * @param targetMinutesOfDay 一天内的分钟数（0-1439），如 8:30 是 510
 * @param date 指定触发日期；null = 每日任务语义（今天/明天）
 */
internal fun nextTriggerMillis(
    nowMillis: Long,
    targetMinutesOfDay: Int,
    zone: ZoneId,
    date: LocalDate? = null,
): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    var target = now
        .withHour(targetMinutesOfDay / 60)
        .withMinute(targetMinutesOfDay % 60)
        .withSecond(0)
        .withNano(0)
    if (date != null) return target.with(date).toInstant().toEpochMilli()
    if (!target.isAfter(now)) target = target.plusDays(1)
    return target.toInstant().toEpochMilli()
}
