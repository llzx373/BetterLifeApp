// 应用主题：色/字/形/间距/动效 token 分别定义在 Color/Type/Shape/Spacing/Lens/Motion.kt，这里只做装配。
package com.betterlife.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun BetterLifeTheme(
    themeMode: ThemeMode = ThemeMode.BRAND_GREEN,
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 动效档位。预览传 OFF,否则入场动画的中间态会被截图拍成空白(见 design 文档 §8 P3-A1)。 */
    motionLevel: MotionLevel = MotionLevel.STANDARD,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = themeMode == ThemeMode.DARK || darkTheme
    val dynamic = themeMode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    // 口径色不跟随壁纸：Material You 会把四个语义色洗掉，失去区分度（见 DESIGN_SYSTEM §3.1）
    val lensColors = if (dark) darkLensColors else lightLensColors

    CompositionLocalProvider(
        LocalLensColors provides lensColors,
        LocalMotionLevel provides motionLevel,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = AppMotionScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
