// 唯一的间距阶梯。新代码禁止再写 padding(16.dp) 这类字面量，一律用 Spacing.spaceN。
//
// N7 长辈模式：SpacingValues 带放大系数,主题经 LocalSpacing 下发放大档（与字号同系数）。
// 长辈模式生效路径（今日/聊天/我的等）从 LocalSpacing 取；其余界面继续用顶层 Spacing（默认档），
// 用法与旧 object Spacing 完全一致。
package com.betterlife.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
class SpacingValues internal constructor(factor: Float) {
    val space1 = 4.dp * factor // 图标与文字、徽标内边距
    val space2 = 8.dp * factor // 徽标之间
    val space3 = 12.dp * factor // 卡片内边距
    val space4 = 16.dp * factor // 屏幕边距、卡片之间
    val space6 = 24.dp * factor // 区块之间
    val space8 = 32.dp * factor // 大步留白
    val space12 = 48.dp * factor // 空状态、页面底部避让 FAB
}

/** 默认档间距 */
val Spacing = SpacingValues(1f)

/** N7 长辈模式档：与字号同一个放大系数 */
internal val SeniorSpacing = SpacingValues(SENIOR_UI_SCALE)

/** 主题按长辈模式开关下发放大档；切换档位时整树本就随主题重组,用 static 与 LocalMotionLevel 一致 */
val LocalSpacing = staticCompositionLocalOf { Spacing }
