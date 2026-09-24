// 今日页的屏级截图基线。
//
// 这是全 App 视觉层级最要紧的一屏：问候区（纯文字）/ 今日任务（唯一用 Card）/
// 为你推荐（分段列表）三级必须一眼分得开，完成态必须比未完成态更安静。
package com.betterlife.app.ui.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.ui.FourFoldScreenPreview
import com.betterlife.app.ui.PreviewScreen
import com.betterlife.app.ui.fakeEntry
import com.betterlife.app.ui.fakeScored
import com.betterlife.app.ui.fakeTask
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.LibraryViewModel
import com.betterlife.app.viewmodel.TodayViewModel

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
            onToggle = {},
            onSwap = {},
            onDrop = {},
            onOpenEntry = {},
            onAddTodo = {},
            onOpenLibrary = {},
            onEditProfile = {},
            contentPadding = PaddingValues(Spacing.space4),
        )
    }
}
