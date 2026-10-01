// 应用主题：色/字/形/间距/动效 token 分别定义在 Color/Type/Shape/Spacing/Lens/Motion.kt，这里只做装配。
package com.betterlife.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * N7 长辈模式开关。界面结构分支（今日页简化、我的页补入口）读它；
 * 字号/间距的放大走 typography 参数与 LocalSpacing,不读它。
 * 切换时整树随主题重组,用 static 与 LocalMotionLevel 一致。
 */
val LocalSeniorMode = staticCompositionLocalOf { false }

@Composable
fun BetterLifeTheme(
    themeMode: ThemeMode = ThemeMode.BRAND_BLUE_PURPLE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 动效档位。预览传 OFF,否则入场动画的中间态会被截图拍成空白(见 design 文档 §8 P3-A1)。 */
    motionLevel: MotionLevel = MotionLevel.STANDARD,
    /** N7 长辈模式:字号与间距按 SENIOR_UI_SCALE 整体放大,界面经 LocalSeniorMode 简化 */
    seniorMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = themeMode == ThemeMode.DARK || darkTheme
    val dynamic = themeMode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeMode == ThemeMode.BRAND_GREEN -> if (dark) GreenDarkColors else GreenLightColors
        themeMode == ThemeMode.BRAND_ORANGE -> if (dark) OrangeDarkColors else OrangeLightColors
        themeMode == ThemeMode.BRAND_TEAL -> if (dark) TealDarkColors else TealLightColors
        // 蓝紫(默认)、深色优先、以及低版本系统上 Material You 的兜底
        else -> if (dark) DarkColors else LightColors
    }
    // 口径色不跟随壁纸：Material You 会把四个语义色洗掉，失去区分度（见 DESIGN_SYSTEM §3.1）
    val lensColors = if (dark) darkLensColors else lightLensColors

    CompositionLocalProvider(
        LocalLensColors provides lensColors,
        LocalMotionLevel provides motionLevel,
        LocalSeniorMode provides seniorMode,
        LocalSpacing provides if (seniorMode) SeniorSpacing else Spacing,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = AppMotionScheme,
            shapes = AppShapes,
            typography = if (seniorMode) SeniorTypography else AppTypography,
            content = content,
        )
    }
}
