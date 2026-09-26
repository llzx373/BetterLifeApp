// 已屏蔽建议页的屏级截图基线。
//
// 这屏是「不再推荐」唯一的恢复入口：列表态要看出每行有「恢复」按钮,
// 空态要教会用户怎么屏蔽(长按推荐条目 / 详情页菜单)。
package com.betterlife.app.ui.dismissed

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.ui.FourFoldScreenPreview
import com.betterlife.app.ui.PreviewScreen
import com.betterlife.app.ui.fakeEntry
import com.betterlife.app.viewmodel.DismissedViewModel

private val creditCardDebt = fakeEntry(
    id = "06-04",
    sec = 6,
    title = "把信用卡的分期还清",
    money = EntryKeys.COST_MID,
    time = EntryKeys.COST_MID,
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
    lens = EntryKeys.LENS_FREEDOM,
    ratio = EntryKeys.RATIO_HIGH,
    grade = "B",
)

@PreviewTest
@FourFoldScreenPreview
@Composable
fun DismissedList() {
    PreviewScreen {
        DismissedContent(
            state = DismissedViewModel.UiState(
                entries = listOf(creditCardDebt, socialSecurityQuery),
                loaded = true,
            ),
            onBack = {},
            onRestore = {},
            onOpenEntry = {},
        )
    }
}

@PreviewTest
@FourFoldScreenPreview
@Composable
fun DismissedEmpty() {
    PreviewScreen {
        DismissedContent(
            state = DismissedViewModel.UiState(loaded = true),
            onBack = {},
            onRestore = {},
            onOpenEntry = {},
        )
    }
}
