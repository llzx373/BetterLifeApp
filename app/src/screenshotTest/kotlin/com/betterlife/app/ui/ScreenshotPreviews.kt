// 截图测试的公共部分。
//
// DESIGN_SYSTEM §8 的验收要求是「每屏附 4 张截图（浅色/深色 × 100%/200% 字体）」，
// 这里用 multi-preview 注解放到一处，避免每个预览函数重复四遍 @Preview。
//
// name 一律用 ASCII：它会进基线文件名，非 ASCII 文件名在 CI 与跨平台上是隐患。
package com.betterlife.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.betterlife.app.ui.theme.BetterLifeTheme
import com.betterlife.app.ui.theme.Spacing

/** 组件级预览：跟随内容大小，四种组合。 */
@Preview(name = "light-100%")
@Preview(name = "dark-100%", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "light-200%", fontScale = 2f)
@Preview(name = "dark-200%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
internal annotation class FourFoldPreview

/**
 * 整屏预览：固定成一台手机的大小，四种组合。
 *
 * 固定尺寸是必须的 —— 整屏用的都是 `fillMaxSize` + `LazyColumn`，
 * 没有约束时渲染结果没有意义。412×915 是 Pixel 7 的逻辑分辨率。
 */
@Preview(name = "light-100%", widthDp = 412, heightDp = 915)
@Preview(name = "dark-100%", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 915)
@Preview(name = "light-200%", fontScale = 2f, widthDp = 412, heightDp = 915)
@Preview(
    name = "dark-200%",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    fontScale = 2f,
    widthDp = 412,
    heightDp = 915,
)
internal annotation class FourFoldScreenPreview

/**
 * 组件预览的外壳：套上真实主题，落在 surface 底上，四周留出屏幕边距。
 *
 * 深色靠 `uiMode` 触发，`BetterLifeTheme` 默认跟随 `isSystemInDarkTheme()`，
 * 走的就是真实运行时那条分支，不是另写一套配色。
 */
@Composable
internal fun PreviewSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BetterLifeTheme {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
            Box(Modifier.padding(Spacing.space4)) { content() }
        }
    }
}

/** 整屏预览的外壳：真实主题 + 铺满，屏幕边距交给各屏自己的 contentPadding。 */
@Composable
internal fun PreviewScreen(content: @Composable () -> Unit) {
    BetterLifeTheme {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
