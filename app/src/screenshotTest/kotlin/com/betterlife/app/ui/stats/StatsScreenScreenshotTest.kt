// 统计页（B6/B7）的屏级截图基线。
//
// 三个关键态：加载中（骨架就是一只 spinner）、空态（安静的引导，不施压）、
// 有数据（成就含一个新达成、连续天数、近 8 周趋势、周期报告）。
// 状态是无状态的 StatsContent 直接喂假状态，日期全部写死，基线不随真实时钟漂移。
package com.betterlife.app.ui.stats

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.stats.Achievement
import com.betterlife.app.stats.AchievementKeys
import com.betterlife.app.stats.CheckinNote
import com.betterlife.app.stats.DailyStreak
import com.betterlife.app.stats.PeriodReport
import com.betterlife.app.stats.StatsPeriod
import com.betterlife.app.stats.WeeklyCompletion
import com.betterlife.app.ui.FourFoldScreenPreview
import com.betterlife.app.ui.PreviewScreen
import com.betterlife.app.viewmodel.StatsViewModel
import java.time.LocalDate

/** 近 8 周（含本周）的固定周一序列，最后一周有一格无数据（planned=0 → 短横占位） */
private val FIXED_TREND = listOf(
    WeeklyCompletion(LocalDate.of(2026, 8, 3), done = 10, planned = 14),
    WeeklyCompletion(LocalDate.of(2026, 8, 10), done = 12, planned = 14),
    WeeklyCompletion(LocalDate.of(2026, 8, 17), done = 7, planned = 14),
    WeeklyCompletion(LocalDate.of(2026, 8, 24), done = 14, planned = 14),
    WeeklyCompletion(LocalDate.of(2026, 8, 31), done = 0, planned = 0),
    WeeklyCompletion(LocalDate.of(2026, 9, 7), done = 11, planned = 14),
    WeeklyCompletion(LocalDate.of(2026, 9, 14), done = 13, planned = 14),
    WeeklyCompletion(LocalDate.of(2026, 9, 21), done = 9, planned = 12),
)

private val populatedState = StatsViewModel.UiState(
    loaded = true,
    hasAnyActivity = true,
    achievements = listOf(
        Achievement(AchievementKeys.STREAK_7, 12),
        Achievement(AchievementKeys.TOTAL_10, 23),
        Achievement(AchievementKeys.PERFECT_WEEK, 1),
    ),
    // TOTAL_10 是本次打开新达成的：只它带「新」徽标，其余安静陈列
    newAchievementKeys = setOf(AchievementKeys.TOTAL_10),
    streaks = listOf(
        DailyStreak("02-01", "把家里的食盐换成低钠盐", current = 9, max = 12),
        DailyStreak("11-03", "晚饭后走 20 分钟", current = 4, max = 4),
    ),
    bestStreak = 12,
    weeklyTrend = FIXED_TREND,
    selectedPeriod = StatsPeriod.THIS_WEEK,
    report = PeriodReport(
        start = LocalDate.of(2026, 9, 21),
        end = LocalDate.of(2026, 9, 27),
        doneByLens = mapOf(
            EntryKeys.LENS_MORTALITY to 8,
            EntryKeys.LENS_TIME to 4,
            EntryKeys.LENS_MONEY to 2,
        ),
        totalDone = 14,
        doneDaily = 12,
        plannedDaily = 18,
        notes = listOf(
            CheckinNote("2026-09-26", "晚饭后走 20 分钟", "下雨天在楼道里走了走"),
            CheckinNote("2026-09-24", "把家里的食盐换成低钠盐", "爸妈也接受了"),
        ),
    ),
    aiConfigured = true,
)

@PreviewTest
@FourFoldScreenPreview
@Composable
fun StatsLoading() {
    PreviewScreen {
        StatsContent(
            state = StatsViewModel.UiState(),
            onBack = {},
            onOpenSettings = {},
            onSelectPeriod = {},
            onInterpret = {},
        )
    }
}

@PreviewTest
@FourFoldScreenPreview
@Composable
fun StatsEmpty() {
    PreviewScreen {
        StatsContent(
            state = StatsViewModel.UiState(loaded = true, hasAnyActivity = false),
            onBack = {},
            onOpenSettings = {},
            onSelectPeriod = {},
            onInterpret = {},
        )
    }
}

@PreviewTest
@FourFoldScreenPreview
@Composable
fun StatsPopulated() {
    PreviewScreen {
        StatsContent(
            state = populatedState,
            onBack = {},
            onOpenSettings = {},
            onSelectPeriod = {},
            onInterpret = {},
        )
    }
}
