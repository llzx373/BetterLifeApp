package com.betterlife.app.tasks

import java.time.LocalDate

/** 连续达标多少周算「已养成」并给出提示 */
const val GRADUATION_CONSECUTIVE_WEEKS = 4

/**
 * 一个每周习惯连续达标（周打卡数 >= timesPerWeek）的周数。
 * 从本周往前数；本周还没达标不算断签（本周仍在进行中），从上周开始计。
 * 某周一条打卡都没有时链条中断。只依赖打卡日期，与习惯何时创建无关——
 * 创建前的周天然是零打卡，链条自然到头。
 */
fun consecutiveReachedWeeks(
    checkinDates: List<String>,
    timesPerWeek: Int,
    today: LocalDate,
): Int {
    if (timesPerWeek <= 0) return 0
    val byWeek = checkinDates
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .groupingBy { weekRange(it).first }
        .eachCount()
    var weekStart = weekRange(today).first
    var reached = 0
    // 本周没达标不算断签（本周仍在进行中），只是不计入连续数
    if ((byWeek[weekStart] ?: 0) >= timesPerWeek) reached++
    weekStart = weekStart.minusWeeks(1)
    while ((byWeek[weekStart] ?: 0) >= timesPerWeek) {
        reached++
        weekStart = weekStart.minusWeeks(1)
    }
    return reached
}
