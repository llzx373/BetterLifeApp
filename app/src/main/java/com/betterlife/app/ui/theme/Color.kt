// 四个品牌色(蓝紫/青绿/暖橙/青)的 M3 tonal palette 与明/暗两套 ColorScheme。
// 中性色阶(surface 家族、outline、error 等)四个品牌共用同一套 token —— 它们本就接近中性;
// 品牌差异只在 primary/secondary/tertiary 三个家族。新品牌的 tonal 由 seed 按 M3 派生
// (material-color-utilities:primary 取种子调色板,secondary 同 hue 低彩 16,tertiary hue+60 彩 24),
// 六阶文字对全部过 WCAG AA(≥4.5:1);品牌绿沿用历史手工值,原样保留。
// 改色只改这里,业务代码里不要再写 Color(0x...)。
package com.betterlife.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** M3 tonal 调色板上本 App 用到的六阶 */
private class Ramp(
    val t10: Color,
    val t20: Color,
    val t30: Color,
    val t40: Color,
    val t80: Color,
    val t90: Color,
)

/** 一个品牌色的三个家族;暗色 primaryContainer 默认取 30 阶,品牌绿沿用历史手工值 */
private class BrandPalette(
    val primary: Ramp,
    val secondary: Ramp,
    val tertiary: Ramp,
    val primaryContainerDark: Color = primary.t30,
)

// ---------- 品牌:蓝紫(默认;seed #5A58D6,indigo-violet 区间) ----------
private val BluePurplePalette = BrandPalette(
    primary = Ramp(
        t10 = Color(0xFF0C006A), t20 = Color(0xFF1C0D9D), t30 = Color(0xFF3631B2),
        t40 = Color(0xFF4F4DCB), t80 = Color(0xFFC2C1FF), t90 = Color(0xFFE2DFFF),
    ),
    secondary = Ramp(
        t10 = Color(0xFF1A1A2C), t20 = Color(0xFF2F2F42), t30 = Color(0xFF454559),
        t40 = Color(0xFF5D5C71), t80 = Color(0xFFC6C4DD), t90 = Color(0xFFE3E0F9),
    ),
    tertiary = Ramp(
        t10 = Color(0xFF2F1124), t20 = Color(0xFF472639), t30 = Color(0xFF603C50),
        t40 = Color(0xFF795369), t80 = Color(0xFFE9B9D2), t90 = Color(0xFFFFD8EB),
    ),
)

// ---------- 品牌:青绿(seed #2E7D32;历史手工调色,原样保留) ----------
private val GreenPalette = BrandPalette(
    primary = Ramp(
        t10 = Color(0xFF002204), t20 = Color(0xFF00390A), t30 = Color(0xFF005313),
        t40 = Color(0xFF2E7D32), t80 = Color(0xFF95D494), t90 = Color(0xFFB0F1AF),
    ),
    secondary = Ramp(
        t10 = Color(0xFF101F0F), t20 = Color(0xFF243423), t30 = Color(0xFF3A4B38),
        t40 = Color(0xFF52634F), t80 = Color(0xFFB9CCB4), t90 = Color(0xFFD5E8CF),
    ),
    // 青灰(与绿拉开,用于"待核实"等中性强调)
    tertiary = Ramp(
        t10 = Color(0xFF001F23), t20 = Color(0xFF00363C), t30 = Color(0xFF1F4D53),
        t40 = Color(0xFF39656B), t80 = Color(0xFFA1CED5), t90 = Color(0xFFBCEBF1),
    ),
    primaryContainerDark = Color(0xFF145318),
)

// ---------- 品牌:暖橙(seed #C2410C 区间) ----------
private val OrangePalette = BrandPalette(
    primary = Ramp(
        t10 = Color(0xFF390C00), t20 = Color(0xFF5D1800), t30 = Color(0xFF832600),
        t40 = Color(0xFFAC3400), t80 = Color(0xFFFFB59D), t90 = Color(0xFFFFDBD0),
    ),
    secondary = Ramp(
        t10 = Color(0xFF2C160E), t20 = Color(0xFF442A21), t30 = Color(0xFF5D4036),
        t40 = Color(0xFF77574D), t80 = Color(0xFFE7BDB1), t90 = Color(0xFFFFDBD0),
    ),
    tertiary = Ramp(
        t10 = Color(0xFF221B00), t20 = Color(0xFF3A3005), t30 = Color(0xFF51461A),
        t40 = Color(0xFF6A5E2F), t80 = Color(0xFFD7C68D), t90 = Color(0xFFF4E2A7),
    ),
)

// ---------- 品牌:青(seed #0F766E 区间) ----------
private val TealPalette = BrandPalette(
    primary = Ramp(
        t10 = Color(0xFF00201D), t20 = Color(0xFF003733), t30 = Color(0xFF00504A),
        t40 = Color(0xFF006A63), t80 = Color(0xFF80D5CB), t90 = Color(0xFF9CF2E8),
    ),
    secondary = Ramp(
        t10 = Color(0xFF051F1D), t20 = Color(0xFF1C3532), t30 = Color(0xFF324B48),
        t40 = Color(0xFF4A6360), t80 = Color(0xFFB1CCC8), t90 = Color(0xFFCCE8E4),
    ),
    tertiary = Ramp(
        t10 = Color(0xFF001D33), t20 = Color(0xFF17324A), t30 = Color(0xFF2F4961),
        t40 = Color(0xFF47617A), t80 = Color(0xFFAEC9E6), t90 = Color(0xFFCEE5FF),
    ),
)

// ---------- Error(四个品牌共用) ----------
private val Error40 = Color(0xFFBA1A1A)
private val ErrorContainer = Color(0xFFFFDAD6)
private val OnErrorContainer = Color(0xFF410002)
private val Error80 = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)

// ---------- Neutral(带绿调的中性阶,四个品牌共用) ----------
private val Neutral0 = Color(0xFF000000)
private val Neutral4 = Color(0xFF0B0F0A)
private val Neutral10 = Color(0xFF10150F)
private val Neutral12 = Color(0xFF141914)
private val Neutral17 = Color(0xFF1C211B)
private val Neutral20 = Color(0xFF2D322C)
private val Neutral22 = Color(0xFF262B25)
private val Neutral24 = Color(0xFF2A2F29)
private val Neutral87 = Color(0xFFD7DBD3)
private val Neutral90 = Color(0xFFDFE4DB)
private val Neutral92 = Color(0xFFE4EAE1)
private val Neutral94 = Color(0xFFEAEFE7)
private val Neutral95 = Color(0xFFEEF1EA)
private val Neutral96 = Color(0xFFF0F5EE)
private val Neutral98 = Color(0xFFF6FBF1)
private val Neutral100 = Color(0xFFFFFFFF)

// ---------- Neutral Variant(surfaceVariant / outline 家族,四个品牌共用) ----------
private val NeutralVariant30 = Color(0xFF424940)
private val NeutralVariant50 = Color(0xFF72796F)
private val NeutralVariant60 = Color(0xFF8C9388)
private val NeutralVariant80 = Color(0xFFC2C9BE)
private val NeutralVariant90 = Color(0xFFDEE5D9)

private fun brandLightColors(b: BrandPalette) = lightColorScheme(
    primary = b.primary.t40,
    onPrimary = Neutral100,
    primaryContainer = b.primary.t90,
    onPrimaryContainer = b.primary.t10,
    inversePrimary = b.primary.t80,
    secondary = b.secondary.t40,
    onSecondary = Neutral100,
    secondaryContainer = b.secondary.t90,
    onSecondaryContainer = b.secondary.t10,
    tertiary = b.tertiary.t40,
    onTertiary = Neutral100,
    tertiaryContainer = b.tertiary.t90,
    onTertiaryContainer = b.tertiary.t10,
    error = Error40,
    onError = Neutral100,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceTint = b.primary.t40,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    scrim = Neutral0,
    surfaceBright = Neutral98,
    surfaceDim = Neutral87,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
)

private fun brandDarkColors(b: BrandPalette) = darkColorScheme(
    primary = b.primary.t80,
    onPrimary = b.primary.t20,
    primaryContainer = b.primaryContainerDark,
    onPrimaryContainer = b.primary.t90,
    inversePrimary = b.primary.t40,
    secondary = b.secondary.t80,
    onSecondary = b.secondary.t20,
    secondaryContainer = b.secondary.t30,
    onSecondaryContainer = b.secondary.t90,
    tertiary = b.tertiary.t80,
    onTertiary = b.tertiary.t20,
    tertiaryContainer = b.tertiary.t30,
    onTertiaryContainer = b.tertiary.t90,
    error = Error80,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorContainer,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceTint = b.primary.t80,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    scrim = Neutral0,
    surfaceBright = Neutral24,
    surfaceDim = Neutral4,
    surfaceContainerLowest = Neutral4,
    surfaceContainerLow = Neutral12,
    surfaceContainer = Neutral17,
    surfaceContainerHigh = Neutral22,
    surfaceContainerHighest = Neutral24,
)

internal val BluePurpleLightColors = brandLightColors(BluePurplePalette)
internal val BluePurpleDarkColors = brandDarkColors(BluePurplePalette)
internal val GreenLightColors = brandLightColors(GreenPalette)
internal val GreenDarkColors = brandDarkColors(GreenPalette)
internal val OrangeLightColors = brandLightColors(OrangePalette)
internal val OrangeDarkColors = brandDarkColors(OrangePalette)
internal val TealLightColors = brandLightColors(TealPalette)
internal val TealDarkColors = brandDarkColors(TealPalette)

/** 默认品牌(蓝紫)的明/暗 scheme;口径色无障碍单测(LensColorAccessibilityTest)直接引用 */
internal val LightColors = BluePurpleLightColors
internal val DarkColors = BluePurpleDarkColors
