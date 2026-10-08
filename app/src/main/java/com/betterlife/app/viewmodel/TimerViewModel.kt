package com.betterlife.app.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.SettingsGateway
import com.betterlife.app.tasks.TimerSession
import com.betterlife.app.tasks.TimerTaskGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerViewModel(
    private val taskId: Long,
    private val gateway: TimerTaskGateway,
    settings: SettingsGateway,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    /**
     * 计时页状态。剩余时间每秒重算一次（按单调时钟，不是 tick 计数），
     * 所以切后台再回来不需要任何补偿逻辑。
     */
    sealed interface UiState {
        data object Loading : UiState

        /** 选时长；title 为空串表示任务已被删除，UI 退回通用文案 */
        data class Setup(val title: String) : UiState

        data class Running(
            val title: String,
            val durationMillis: Long,
            val remainingMillis: Long,
            val paused: Boolean,
        ) : UiState

        /** 时间到且已自动打卡 */
        data class Finished(val title: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 计时结束提示音 URI，空串 = 系统默认通知音 */
    val ringtoneUri: StateFlow<String> = settings.settingsFlow
        .map { it.timerRingtoneUri }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private var session: TimerSession? = null
    private var tickJob: Job? = null
    private var completed = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Setup(gateway.taskTitle(taskId).orEmpty())
        }
    }

    fun start(durationMillis: Long) {
        val title = (_uiState.value as? UiState.Setup)?.title ?: return
        val s = TimerSession(durationMillis, clock)
        session = s
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                // 结束判定先于状态发射：到点的那一拍直接进 Finished
                if (s.isFinished) {
                    finish(title)
                    return@launch
                }
                emitRunning(title, s)
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    fun togglePause() {
        val s = session ?: return
        val state = _uiState.value as? UiState.Running ?: return
        if (s.isPaused) s.resume() else s.pause()
        // 立刻刷一帧，不等下一秒 tick，否则暂停按钮看起来没反应
        emitRunning(state.title, s)
    }

    /** 放弃：停表、不打卡；返回上一页由 UI 层做 */
    fun giveUp() {
        tickJob?.cancel()
        session = null
    }

    private fun emitRunning(title: String, s: TimerSession) {
        _uiState.value = UiState.Running(title, s.durationMillis, s.remainingMillis(), s.isPaused)
    }

    /** 时间到：自动打卡（completeTask 内部会取消该任务的提醒），然后进完成态 */
    private suspend fun finish(title: String) {
        if (completed) return
        completed = true
        withContext(Dispatchers.IO) { gateway.completeTask(taskId) }
        _uiState.value = UiState.Finished(title)
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1_000L

        fun factory(taskId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                TimerViewModel(taskId, app.container.taskManager, app.container.settingsStore)
            }
        }
    }
}
