package com.betterlife.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 口径色的无障碍验收（DESIGN_SYSTEM §3.2 的三条硬约束）。
 *
 * 这一项文档里写的是「上线前跑一次模拟」。**一次性的肉眼判断守不住** —— 改个色值就
 * 可能悄悄退回去，所以把对比度与氘代（红绿色盲）模拟写成单测，让它在每次构建里跑。
 *
 * 两条判据：
 * 1. 文字用途的对比度 ≥ 4.5:1（WCAG AA），对 surface 与四档 surfaceContainer 都要成立；
 * 2. 氘代模拟后，任意两个口径色之间的 ΔE76 ≥ 15 —— 也就是「不能糊成一团」。
 */
class LensColorAccessibilityTest {

    private val lightSurfaces = listOf(
        "surface" to LightColors.surface,
        "surfaceContainerLow" to LightColors.surfaceContainerLow,
        "surfaceContainerHigh" to LightColors.surfaceContainerHigh,
        "surfaceContainerHighest" to LightColors.surfaceContainerHighest,
    )

    private val darkSurfaces = listOf(
        "surface" to DarkColors.surface,
        "surfaceContainerLow" to DarkColors.surfaceContainerLow,
        "surfaceContainerHigh" to DarkColors.surfaceContainerHigh,
        "surfaceContainerHighest" to DarkColors.surfaceContainerHighest,
    )

    private fun lensPairs(colors: LensColors) = listOf(
        "保命" to colors.life,
        "守钱" to colors.money,
        "省精力" to colors.time,
        "别踩线" to colors.line,
    )

    @Test
    fun lightLensColorsMeetTextContrast() {
        assertContrast(lensPairs(lightLensColors), lightSurfaces, "浅色")
    }

    @Test
    fun darkLensColorsMeetTextContrast() {
        assertContrast(lensPairs(darkLensColors), darkSurfaces, "深色")
    }

    private fun assertContrast(
        pairs: List<Pair<String, Color>>,
        surfaces: List<Pair<String, Color>>,
        schemeName: String,
    ) {
        val offenders = mutableListOf<String>()
        pairs.forEach { (name, color) ->
            surfaces.forEach { (surfaceName, surface) ->
                val ratio = contrastRatio(color, surface)
                if (ratio < TEXT_CONTRAST_MIN) {
                    offenders += "$schemeName/$name on $surfaceName = ${"%.2f".format(ratio)}"
                }
            }
        }
        assertTrue(
            "口径色作为文字用时对比度不足 ${TEXT_CONTRAST_MIN}:1：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun lensColorsStayApartUnderDeuteranopia() {
        assertDeuteranopiaSeparation(lightLensColors, "浅色")
        assertDeuteranopiaSeparation(darkLensColors, "深色")
    }

    private fun assertDeuteranopiaSeparation(colors: LensColors, schemeName: String) {
        val pairs = lensPairs(colors)
        val offenders = mutableListOf<String>()
        for (i in pairs.indices) {
            for (j in i + 1 until pairs.size) {
                val (nameA, colorA) = pairs[i]
                val (nameB, colorB) = pairs[j]
                val delta = deltaE76(
                    toLab(simulateDeuteranopia(colorA)),
                    toLab(simulateDeuteranopia(colorB)),
                )
                if (delta < DEUTERANOPIA_MIN_DELTA_E) {
                    offenders += "$schemeName/$nameA vs $nameB ΔE=${"%.1f".format(delta)}"
                }
            }
        }
        assertTrue(
            "氘代模拟下口径色区分度不足（ΔE76 < $DEUTERANOPIA_MIN_DELTA_E）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /** 模拟器自身要可信：灰色在氘代下仍是灰色，否则后面的判据都站不住 */
    @Test
    fun deuteranopiaSimulationKeepsGrayNeutral() {
        val gray = Color(0xFF808080)
        val simulated = simulateDeuteranopia(gray)
        val channels = listOf(simulated.red, simulated.green, simulated.blue)
        assertTrue(
            "灰色经过氘代模拟后应当是中性色，实际 ${channels.map { "%.3f".format(it) }}",
            channels.all { abs(it - channels[0]) < 0.01f },
        )
    }

    private companion object {
        /** WCAG AA 对正文的门槛 */
        const val TEXT_CONTRAST_MIN = 4.5

        /** ΔE76 ≥ 15 表示「明显可区分」（JND 约 2.3，15 已在其数倍之上） */
        const val DEUTERANOPIA_MIN_DELTA_E = 15.0
    }
}

// ---- 色彩计算：纯函数，无 Android 依赖 ----

/** WCAG 相对亮度 */
private fun relativeLuminance(color: Color): Double {
    fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** WCAG 对比度，值域 1..21 */
private fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun toLinear(v: Float): Double {
    val c = v.toDouble()
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun toSrgb(v: Double): Double {
    val c = v.coerceIn(0.0, 1.0)
    return if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
}

/**
 * Machado 等（2009）氘代（deuteranopia）严重度 1.0 的模拟矩阵，作用在**线性 RGB** 上。
 * 这是该领域的通行取值，比早期只换色相的简化公式更接近真实视觉。
 */
private fun simulateDeuteranopia(color: Color): Color {
    val r = toLinear(color.red)
    val g = toLinear(color.green)
    val b = toLinear(color.blue)
    val nr = 0.367322 * r + 0.860646 * g - 0.227968 * b
    val ng = 0.280085 * r + 0.672501 * g + 0.047413 * b
    val nb = -0.011820 * r + 0.042940 * g + 0.968881 * b
    return Color(
        red = toSrgb(nr).toFloat(),
        green = toSrgb(ng).toFloat(),
        blue = toSrgb(nb).toFloat(),
    )
}

private data class Lab(val l: Double, val a: Double, val b: Double)

/** sRGB → CIE L*a*b*（D65） */
private fun toLab(color: Color): Lab {
    val r = toLinear(color.red)
    val g = toLinear(color.green)
    val b = toLinear(color.blue)

    val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b

    fun f(t: Double): Double =
        if (t > 0.008856) cbrt(t) else (7.787 * t) + (16.0 / 116.0)

    val fx = f(x / 0.95047)
    val fy = f(y / 1.0)
    val fz = f(z / 1.08883)
    return Lab(l = 116.0 * fy - 16.0, a = 500.0 * (fx - fy), b = 200.0 * (fy - fz))
}

/** CIE76 色差 */
private fun deltaE76(a: Lab, b: Lab): Double =
    sqrt((a.l - b.l).pow(2) + (a.a - b.a).pow(2) + (a.b - b.b).pow(2))
