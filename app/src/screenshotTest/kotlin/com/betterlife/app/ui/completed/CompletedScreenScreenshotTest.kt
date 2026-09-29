// 已完成列表的屏级截图基线。
//
// 这屏是「我做过了」/打卡完成条目的唯一撤销入口：列表态要看出每行有「撤销完成」按钮，
// 空态要教会用户从哪标记（详情页或推荐卡片上的「我做过了」）。
package com.betterlife.app.ui.completed

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.ui.FourFoldScreenPreview
import com.betterlife.app.ui.PreviewScreen
import com.betterlife.app.ui.fakeEntry
import com.betterlife.app.viewmodel.CompletedViewModel

private val flossTeeth = fakeEntry(
    id = "02-03",
    sec = 2,
    title = "每天用牙线清洁牙缝",
    money = EntryKeys.COST_LESS,
    time = EntryKeys.COST_LESS,
    lens = EntryKeys.LENS_MORTALITY,
    ratio = EntryKeys.RATIO_HIGH,
    grade = "A",
)

private val emergencyFund = fakeEntry(
    id = "06-02",
    sec = 6,
    title = "存一笔 3 个月生活费的应急金",
    money = EntryKeys.COST_MID,
    time = EntryKeys.COST_LESS,
    lens = EntryKeys.LENS_MONEY,
    ratio = EntryKeys.RATIO_HIGH,
    grade = "B",
)

@PreviewTest
@FourFoldScreenPreview
@Composable
fun CompletedList() {
    PreviewScreen {
        CompletedContent(
            state = CompletedViewModel.UiState(
                entries = listOf(flossTeeth, emergencyFund),
                loaded = true,
            ),
            onBack = {},
            onUnmark = {},
            onOpenEntry = {},
        )
    }
}

@PreviewTest
@FourFoldScreenPreview
@Composable
fun CompletedEmpty() {
    PreviewScreen {
        CompletedContent(
            state = CompletedViewModel.UiState(loaded = true),
            onBack = {},
            onUnmark = {},
            onOpenEntry = {},
        )
    }
}
