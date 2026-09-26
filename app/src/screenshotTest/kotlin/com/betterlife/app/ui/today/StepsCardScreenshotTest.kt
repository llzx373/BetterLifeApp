// 今日步数卡片的组件级截图基线：两种来源的读数态 + 待授权态。
//
// Unavailable 不渲染卡片（调用方直接不画），所以没有它的预览——
// 「什么都没有」没有可回归的图。
package com.betterlife.app.ui.today

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.health.StepsSource
import com.betterlife.app.data.health.StepsState
import com.betterlife.app.ui.FourFoldPreview
import com.betterlife.app.ui.PreviewSurface

@PreviewTest
@FourFoldPreview
@Composable
fun StepsCardHealthConnect() {
    PreviewSurface {
        StepsCard(
            state = StepsState.Available(steps = 8432, source = StepsSource.HEALTH_CONNECT),
            onAuthorize = {},
        )
    }
}

@PreviewTest
@FourFoldPreview
@Composable
fun StepsCardSensor() {
    PreviewSurface {
        StepsCard(
            state = StepsState.Available(steps = 1204, source = StepsSource.SENSOR),
            onAuthorize = {},
        )
    }
}

@PreviewTest
@FourFoldPreview
@Composable
fun StepsCardUnauthorized() {
    PreviewSurface {
        StepsCard(
            state = StepsState.Unauthorized(StepsSource.HEALTH_CONNECT),
            onAuthorize = {},
        )
    }
}
