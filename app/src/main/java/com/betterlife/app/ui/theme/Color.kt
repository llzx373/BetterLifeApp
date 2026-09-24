// 品牌绿 #2E7D32 的完整 M3 tonal palette 与明/暗两套 ColorScheme。
// 补齐了此前缺失的 surfaceContainer* / surfaceDim / surfaceBright / inverse* / outlineVariant / scrim，
// 这些正是 M3 Expressive 组件（工具栏、分段列表、FloatingToolbar）要用的色槽。
// 改色只改这里，业务代码里不要再写 Color(0x...)。
package com.betterlife.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------- Primary：品牌绿 ----------
private val Green10 = Color(0xFF002204)
private val Green20 = Color(0xFF00390A)
private val Green30 = Color(0xFF005313)
private val Green40 = Color(0xFF2E7D32) // 品牌绿
private val Green80 = Color(0xFF95D494)
private val Green90 = Color(0xFFB0F1AF)
private val GreenContainerDark = Color(0xFF145318)

// ---------- Secondary：低饱和绿灰 ----------
private val Secondary40 = Color(0xFF52634F)
private val SecondaryContainer = Color(0xFFD5E8CF)
private val OnSecondaryContainer = Color(0xFF101F0F)
private val Secondary80 = Color(0xFFB9CCB4)
private val OnSecondaryDark = Color(0xFF243423)
private val SecondaryContainerDark = Color(0xFF3A4B38)

// ---------- Tertiary：青灰（与绿拉开，用于“待核实”等中性强调） ----------
private val Tertiary40 = Color(0xFF39656B)
private val TertiaryContainer = Color(0xFFBCEBF1)
private val OnTertiaryContainer = Color(0xFF001F23)
private val Tertiary80 = Color(0xFFA1CED5)
private val OnTertiaryDark = Color(0xFF00363C)
private val TertiaryContainerDark = Color(0xFF1F4D53)

// ---------- Error ----------
private val Error40 = Color(0xFFBA1A1A)
private val ErrorContainer = Color(0xFFFFDAD6)
private val OnErrorContainer = Color(0xFF410002)
private val Error80 = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)

// ---------- Neutral（带绿调的中性阶） ----------
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

// ---------- Neutral Variant（surfaceVariant / outline 家族） ----------
private val NeutralVariant30 = Color(0xFF424940)
private val NeutralVariant50 = Color(0xFF72796F)
private val NeutralVariant60 = Color(0xFF8C9388)
private val NeutralVariant80 = Color(0xFFC2C9BE)
private val NeutralVariant90 = Color(0xFFDEE5D9)

internal val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Neutral100,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    inversePrimary = Green80,
    secondary = Secondary40,
    onSecondary = Neutral100,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary40,
    onTertiary = Neutral100,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
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
    surfaceTint = Green40,
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

internal val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = Green90,
    inversePrimary = Green40,
    secondary = Secondary80,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = SecondaryContainer,
    tertiary = Tertiary80,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = TertiaryContainer,
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
    surfaceTint = Green80,
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
