// 唯一的间距阶梯。新代码禁止再写 padding(16.dp) 这类字面量，一律用 Spacing.spaceN。
package com.betterlife.app.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val space1 = 4.dp // 图标与文字、徽标内边距
    val space2 = 8.dp // 徽标之间
    val space3 = 12.dp // 卡片内边距
    val space4 = 16.dp // 屏幕边距、卡片之间
    val space6 = 24.dp // 区块之间
    val space8 = 32.dp // 大步留白
    val space12 = 48.dp // 空状态、页面底部避让 FAB
}
