// 本地版 collectAsStateWithLifecycle:gradle 未引入 lifecycle-runtime-compose,用 repeatOnLifecycle 实现同义 API
package com.betterlife.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * 与 androidx.lifecycle.compose.collectAsStateWithLifecycle 同义:
 * 生命周期达到 minActiveState 时收集,退到后台自动停止。
 * 后续若 gradle 补上 lifecycle-runtime-compose 依赖,可整体替换为官方实现。
 */
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): State<T> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(initialValue = value, this, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
            this@collectAsStateWithLifecycle.collect { value = it }
        }
    }
}
