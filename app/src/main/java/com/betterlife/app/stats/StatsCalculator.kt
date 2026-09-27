// 统计纯逻辑：连续天数、近 8 周完成率趋势、周期报告。
// 不依赖 Android / Room —— ViewModel 把 TaskEntity 折成 StatsTaskRow 再喂进来，
// 这样趋势数学、口径分布、周期过滤都能脱离设备单测。
package com.betterlife.app.stats

import com.betterlife.app.tasks.computeStreak
import com.betterlife.app.tasks.weekRange
import java.time.LocalDate

/** 任务类型的机器可读值，与 tasks 表里的 type 字段一致（这里自带一份，保持本包零依赖） */
object StatsRowType {
    const val DAILY = "DAILY"
    const val ONCE = "ONCE"
    const val WEEKLY = "WEEKLY"
}

/** 一行任务的统计视图：只留统计用得上的字段 */
data class StatsTaskRow(
    val entryId: String,
    val type: String,
    val date: String?,     // yyyy-MM-dd，DAILY / WEEKLY 使用
    val done: Boolean,
    val note: String? = null,
)

/** 条目的统计视图：标题 + 口径；自定义条目由调用方补进 map（lens 为空串） */
data class StatsEntry(
    val entryId: String,
    val title: String,
    val lens: String,
)

/** 一个每日习惯的连续天数：current = 当前连签，max = 历史最长 */
data class DailyStreak(
    val entryId: String,
    val title: String,
    val current: Int,
    val max: Int,
)

/** 一个 ISO 周的每日任务完成情况；planned == 0 表示那周还没开始记录（rate 为 null） */
data class WeeklyCompletion(
    val weekStart: LocalDate,
    val done: Int,
    val planned: Int,
) {
    val rate: Float? get() = if (planned == 0) null else done.toFloat() / planned
}

/** 一次带备注的打卡（随手记），周期报告里按日期倒序列出 */
data class CheckinNote(
    val date: String,
    val entryTitle: String,
    val note: String,
)

/**
 * 一段区间 [start, end]（闭区间）的回顾报告。
 * 只统计带日期的行：DAILY（有完成率分母）与 WEEKLY（恒为完成的打卡）；
 * ONCE 任务没有日期维度，不进周期报告。
 */
data class PeriodReport(
    val start: LocalDate,
    val end: LocalDate,
    /** 口径 → 完成次数；无口径（自定义/未知条目）归到空串 key */
    val doneByLens: Map<String, Int>,
    val totalDone: Int,
    val doneDaily: Int,
    val plannedDaily: Int,
    val notes: List<CheckinNote>,
) {
    val completionRate: Float?
        get() = if (plannedDaily == 0) null else doneDaily.toFloat() / plannedDaily
}

/** 全量统计结果：连续天数、趋势、完美周数与累计完成（成就判定的输入也在这里凑齐） */
data class StatsResult(
    val dailyStreaks: List<DailyStreak>,
    val weeklyTrend: List<WeeklyCompletion>,
    /** 已全部完成（100%）且已结束的 ISO 周数，按全部历史算，不限于趋势窗口 */
    val perfectWeeks: Int,
    /** 累计完成次数：所有 done 行（DAILY + WEEKLY + ONCE） */
    val totalCompletions: Int,
) {
    val bestStreak: Int get() = dailyStreaks.maxOfOrNull { maxOf(it.current, it.max) } ?: 0
}

/**
 * 历史最长连签：与 [computeStreak] 同一套请假搭桥语义 ——
 * 两段打卡之间只隔着请假日时视为没断，否则重新计。
 */
fun maxStreak(doneDates: Set<String>, leaveDates: Set<String>): Int {
    if (doneDates.isEmpty()) return 0
    val sorted = doneDates.map(LocalDate::parse).distinct().sorted()
    var best = 1
    var run = 1
    var prev = sorted.first()
    for (day in sorted.drop(1)) {
        val bridged = generateSequence(prev.plusDays(1)) { it.plusDays(1) }
            .takeWhile { it.isBefore(day) }
            .all { it.toString() in leaveDates }
        run = if (bridged) run + 1 else 1
        if (run > best) best = run
        prev = day
    }
    return best
}

/**
 * 全量统计入口。
 *
 * @param leaveDates entryId → 请假日期集合（yyyy-MM-dd）
 * @param trendWeeks 趋势窗口周数，默认近 8 周（含本周）
 */
fun computeStats(
    rows: List<StatsTaskRow>,
    entries: Map<String, StatsEntry>,
    leaveDates: Map<String, Set<String>>,
    today: LocalDate,
    trendWeeks: Int = 8,
): StatsResult {
    val dailyRows = rows.filter { it.type == StatsRowType.DAILY && it.date != null }

    val streaks = dailyRows.groupBy { it.entryId }.map { (entryId, entryRows) ->
        val doneDates = entryRows.filter { it.done }.mapNotNull { it.date }.toHashSet()
        val leaves = leaveDates[entryId].orEmpty()
        DailyStreak(
            entryId = entryId,
            title = entries[entryId]?.title ?: entryId,
            current = computeStreak(doneDates, leaves, today),
            max = maxStreak(doneDates, leaves),
        )
    }.sortedWith(compareByDescending<DailyStreak> { it.current }.thenByDescending { it.max }.thenBy { it.title })

    val currentWeekStart = weekRange(today).first
    val weeklyTrend = (trendWeeks - 1 downTo 0).map { back ->
        val start = currentWeekStart.minusWeeks(back.toLong())
        val end = start.plusDays(6)
        val inWeek = dailyRows.filter { row ->
            val d = LocalDate.parse(row.date)
            !d.isBefore(start) && !d.isAfter(end)
        }
        WeeklyCompletion(weekStart = start, done = inWeek.count { it.done }, planned = inWeek.size)
    }

    // 完美周按全历史算：按周分组后，已结束的周里每一天的每日任务都完成了才算
    val perfectWeeks = dailyRows
        .groupBy { weekRange(LocalDate.parse(it.date)).first }
        .count { (weekStart, weekRows) ->
            weekStart.isBefore(currentWeekStart) && weekRows.isNotEmpty() && weekRows.all { it.done }
        }

    return StatsResult(
        dailyStreaks = streaks,
        weeklyTrend = weeklyTrend,
        perfectWeeks = perfectWeeks,
        totalCompletions = rows.count { it.done },
    )
}

/** 周期报告：[start, end] 闭区间；只统计带日期的 DAILY / WEEKLY 行 */
fun periodReport(
    rows: List<StatsTaskRow>,
    entries: Map<String, StatsEntry>,
    start: LocalDate,
    end: LocalDate,
): PeriodReport {
    val inRange = rows.filter { row ->
        row.date?.let { dateStr ->
            val d = LocalDate.parse(dateStr)
            !d.isBefore(start) && !d.isAfter(end)
        } == true
    }
    val daily = inRange.filter { it.type == StatsRowType.DAILY }
    val weekly = inRange.filter { it.type == StatsRowType.WEEKLY }
    val doneRows = daily.filter { it.done } + weekly

    val doneByLens = doneRows
        .groupingBy { entries[it.entryId]?.lens.orEmpty() }
        .eachCount()

    val notes = doneRows
        .filter { !it.note.isNullOrBlank() }
        .map {
            CheckinNote(
                date = it.date.orEmpty(),
                entryTitle = entries[it.entryId]?.title ?: it.entryId,
                note = it.note.orEmpty().trim(),
            )
        }
        .sortedByDescending { it.date }

    return PeriodReport(
        start = start,
        end = end,
        doneByLens = doneByLens,
        totalDone = doneRows.size,
        doneDaily = daily.count { it.done },
        plannedDaily = daily.size,
        notes = notes,
    )
}
