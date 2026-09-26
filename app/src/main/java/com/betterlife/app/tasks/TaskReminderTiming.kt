package com.betterlife.app.tasks

import java.time.Instant
import java.time.ZoneId

/**
 * 单任务提醒的触发时刻计算。提成纯函数是为了能脱离 Android 环境单测。
 *
 * 目标时间晚于当前 → 今天触发；已经过点 → 顺延到明天同一时刻。
 *
 * @param targetMinutesOfDay 一天内的分钟数（0-1439），如 8:30 是 510
 */
internal fun nextTriggerMillis(nowMillis: Long, targetMinutesOfDay: Int, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    var target = now
        .withHour(targetMinutesOfDay / 60)
        .withMinute(targetMinutesOfDay % 60)
        .withSecond(0)
        .withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return target.toInstant().toEpochMilli()
}
