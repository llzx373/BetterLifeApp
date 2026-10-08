package com.betterlife.app.data.health

import java.time.Instant

/**
 * 运动 session 裁剪到目标窗口后的时长（毫秒）。提成纯函数是为了脱离 Android 环境单测
 * （HealthConnectRepository.readTodayExerciseMinutes 用它累计今日运动分钟）。
 *
 * HC 的 between 过滤命中的是「与窗口有交集」的 session，不裁剪就按全长累计：
 * 昨晚 23:00 到今天 01:00 的两小时运动会给今天计 120 分钟，直接满足 exercise 类
 * 自动核销规则——违背「误核销比不核销更糟糕」的保守原则。所以只计交集部分：
 * min(sessionEnd, windowEnd) - max(sessionStart, windowStart)，无交集为 0。
 */
internal fun exerciseOverlapMillis(
    sessionStart: Instant,
    sessionEnd: Instant,
    windowStart: Instant,
    windowEnd: Instant,
): Long {
    val start = maxOf(sessionStart.toEpochMilli(), windowStart.toEpochMilli())
    val end = minOf(sessionEnd.toEpochMilli(), windowEnd.toEpochMilli())
    return (end - start).coerceAtLeast(0L)
}
