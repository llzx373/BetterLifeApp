// 应用主题：色/字/形/间距 token 分别定义在 Color/Type/Shape/Spacing/Lens.kt，这里只做装配。
// 主题容器在 P1 第 7 项切到 MaterialExpressiveTheme；当前仍是 MaterialTheme。
package com.betterlife.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun BetterLifeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }
    // 口径色不跟随壁纸：Material You 会把四个语义色洗掉，失去区分度（见 DESIGN_SYSTEM §3.1）
    val lensColors = if (darkTheme) darkLensColors else lightLensColors

    CompositionLocalProvider(LocalLensColors provides lensColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
