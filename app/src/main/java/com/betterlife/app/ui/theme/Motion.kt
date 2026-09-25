// 全局动效方案与降级档位。
//
// 三档:标准 / 减弱(只保留 100ms 淡入)/ 关闭。组件从 MaterialTheme.motionScheme 取
// defaultSpatialSpec()/defaultEffectsSpec(),不要手写 tween(300) —— 这样全局一致,
// 且能一处切到 standard() 做对比。
//
// 「关闭」这一档不只是无障碍需求:截图基线拍到的是静态帧,入场动画里的 alpha=0 中间态
// 会被拍成一片空白(见 docs/DESIGN_SYSTEM.md §8 P3-A1)。所以关闭档必须能真正跳过动画,
// 而不只是把时长调短 —— 预览就靠它拿到确定性的图。
package com.betterlife.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** 全局动效方案。 */
internal val AppMotionScheme: MotionScheme = MotionScheme.expressive()

/** 减弱档保留的淡入时长 */
private const val REDUCED_FADE_MILLIS = 100

enum class MotionLevel(val key: String) {
    /** 标准:完整取用 expressive 方案 */
    STANDARD("standard"),

    /** 减弱:只保留 100ms 淡入,不做位移与缩放。前庭功能敏感的用户比想象中多 */
    REDUCED("reduced"),

    /** 关闭:一律瞬时 */
    OFF("off"),
    ;

    companion object {
        fun fromKey(key: String?): MotionLevel =
            entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

val LocalMotionLevel = staticCompositionLocalOf { MotionLevel.STANDARD }

/**
 * 系统「移除动画」与用户档位合成。
 *
 * 系统关了动画就是关了 —— 用户档位只能让它更弱,不能把它覆盖回去。
 * 抽成纯函数是为了能单测:这段逻辑没有 UI,不该只靠肉眼验证。
 */
fun effectiveMotionLevel(userLevel: MotionLevel, systemAnimationsDisabled: Boolean): MotionLevel =
    if (systemAnimationsDisabled) MotionLevel.OFF else userLevel

/** 读系统动画时长缩放。0 表示「移除动画」已在开发者选项/无障碍里打开。 */
fun areSystemAnimationsDisabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}.getOrDefault(false)

/**
 * 淡入淡出类动效的时长。
 *
 * 泛型是为了能直接喂给 `slideInVertically` 这类需要 `FiniteAnimationSpec<IntOffset>` 的 API ——
 * MotionScheme 的 spec 本身就是泛型的,写死成 Float 反而要到处转。
 */
@Composable
fun <T> motionEffectsSpec(): FiniteAnimationSpec<T> = when (LocalMotionLevel.current) {
    MotionLevel.STANDARD -> MaterialTheme.motionScheme.defaultEffectsSpec()
    MotionLevel.REDUCED -> tween(durationMillis = REDUCED_FADE_MILLIS)
    MotionLevel.OFF -> snap()
}

/** 位移/缩放类动效。减弱档起一律瞬时 —— 也就是「只保留淡入」。 */
@Composable
fun <T> motionSpatialSpec(): FiniteAnimationSpec<T> = when (LocalMotionLevel.current) {
    MotionLevel.STANDARD -> MaterialTheme.motionScheme.defaultSpatialSpec()
    MotionLevel.REDUCED, MotionLevel.OFF -> snap()
}
