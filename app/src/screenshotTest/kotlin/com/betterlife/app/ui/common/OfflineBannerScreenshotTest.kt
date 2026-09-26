// 离线横幅的截图基线:拍可见态(动效已在 PreviewSurface 里关掉,是确定性的静态帧)。
// 隐藏态渲染为空,没有截图价值,不建基线。
package com.betterlife.app.ui.common

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.ui.FourFoldPreview
import com.betterlife.app.ui.PreviewSurface

@PreviewTest
@FourFoldPreview
@Composable
fun OfflineBannerVisible() {
    PreviewSurface {
        OfflineBanner(visible = true)
    }
}
