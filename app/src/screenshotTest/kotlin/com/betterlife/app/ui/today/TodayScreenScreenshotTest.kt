// 今日页的屏级截图基线。
//
// 这是全 App 视觉层级最要紧的一屏：问候区（纯文字）/ 今日任务（唯一用 Card）/
// 为你推荐（分段列表）三级必须一眼分得开，完成态必须比未完成态更安静。
package com.betterlife.app.ui.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.data.health.StepsState
import com.betterlife.app.ui.FourFoldScreenPreview
import com.betterlife.app.ui.PreviewScreen
import com.betterlife.app.ui.fakeEntry
import com.betterlife.app.ui.fakeScored
import com.betterlife.app.ui.fakeTask
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.LibraryViewModel
import com.betterlife.app.viewmodel.TodayViewModel
import java.time.LocalDateTime

/**
 * 固定时刻。今日页头部要显示问候语和日期，跟着真实时钟跑的话
 * 基线每小时（问候语）和每天（日期）都会自己失效 —— 这条预览生成过一次就会失败。
 */
private val FIXED_NOW: LocalDateTime = LocalDateTime.of(2026, 9, 24, 20, 30)

private val lowSodiumSalt = fakeEntry(
    id = "02-01",
    sec = 2,
    title = "把家里的食盐换成低钠盐",
    money = EntryKeys.COST_LESS,
    time = EntryKeys.COST_LESS,
    will = EntryKeys.WILL_SOME,
    level = EntryKeys.GAIN_BIG,
    lens = EntryKeys.LENS_MORTALITY,
    ratio = EntryKeys.RATIO_VERY_HIGH,
)

private val walkAfterDinner = fakeEntry(
    id = "11-03",
    sec = 11,
    title = "晚饭后走 20 分钟",
    money = "0",
    time = EntryKeys.COST_LESS,
    will = EntryKeys.WILL_SOME,
    level = EntryKeys.GAIN_BIG,
    lens = EntryKeys.LENS_MORTALITY,
    ratio = EntryKeys.RATIO_VERY_HIGH,
)

private val fixedSleepWindow = fakeEntry(
    id = "13-02",
    sec = 13,
    title = "固定起床时间，周末也一样",
    money = "0",
    time = EntryKeys.COST_LESS,
    will = EntryKeys.WILL_YES,
    level = EntryKeys.GAIN_MID,
    lens = EntryKeys.LENS_TIME,
    ratio = EntryKeys.RATIO_HIGH,
)

private val creditCardDebt = fakeEntry(
    id = "06-04",
    sec = 6,
    title = "把信用卡的分期还清",
    money = EntryKeys.COST_MID,
    time = EntryKeys.COST_MID,
    will = EntryKeys.WILL_SOME,
    level = EntryKeys.GAIN_MID,
    lens = EntryKeys.LENS_MONEY,
    ratio = EntryKeys.RATIO_HIGH,
    grade = "B",
)

private val socialSecurityQuery = fakeEntry(
    id = "08-12",
    sec = 8,
    title = "先查清社保断缴的影响再决定",
    money = "0",
    time = EntryKeys.COST_MID,
    will = EntryKeys.WILL_SOME,
    level = EntryKeys.GAIN_MID,
    lens = EntryKeys.LENS_FREEDOM,
    ratio = EntryKeys.RATIO_HIGH,
    grade = "B",
)

private fun todayTasks(allDone: Boolean) = listOf(
    TodayViewModel.TaskItem(fakeTask(1, lowSodiumSalt.id, done = allDone), lowSodiumSalt, streak = 9),
    TodayViewModel.TaskItem(
        fakeTask(2, walkAfterDinner.id, done = allDone),
        walkAfterDinner,
        streak = 4,
    ),
    TodayViewModel.TaskItem(
        fakeTask(3, fixedSleepWindow.id, done = allDone),
        fixedSleepWindow,
        streak = 4,
    ),
)

private val libraryState = LibraryViewModel.UiState(
    recommended = linkedMapOf(
        EntryKeys.LENS_MORTALITY to listOf(fakeScored(walkAfterDinner, 140)),
        EntryKeys.LENS_MONEY to listOf(fakeScored(creditCardDebt, 110)),
        EntryKeys.LENS_FREEDOM to listOf(fakeScored(socialSecurityQuery, 90)),
    ),
)

@PreviewTest
@FourFoldScreenPreview
@Composable
fun TodayReady() {
    PreviewScreen {
        TodayContent(
            state = TodayViewModel.UiState.Ready(todayTasks(allDone = false)),
            libraryState = libraryState,
            // Unavailable 不渲染卡片：保持这张基线只盯三级层级，步数卡有自己的组件级基线
            stepsState = StepsState.Unavailable,
            now = FIXED_NOW,
            onCheckIn = { _, _ -> },
            onUndo = {},
            onDrop = {},
            onTakeLeave = {},
            onCancelLeave = {},
            onBackfill = {},
            onOpenEntry = {},
            onAddTodo = {},
            onDismissEntry = {},
            onMarkDoneBefore = {},
            onAuthorizeSteps = {},
            onStartTimer = {},
            onOpenLibrary = {},
            onEditProfile = {},
            contentPadding = PaddingValues(Spacing.space4),
        )
    }
}

/**
 * 全部完成是「安静的庆祝」：卡片收起，只留一句话，不弹窗不放彩带。
 *
 * 这张基线在 B5 之前是**画不出来**的 —— 那张卡靠入场动画出现，静态帧抓到的是
 * alpha=0，一片空白。B5 给了「关闭动效」这条路径，预览走 OFF 才拍得到它。
 */
@PreviewTest
@FourFoldScreenPreview
@Composable
fun TodayAllDone() {
    PreviewScreen {
        TodayContent(
            state = TodayViewModel.UiState.Ready(todayTasks(allDone = true)),
            libraryState = libraryState,
            stepsState = StepsState.Unavailable,
            now = FIXED_NOW,
            onCheckIn = { _, _ -> },
            onUndo = {},
            onDrop = {},
            onTakeLeave = {},
            onCancelLeave = {},
            onBackfill = {},
            onOpenEntry = {},
            onAddTodo = {},
            onDismissEntry = {},
            onMarkDoneBefore = {},
            onAuthorizeSteps = {},
            onStartTimer = {},
            onOpenLibrary = {},
            onEditProfile = {},
            contentPadding = PaddingValues(Spacing.space4),
        )
    }
}

/**
 * 加载中的骨架屏:占位结构必须和 Ready 布局对得上（问候两行、一张卡、三条行）,
 * 否则加载→就绪的瞬间整页会跳。
 */
@PreviewTest
@FourFoldScreenPreview
@Composable
fun TodayLoadingSkeleton() {
    PreviewScreen {
        TodaySkeleton()
    }
}
