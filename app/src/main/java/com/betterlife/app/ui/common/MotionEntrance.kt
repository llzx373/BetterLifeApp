// 入场容器:统一走这里,而不是各处直接写 AnimatedVisibility。
//
// 理由有两个:
// 1. 关闭档必须能**真正跳过动画**,而不只是把时长调到 0 —— 静态截图拍到 alpha=0 的
//    中间态就是一片空白,那种基线比没有更糟(见 docs/DESIGN_SYSTEM.md §8 P3-A1)。
// 2. 减弱档要能整档抽掉位移与缩放,只留淡入;散在各处的 AnimatedVisibility 做不到这点。
package com.betterlife.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.betterlife.app.ui.theme.LocalMotionLevel
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.motionEffectsSpec
import com.betterlife.app.ui.theme.motionSpatialSpec

/**
 * 淡入;标准档额外叠一次缩放。[scaleFrom] 传 null 表示只要淡入。
 */
@Composable
private fun entrance(scaleFrom: Float): EnterTransition =
    fadeIn(animationSpec = motionEffectsSpec()) +
        scaleIn(initialScale = scaleFrom, animationSpec = motionSpatialSpec())

@Composable
internal fun MotionEntrance(
    visible: Boolean,
    modifier: Modifier = Modifier,
    scaleFrom: Float? = null,
    content: @Composable () -> Unit,
) {
    if (LocalMotionLevel.current == MotionLevel.OFF) {
        if (visible) Box(modifier) { content() }
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (scaleFrom == null) {
            fadeIn(animationSpec = motionEffectsSpec())
        } else {
            entrance(scaleFrom)
        },
        exit = fadeOut(animationSpec = motionEffectsSpec()),
    ) {
        content()
    }
}

/**
 * 首次组合时播放入场动画的变体 —— 用 [MutableTransitionState] 而不是 `visible: Boolean`
 * 才能在首帧就播,`AnimatedVisibility(visible = true)` 不会。
 */
@Composable
internal fun MotionEntrance(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    scaleFrom: Float? = null,
    content: @Composable () -> Unit,
) {
    if (LocalMotionLevel.current == MotionLevel.OFF) {
        if (visibleState.targetState) Box(modifier) { content() }
        return
    }
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = if (scaleFrom == null) {
            fadeIn(animationSpec = motionEffectsSpec())
        } else {
            entrance(scaleFrom)
        },
        exit = fadeOut(animationSpec = motionEffectsSpec()),
    ) {
        content()
    }
}
