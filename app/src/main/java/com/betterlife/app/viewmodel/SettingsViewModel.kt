package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.ai.ChatMessage
import com.betterlife.app.ai.LlmClient
import com.betterlife.app.data.AppSettings
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.tasks.ReminderScheduler
import com.betterlife.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler,
    private val llmClient: LlmClient,
) : ViewModel() {

    /** 可选的 API 服务商预设，供 UI 渲染快捷按钮 */
    val presets: List<LlmClient.Companion.Preset> = listOf(LlmClient.KIMI, LlmClient.DEEPSEEK)

    /** 连接自检的结果，交给界面翻成文案 */
    sealed interface ConnectionTest {
        data object Idle : ConnectionTest
        data object Running : ConnectionTest
        data object Success : ConnectionTest
        data class Failed(val detail: String) : ConnectionTest
    }

    /**
     * [loaded] 明确区分「DataStore 还没读出来」和「读出来就是空」——
     * 界面据此显示加载态，而不是先渲染空表单再被填上。
     */
    data class UiState(
        val settings: AppSettings = AppSettings(),
        val loaded: Boolean = false,
        val connectionTest: ConnectionTest = ConnectionTest.Idle,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.settingsFlow.collect { s ->
                _uiState.value = _uiState.value.copy(settings = s, loaded = true)
            }
        }
    }

    fun saveApiConfig(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setApiConfig(baseUrl, apiKey, model)
        }
    }

    /** 用已保存的配置真发一次请求，让用户知道设置到底能不能用 */
    fun testConnection() {
        if (_uiState.value.connectionTest is ConnectionTest.Running) return
        _uiState.value = _uiState.value.copy(connectionTest = ConnectionTest.Running)
        viewModelScope.launch {
            val result = llmClient.chat(
                messages = listOf(ChatMessage(ChatMessage.ROLE_USER, PING_PROMPT)),
                model = "",
            )
            _uiState.value = _uiState.value.copy(
                connectionTest = result.fold(
                    onSuccess = { ConnectionTest.Success },
                    onFailure = { ConnectionTest.Failed(it.message.orEmpty()) },
                ),
            )
        }
    }

    /** 套用预设（不覆盖已填的 apiKey） */
    fun applyPreset(preset: LlmClient.Companion.Preset) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = settingsStore.current()
            settingsStore.setApiConfig(preset.baseUrl, current.apiKey, preset.model)
        }
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setReminder(enabled, hour, minute)
            if (enabled) reminderScheduler.enqueue(hour, minute)
            else reminderScheduler.cancel()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setThemeMode(mode.key)
        }
    }

    companion object {
        private const val PING_PROMPT = "ping"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                SettingsViewModel(c.settingsStore, c.reminderScheduler, c.llmClient)
            }
        }
    }
}
