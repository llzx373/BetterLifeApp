// 计时页的屏级截图基线：选时长 / 倒计时 / 完成 三态。
//
// Running 态的预览喂固定的 remainingMillis，渲染的是纯静态帧 ——
// 真实倒计时每秒重算，但基线只需要锁住布局与配色，不锁动画。
package com.betterlife.app.ui.timer

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.ui.FourFoldScreenPreview
import com.betterlife.app.ui.PreviewScreen
import com.betterlife.app.viewmodel.TimerViewModel

private const val TITLE = "晚饭后走 20 分钟"

@PreviewTest
@FourFoldScreenPreview
@Composable
fun TimerSetup() {
    PreviewScreen {
        TimerContent(
            state = TimerViewModel.UiState.Setup(TITLE),
            onBack = {},
            onStart = {},
            onTogglePause = {},
            onGiveUp = {},
        )
    }
}

@PreviewTest
@FourFoldScreenPreview
@Composable
fun TimerRunning() {
    PreviewScreen {
        TimerContent(
            state = TimerViewModel.UiState.Running(
                title = TITLE,
                durationMillis = 10 * 60_000L,
                remainingMillis = 455_000L, // 7:35
                paused = false,
            ),
            onBack = {},
            onStart = {},
            onTogglePause = {},
            onGiveUp = {},
        )
    }
}

@PreviewTest
@FourFoldScreenPreview
@Composable
fun TimerFinished() {
    PreviewScreen {
        TimerContent(
            state = TimerViewModel.UiState.Finished(TITLE),
            onBack = {},
            onStart = {},
            onTogglePause = {},
            onGiveUp = {},
        )
    }
}
