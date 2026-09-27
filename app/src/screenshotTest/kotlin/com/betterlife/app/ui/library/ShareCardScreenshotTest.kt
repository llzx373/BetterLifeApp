// 分享卡片（C4）的组件级截图基线。
//
// 卡片本体 ShareCardContent 同时被离屏渲染（生成 PNG 分享）与这里的预览复用，
// 基线盯住的是「发出去的那张图」长什么样：应用名 + 口径徽标、标题、说人话、
// 收益、CostMeter、出处。
package com.betterlife.app.ui.library

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.ui.FourFoldPreview
import com.betterlife.app.ui.PreviewSurface
import com.betterlife.app.ui.fakeEntry

@PreviewTest
@FourFoldPreview
@Composable
fun ShareCardTypical() {
    PreviewSurface {
        ShareCardContent(
            fakeEntry(
                id = "02-01",
                sec = 2,
                title = "把家里的食盐换成低钠盐",
                money = EntryKeys.COST_LESS,
                time = EntryKeys.COST_LESS,
                will = EntryKeys.WILL_SOME,
                level = EntryKeys.GAIN_BIG,
                lens = EntryKeys.LENS_MORTALITY,
                ratio = EntryKeys.RATIO_VERY_HIGH,
                human = "味道几乎一样，只是钠少了一截",
                gain = "血压更稳，中风风险更低",
                src = "《活好：从 600 个小改变开始》",
            ),
        )
    }
}

/** 长标题 + 无「说人话」字段的条目：分享卡不能破版，可选段落整段缺席 */
@PreviewTest
@FourFoldPreview
@Composable
fun ShareCardLongTitleNoHuman() {
    PreviewSurface {
        ShareCardContent(
            fakeEntry(
                id = "21-07",
                sec = 21,
                title = "给家里老人的卫生间装上扶手和防滑垫，门槛能拆就拆",
                money = EntryKeys.COST_MID,
                time = EntryKeys.COST_MID,
                will = EntryKeys.WILL_YES,
                level = EntryKeys.GAIN_MID,
                lens = EntryKeys.LENS_FREEDOM,
                ratio = EntryKeys.RATIO_HIGH,
                grade = "B",
                gain = "跌倒风险大幅下降，老人在家更敢动",
            ),
        )
    }
}
