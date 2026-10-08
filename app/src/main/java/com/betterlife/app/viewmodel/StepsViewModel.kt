package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.health.StepsRepository
import com.betterlife.app.data.health.StepsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StepsViewModel(
    private val stepsRepository: StepsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StepsState>(StepsState.Loading)
    val uiState: StateFlow<StepsState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    init {
        refresh()
    }

    /**
     * 重新判定状态并订阅步数流。权限授予/拒绝之后、页面 resume 时调用:
     * 门面每次收集都会重新查 SDK 状态与权限,重新收集就是刷新。
     * 已有结果时保留旧值(stale-while-revalidate),等流的新排放再替换——
     * 重置回 Loading 会让步数卡先消失再插入,回到首页时布局会闪一下。
     *
     * 幂等:已有活跃收集时直接跳过。传感器路径是常驻流,重复 refresh 只会把监听
     * 注销再注册,首开时 init 与 LifecycleResumeEffect 各调一次造成的快速重注册
     * 可能吞掉首帧回投事件。一次性状态(HC 快照/授权/不可用)的流发完即结束,
     * Job 不再活跃,下次 refresh 自然重新收集。
     */
    fun refresh() {
        if (collectJob?.isActive == true) return
        collectJob = viewModelScope.launch(Dispatchers.IO) {
            stepsRepository.stepsFlow().collect { _uiState.value = it }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                StepsViewModel(app.container.stepsRepository)
            }
        }
    }
}
