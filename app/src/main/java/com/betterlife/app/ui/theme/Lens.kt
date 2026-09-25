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
import com.betterlife.app.data.EntryKeys

@Immutable
data class LensColors(
    val life: Color, // 先保命（死亡率）
    val money: Color, // 守住钱（金钱）
    val time: Color, // 省精力（时间）
    val line: Color, // 别踩线（自由）
) {
    /** 按 entries.json 的 lens 字段取色；未识别的口径返回 null，由调用方决定降级色 */
    fun forLens(lens: String): Color? = when (lens) {
        EntryKeys.LENS_MORTALITY -> life
        EntryKeys.LENS_MONEY -> money
        EntryKeys.LENS_TIME -> time
        EntryKeys.LENS_FREEDOM -> line
        else -> null
    }
}

// 浅色四个口径色。取值由 LensColorAccessibilityTest 反推得到：把明度拉开到
// 氘代（红绿色盲）模拟下任意两色 ΔE76 ≥ 15，同时对 surface 与四档 surfaceContainer
// 的对比度都 ≥ 4.5:1（文字用途）。
//
// 氘代会把红-绿轴压掉，所以「保命(红) / 守钱(琥珀)」与「省精力(蓝) / 别踩线(紫)」
// 两两都会塌到同一条轴上 —— 只能靠明度区分，这就是它们看起来「两深两中」的原因。
internal val lightLensColors = LensColors(
    life = Color(0xFF5E0F0B), // 保命：最暗。唯一允许用危险的语义，因为它就是危险
    money = Color(0xFF8A5100), // 守钱：暖橙，与保命拉开明度
    time = Color(0xFF1B5FA8), // 省精力：冷静、非情绪化
    line = Color(0xFF33205C), // 别踩线：比省精力更暗的紫，否则氘代下与蓝糊成一团
)

// 深色四个口径色，同样由 LensColorAccessibilityTest 反推。深色底的可用明度区间更窄
// （四档 surfaceContainer 里最亮的那个已经抬到 #2A2F29，对比度 4.5:1 要求 L* 不低于约 61），
// 所以这组的调整幅度比浅色那组小，但同样必须靠明度把蓝/紫分开。
internal val darkLensColors = LensColors(
    life = Color(0xFFFFB4AB),
    money = Color(0xFFE4A94F),
    time = Color(0xFFC6DFFF),
    line = Color(0xFF9C86E8),
)

val LocalLensColors = staticCompositionLocalOf { lightLensColors }

/** 口径图标：与口径色配对，保证不依赖颜色也能区分 */
fun lensIcon(lens: String): ImageVector? = when (lens) {
    EntryKeys.LENS_MORTALITY -> Icons.Filled.Shield
    EntryKeys.LENS_MONEY -> Icons.Filled.CurrencyYuan
    EntryKeys.LENS_TIME -> Icons.Filled.Schedule
    EntryKeys.LENS_FREEDOM -> Icons.Filled.Warning
    else -> null
}
