// 预测性返回:把系统返回手势的进度变成缩放 + 淡出,让「返回」在手指下就看得见。
//
// 手势完成时调用 onBack();中途取消则原样复位。返回手势的进度是个 Flow,
// 所以这里必须自己收:收完(正常结束)= 手势完成,异常(取消)= 复位。
//
// 截图测试覆盖不了这里 —— 那是一段手势中的中间态,不是静态终态。
// 验收只能靠真机手动走查(见 docs/DESIGN_SYSTEM.md §8 P3-B3)。
package com.betterlife.app.ui.common

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** 手势走到底时整体缩到 92%,和系统「退到上一层」的方向感一致 */
private const val MAX_SCALE_SHRINK = 0.08f

/** 同时淡到 70%:缩放和淡出一起给,单靠缩放在小屏上不够明显 */
private const val MAX_ALPHA_FADE = 0.3f

/**
 * 给当前屏挂上预测性返回。[onBack] 在手势完成时被调用一次。
 *
 * 用法:`Scaffold(modifier = predictiveBackTransition(onBack)) { ... }`
 */
@Composable
internal fun predictiveBackTransition(onBack: () -> Unit): Modifier {
    var progress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = true) { events ->
        try {
            events.collect { event -> progress = event.progress }
            // 流正常结束 = 用户松手走完手势,这时才真的返回
            onBack()
        } finally {
            progress = 0f
        }
    }

    return Modifier.graphicsLayer {
        val scale = 1f - MAX_SCALE_SHRINK * progress
        scaleX = scale
        scaleY = scale
        alpha = 1f - MAX_ALPHA_FADE * progress
    }
}
