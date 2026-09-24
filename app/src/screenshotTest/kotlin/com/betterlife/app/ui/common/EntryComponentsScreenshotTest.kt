// 条目通用组件的截图基线：改这两个组件时，基线会立刻显示视觉有没有变。
//
// 这里的示例数据是中文字面量 —— 截图源集不参与构建产物，也不在
// UiNoChineseLiteralTest 的扫描范围（那条规约只管 main 里的 ui 包）。
package com.betterlife.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.ui.FourFoldPreview
import com.betterlife.app.ui.PreviewSurface
import com.betterlife.app.ui.fakeEntry
import com.betterlife.app.ui.theme.Spacing

@PreviewTest
@FourFoldPreview
@Composable
fun CostMeterRatioVariants() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
            CostMeter(
                fakeEntry(
                    id = "02-01",
                    title = "把家里的食盐换成低钠盐",
                    money = EntryKeys.COST_LESS,
                    time = EntryKeys.COST_LESS,
                    will = EntryKeys.WILL_SOME,
                    level = EntryKeys.GAIN_BIG,
                    ratio = EntryKeys.RATIO_VERY_HIGH,
                ),
            )
            CostMeter(
                fakeEntry(
                    id = "06-04",
                    title = "把信用卡的分期还清",
                    money = EntryKeys.COST_MID,
                    time = EntryKeys.COST_MID,
                    will = EntryKeys.WILL_SOME,
                    level = EntryKeys.GAIN_MID,
                    ratio = EntryKeys.RATIO_HIGH,
                ),
            )
            CostMeter(
                fakeEntry(
                    id = "21-07",
                    title = "重新装修一遍厨房",
                    money = EntryKeys.COST_MORE,
                    time = EntryKeys.COST_MORE,
                    will = EntryKeys.WILL_YES,
                    level = EntryKeys.GAIN_SMALL,
                    ratio = EntryKeys.RATIO_NORMAL,
                ),
            )
        }
    }
}

@PreviewTest
@FourFoldPreview
@Composable
fun EntryBadges() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                RatioBadge(EntryKeys.RATIO_VERY_HIGH)
                RatioBadge(EntryKeys.RATIO_HIGH)
                RatioBadge(EntryKeys.RATIO_NORMAL)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                GradeBadge("A")
                GradeBadge("B")
                GradeBadge("C")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                DisputeBadge()
                TodoBadge()
            }
        }
    }
}
