package com.betterlife.app.tasks

import java.time.LocalDate

/**
 * 连续打卡天数计算。提成纯函数是为了能脱离 Android 环境单测。
 *
 * 从今天往回走：今天没打卡也没请假时从昨天开始数，避免「今天还没打卡就清零」的误导。
 * 打卡日计数 +1；请假日是「桥」——不断签但也不计数；其余日子断签停止。
 * 边界：今天请假同样算桥（不影响已有连签）；只有请假、没有任何打卡时连签为 0。
 *
 * @param doneDates 已完成日期（yyyy-MM-dd）
 * @param leaveDates 请假日期（yyyy-MM-dd），见 streak_leaves 表
 */
fun computeStreak(doneDates: Set<String>, leaveDates: Set<String>, today: LocalDate): Int {
    var cursor = today
    if (cursor.toString() !in doneDates && cursor.toString() !in leaveDates) {
        cursor = cursor.minusDays(1)
    }
    var count = 0
    while (true) {
        val day = cursor.toString()
        when {
            day in doneDates -> {
                count++
                cursor = cursor.minusDays(1)
            }
            // 请假搭桥：跳过这一天继续往前数，连签不清零也不累加
            day in leaveDates -> cursor = cursor.minusDays(1)
            else -> return count
        }
    }
}
