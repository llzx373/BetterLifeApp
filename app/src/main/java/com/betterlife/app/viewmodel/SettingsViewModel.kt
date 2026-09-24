package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.ai.LlmClient
import com.betterlife.app.data.AppSettings
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.tasks.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    /** 可选的 API 服务商预设，供 UI 渲染快捷按钮 */
    val presets: List<LlmClient.Companion.Preset> = listOf(LlmClient.KIMI, LlmClient.DEEPSEEK)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.settingsFlow.collect { _settings.value = it }
        }
    }

    fun saveApiConfig(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setApiConfig(baseUrl, apiKey, model)
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                SettingsViewModel(c.settingsStore, c.reminderScheduler)
            }
        }
    }
}
