// 里程碑日签分享卡(N6)的组件级截图基线。
//
// 卡片本体 MilestoneShareCardContent 同时被离屏渲染(生成 PNG 分享)与这里的预览复用,
// 基线盯住的是「发出去的那张图」长什么样:应用名 + 日期、连签天数、一条书摘。
// 日期写死,基线不随真实时钟漂移。
package com.betterlife.app.ui.stats

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.stats.MilestoneShareData
import com.betterlife.app.ui.FourFoldPreview
import com.betterlife.app.ui.PreviewSurface
import java.time.LocalDate

@PreviewTest
@FourFoldPreview
@Composable
fun MilestoneShareCardWithQuote() {
    PreviewSurface {
        MilestoneShareCardContent(
            MilestoneShareData(
                streakDays = 30,
                date = LocalDate.of(2026, 10, 1),
                quoteTitle = "晚饭后走 20 分钟",
                quoteHuman = "不用快,走到微微出汗就够了",
            ),
        )
    }
}

/** 没 DONE 过且种子池为空(极端)时书摘段整段缺席,卡片不能破版 */
@PreviewTest
@FourFoldPreview
@Composable
fun MilestoneShareCardNoQuote() {
    PreviewSurface {
        MilestoneShareCardContent(
            MilestoneShareData(
                streakDays = 7,
                date = LocalDate.of(2026, 10, 1),
                quoteTitle = null,
                quoteHuman = null,
            ),
        )
    }
}
