// 截图测试的公共部分。
//
// DESIGN_SYSTEM §8 的验收要求是「每屏附 4 张截图（浅色/深色 × 100%/200% 字体）」，
// 这里用一个 multi-preview 注解放到一处，避免每个预览函数重复四遍 @Preview。
package com.betterlife.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.betterlife.app.ui.theme.BetterLifeTheme
import com.betterlife.app.ui.theme.Spacing

/**
 * 四种预览组合：浅色/深色 × 100%/200% 字体。
 *
 * 深色靠 `uiMode` 触发，`BetterLifeTheme` 默认跟随 `isSystemInDarkTheme()`，
 * 走的就是真实运行时那条分支，不是另写一套配色。
 *
 * name 用 ASCII：它会进基线文件名，非 ASCII 文件名在 CI 与跨平台上容易出岔子。
 */
@Preview(name = "light-100%")
@Preview(name = "dark-100%", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "light-200%", fontScale = 2f)
@Preview(name = "dark-200%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
internal annotation class FourFoldPreview

/** 预览的统一外壳：套上真实主题，落在 surface 底上，四周留出屏幕边距。 */
@Composable
internal fun PreviewSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BetterLifeTheme {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
            Box(Modifier.padding(Spacing.space4)) { content() }
        }
    }
}
