// 周期报告的四个可选区间：本周 / 上周 / 本月 / 上月
package com.betterlife.app.stats

import com.betterlife.app.tasks.weekRange
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class StatsPeriod {
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    ;

    /** 该区间的起止日期（闭区间），相对 [today] 求值 */
    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        THIS_WEEK -> weekRange(today)
        LAST_WEEK -> weekRange(today.minusWeeks(1))
        THIS_MONTH -> today.with(TemporalAdjusters.firstDayOfMonth()) to
            today.with(TemporalAdjusters.lastDayOfMonth())
        LAST_MONTH -> {
            val lastMonth = today.minusMonths(1)
            lastMonth.with(TemporalAdjusters.firstDayOfMonth()) to
                lastMonth.with(TemporalAdjusters.lastDayOfMonth())
        }
    }
}
