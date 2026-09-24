// 形状回到 M3 尺度，并让形状承担层级：主卡片 16 / 次级 12 / 列表 4，而不是所有东西一样圆。
package com.betterlife.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // 列表行、徽标
    small = RoundedCornerShape(8.dp), // 输入框、小按钮
    medium = RoundedCornerShape(12.dp), // 次级卡片
    large = RoundedCornerShape(16.dp), // 主卡片（今日任务）
    extraLarge = RoundedCornerShape(28.dp), // 底部弹层、FloatingToolbar
)
