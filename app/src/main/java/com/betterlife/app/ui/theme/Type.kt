// 中文优先排版：只覆盖 6 个真正用得到的档位，其余继承 M3 默认。
// 核心是行高 —— 中文方块字没有拉丁的 x-height 与升降部，同样 leading 下视觉密度高得多，
// M3 默认的 1.43 会挤。这里统一给到 1.6~1.75。
// 不引入任何拉丁 webfont：中文必须走系统字体（Roboto → Noto Sans CJK 回退链）。
package com.betterlife.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** N7 长辈模式的整体放大系数：字号与间距共用这一个比例 */
internal const val SENIOR_UI_SCALE = 1.25f

internal val AppTypography = Typography(
    // 屏幕标题
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 34.sp),
    // 区块标题
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    // 卡片标题
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    // 正文
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
    // 次要说明
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    // 徽标 / 标签
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
)

/**
 * N7 长辈模式排版：整套档位（含继承 M3 默认的槽位）按 [SENIOR_UI_SCALE] 放大,
 * 行高等比跟上,各档位的相对节奏不变。
 */
internal val SeniorTypography = AppTypography.scaled(SENIOR_UI_SCALE)

private fun TextStyle.scaled(factor: Float): TextStyle = copy(
    fontSize = if (fontSize == TextUnit.Unspecified) fontSize else fontSize * factor,
    lineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else lineHeight * factor,
)

private fun Typography.scaled(factor: Float) = Typography(
    displayLarge = displayLarge.scaled(factor),
    displayMedium = displayMedium.scaled(factor),
    displaySmall = displaySmall.scaled(factor),
    headlineLarge = headlineLarge.scaled(factor),
    headlineMedium = headlineMedium.scaled(factor),
    headlineSmall = headlineSmall.scaled(factor),
    titleLarge = titleLarge.scaled(factor),
    titleMedium = titleMedium.scaled(factor),
    titleSmall = titleSmall.scaled(factor),
    bodyLarge = bodyLarge.scaled(factor),
    bodyMedium = bodyMedium.scaled(factor),
    bodySmall = bodySmall.scaled(factor),
    labelLarge = labelLarge.scaled(factor),
    labelMedium = labelMedium.scaled(factor),
    labelSmall = labelSmall.scaled(factor),
)
