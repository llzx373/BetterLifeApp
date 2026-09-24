// 口径色（Lens Colors）—— 本项目的自定义语义色。
// M3 只有 primary/secondary/tertiary/error 四个角色，不够表达四个平行口径
// （先保命 / 守住钱 / 省精力 / 别踩线），所以在这里自定义一组并用 CompositionLocal 下发。
//
// 三条硬约束（见 docs/DESIGN_SYSTEM.md §3.2）：
// 1. 明度必须拉开，氘代色盲模拟下保命与守钱不能糊成一团；
// 2. 绝不只靠颜色 —— 每个口径必须同时带图标（lensIcon），这是无障碍底线；
// 3. 对比度：文字 ≥ 4.5:1、图形 ≥ 3:1，对 surface 与 surfaceContainer* 两种底都验。
package com.betterlife.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyYuan
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class LensColors(
    val life: Color, // 先保命（死亡率）
    val money: Color, // 守住钱（金钱）
    val time: Color, // 省精力（时间）
    val line: Color, // 别踩线（自由）
) {
    /** 按 entries.json 的 lens 字段取色；未识别的口径返回 null，由调用方决定降级色 */
    fun forLens(lens: String): Color? = when (lens) {
        "死亡率" -> life
        "金钱" -> money
        "时间" -> time
        "自由" -> line
        else -> null
    }
}

internal val lightLensColors = LensColors(
    life = Color(0xFF8C1D18), // 保命：唯一允许用危险的语义，因为它就是危险
    money = Color(0xFF8A5100), // 守钱：暖橙，与红拉开明度
    time = Color(0xFF1B5FA8), // 省精力：冷静、非情绪化
    line = Color(0xFF5B3E9E), // 别踩线：法律/边界的中性色，不恐吓
)

internal val darkLensColors = LensColors(
    life = Color(0xFFFFB4AB),
    money = Color(0xFFF5BD6B),
    time = Color(0xFF9DCAFF),
    line = Color(0xFFC7B6FF),
)

val LocalLensColors = staticCompositionLocalOf { lightLensColors }

/** 口径图标：与口径色配对，保证不依赖颜色也能区分 */
fun lensIcon(lens: String): ImageVector? = when (lens) {
    "死亡率" -> Icons.Filled.Shield
    "金钱" -> Icons.Filled.CurrencyYuan
    "时间" -> Icons.Filled.Schedule
    "自由" -> Icons.Filled.Warning
    else -> null
}
