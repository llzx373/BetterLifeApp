package com.betterlife.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * N7 长辈模式的放大系数：字号与间距必须按同一比例放大,且行高等比跟上
 * （只放大字号不放大行高,中文会在行内挤成一团）。
 *
 * 写成单测是因为回归路径很隐蔽：改 Type.kt/Spacing.kt 的任何一处数值,
 * 肉眼很难发现两档比例已经脱钩。
 */
class SeniorScaleTest {

    @Test
    fun seniorTypographyScalesEveryDefinedSlot() {
        assertEquals(
            AppTypography.headlineSmall.fontSize * SENIOR_UI_SCALE,
            SeniorTypography.headlineSmall.fontSize,
        )
        assertEquals(
            AppTypography.titleMedium.fontSize * SENIOR_UI_SCALE,
            SeniorTypography.titleMedium.fontSize,
        )
        assertEquals(
            AppTypography.titleSmall.fontSize * SENIOR_UI_SCALE,
            SeniorTypography.titleSmall.fontSize,
        )
        assertEquals(
            AppTypography.bodyMedium.fontSize * SENIOR_UI_SCALE,
            SeniorTypography.bodyMedium.fontSize,
        )
        assertEquals(
            AppTypography.bodySmall.fontSize * SENIOR_UI_SCALE,
            SeniorTypography.bodySmall.fontSize,
        )
        assertEquals(
            AppTypography.labelSmall.fontSize * SENIOR_UI_SCALE,
            SeniorTypography.labelSmall.fontSize,
        )
    }

    @Test
    fun seniorTypographyKeepsLineHeightRatio() {
        assertEquals(
            AppTypography.bodyMedium.lineHeight * SENIOR_UI_SCALE,
            SeniorTypography.bodyMedium.lineHeight,
        )
        assertEquals(
            AppTypography.headlineSmall.lineHeight * SENIOR_UI_SCALE,
            SeniorTypography.headlineSmall.lineHeight,
        )
    }

    @Test
    fun seniorTypographyScalesInheritedSlotsToo() {
        // bodyLarge 没有显式覆盖（继承 M3 默认）,长辈模式也必须放大它
        assertEquals(
            AppTypography.bodyLarge.fontSize * SENIOR_UI_SCALE,
            SeniorTypography.bodyLarge.fontSize,
        )
    }

    @Test
    fun seniorSpacingScalesWholeLadder() {
        assertEquals(Spacing.space1 * SENIOR_UI_SCALE, SeniorSpacing.space1)
        assertEquals(Spacing.space4 * SENIOR_UI_SCALE, SeniorSpacing.space4)
        assertEquals(Spacing.space12 * SENIOR_UI_SCALE, SeniorSpacing.space12)
    }
}
