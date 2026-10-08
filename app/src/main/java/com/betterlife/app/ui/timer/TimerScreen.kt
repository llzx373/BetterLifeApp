// 番茄钟计时页：给「站起来活动 5 分钟」这类需要一段专注时间的任务用。
//
// 三态：Setup（选时长）→ Running（圆环倒计时，可暂停/放弃）→ Finished（自动打卡）。
// 计时正确性不在这层：剩余时间永远按「结束时刻 - 单调时钟」算（见 TimerSession，
// 默认 SystemClock.elapsedRealtime，墙钟被回调不跳变），切后台息屏回来自动校准；
// 这层只负责每秒拿一次最新值来画。
package com.betterlife.app.ui.timer

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.tasks.formatRemainingMillis
import com.betterlife.app.ui.common.MotionEntrance
import com.betterlife.app.ui.theme.LocalMotionLevel
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.motionEffectsSpec
import com.betterlife.app.viewmodel.TimerViewModel

private val RingSize = 240.dp
private val RingStroke = 12.dp
private val FinishIconSize = 72.dp
private const val FINISH_VIBRATE_MS = 400L

internal val TIMER_PRESETS_MINUTES = listOf(5, 10, 15, 20)
internal const val DEFAULT_TIMER_MINUTES = 10

@Composable
fun TimerScreen(
    taskId: Long,
    onBack: () -> Unit,
    vm: TimerViewModel = viewModel(factory = TimerViewModel.factory(taskId)),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val ringtoneUri by vm.ringtoneUri.collectAsStateWithLifecycle()

    // 提示音与振动只挂在真实入口：截图预览渲染的是 TimerContent，不会响
    val context = LocalContext.current
    LaunchedEffect(state) {
        if (state is TimerViewModel.UiState.Finished) playFinishFeedback(context, ringtoneUri)
    }

    TimerContent(
        state = state,
        onBack = onBack,
        onStart = vm::start,
        onTogglePause = vm::togglePause,
        onGiveUp = vm::giveUp,
    )
}

/** 无状态内容：截图测试直接喂假状态渲染它，不需要 ViewModel / Room / 铃声。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimerContent(
    state: TimerViewModel.UiState,
    onBack: () -> Unit,
    onStart: (Long) -> Unit,
    onTogglePause: () -> Unit,
    onGiveUp: () -> Unit,
) {
    // 计时期间保持亮屏；离开计时态或退出页面即恢复
    val view = LocalView.current
    DisposableEffect(state is TimerViewModel.UiState.Running) {
        view.keepScreenOn = state is TimerViewModel.UiState.Running
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        // 关闭档不套 AnimatedContent:静态帧否则可能抓到相位切换的中间态
        if (LocalMotionLevel.current == MotionLevel.OFF) {
            TimerStateContent(state, padding, onBack, onStart, onTogglePause, onGiveUp)
            return@Scaffold
        }
        // transitionSpec 不是 Composable 上下文,spec 必须先取出来
        val effects = motionEffectsSpec<Float>()
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                // 只淡入淡出,不位移:计时屏数字跳动已经够热闹
                fadeIn(animationSpec = effects) togetherWith fadeOut(animationSpec = effects)
            },
            // 每秒刷新的倒计时不算相位切换,只有相位变了才播切换动画
            contentKey = { it.phase },
            label = "timerState",
        ) { target ->
            TimerStateContent(target, padding, onBack, onStart, onTogglePause, onGiveUp)
        }
    }
}

/** 相位:同一相位内的数据刷新(每秒倒计时)不该触发 AnimatedContent,切换动画按相位而不是 state 判 */
private val TimerViewModel.UiState.phase: Int
    get() = when (this) {
        TimerViewModel.UiState.Loading -> 0
        is TimerViewModel.UiState.Setup -> 1
        is TimerViewModel.UiState.Running -> 2
        is TimerViewModel.UiState.Finished -> 3
    }

/** 三态内容:Setup → Running → Finished。抽出来是为了关闭档能绕过 AnimatedContent 直接渲染 */
@Composable
private fun TimerStateContent(
    state: TimerViewModel.UiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onStart: (Long) -> Unit,
    onTogglePause: () -> Unit,
    onGiveUp: () -> Unit,
) {
    when (state) {
        TimerViewModel.UiState.Loading -> Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is TimerViewModel.UiState.Setup -> SetupContent(
            title = state.title,
            onStart = onStart,
            modifier = Modifier.fillMaxSize().padding(padding),
        )

        is TimerViewModel.UiState.Running -> RunningContent(
            state = state,
            onTogglePause = onTogglePause,
            onGiveUp = onGiveUp,
            onBack = onBack,
            modifier = Modifier.fillMaxSize().padding(padding),
        )

        is TimerViewModel.UiState.Finished -> FinishedContent(
            title = state.title,
            onBack = onBack,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
private fun SetupContent(title: String, onStart: (Long) -> Unit, modifier: Modifier = Modifier) {
    var selectedMinutes by rememberSaveable { mutableIntStateOf(DEFAULT_TIMER_MINUTES) }
    Column(
        modifier = modifier.padding(Spacing.space4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.space6))
        }
        Text(
            text = stringResource(R.string.timer_duration_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.space3))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TIMER_PRESETS_MINUTES.forEachIndexed { index, minutes ->
                SegmentedButton(
                    selected = minutes == selectedMinutes,
                    onClick = { selectedMinutes = minutes },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TIMER_PRESETS_MINUTES.size),
                ) {
                    Text(stringResource(R.string.timer_duration_minutes, minutes))
                }
            }
        }
        Spacer(Modifier.height(Spacing.space8))
        Button(onClick = { onStart(selectedMinutes * 60_000L) }) {
            Text(stringResource(R.string.timer_start))
        }
    }
}

@Composable
private fun RunningContent(
    state: TimerViewModel.UiState.Running,
    onTogglePause: () -> Unit,
    onGiveUp: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmAbandon by remember { mutableStateOf(false) }
    val timeText = formatRemainingMillis(state.remainingMillis)
    val ringDesc = stringResource(R.string.timer_remaining_desc, timeText)
    val progress = if (state.durationMillis <= 0L) 1f
    else 1f - state.remainingMillis.toFloat() / state.durationMillis

    Column(
        modifier = modifier.padding(Spacing.space4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.title.isNotBlank()) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.space6))
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = ringDesc },
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(RingSize),
                strokeWidth = RingStroke,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            // 等宽数字：倒计时跳动时文本宽度不抖
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
        Spacer(Modifier.height(Spacing.space8))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space3)) {
            FilledTonalButton(onClick = onTogglePause) {
                Text(stringResource(if (state.paused) R.string.timer_resume else R.string.timer_pause))
            }
            TextButton(onClick = { confirmAbandon = true }) {
                Text(stringResource(R.string.timer_abandon))
            }
        }
    }

    if (confirmAbandon) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text(stringResource(R.string.timer_abandon_dialog_title)) },
            text = { Text(stringResource(R.string.timer_abandon_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAbandon = false
                    onGiveUp()
                    onBack()
                }) { Text(stringResource(R.string.timer_abandon)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAbandon = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** 完成态：一句话 + 返回。打卡已在进这态之前由 ViewModel 完成 */
@Composable
private fun FinishedContent(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // 完成态是全屏情绪最高点:MutableTransitionState 重载才能首帧就播;
    // 关闭档由 MotionEntrance 内部直接静态输出
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    MotionEntrance(visibleState = visible, modifier = modifier, scaleFrom = 0.96f) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(FinishIconSize),
            )
            Spacer(Modifier.height(Spacing.space4))
            Text(stringResource(R.string.timer_finished), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = stringResource(R.string.timer_finished_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (title.isNotBlank()) {
                Spacer(Modifier.height(Spacing.space2))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.space6))
            Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }
    }
}

private fun playFinishFeedback(context: Context, ringtoneUri: String) {
    runCatching {
        val uri = if (ringtoneUri.isBlank()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            Uri.parse(ringtoneUri)
        }
        RingtoneManager.getRingtone(context, uri)?.play()
    }
    runCatching {
        context.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(FINISH_VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
