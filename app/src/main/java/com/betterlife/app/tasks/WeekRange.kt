package com.betterlife.app.tasks

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * [date] 所在周的起止日期（周一 ~ 周日），闭区间。
 * 每周习惯的进度按打卡日落在该区间内的行数统计，跨周天然归零，无需"重置"。
 */
fun weekRange(date: LocalDate): Pair<LocalDate, LocalDate> {
    val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return start to start.plusDays(6)
}
