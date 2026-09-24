package com.betterlife.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    val apiModel: String = "",
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val reminderEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    val themeMode: String = SettingsStore.DEFAULT_THEME_MODE,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val API_MODEL = stringPreferencesKey("api_model")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            apiBaseUrl = p[Keys.API_BASE_URL] ?: "",
            apiKey = p[Keys.API_KEY] ?: "",
            apiModel = p[Keys.API_MODEL] ?: "",
            reminderHour = p[Keys.REMINDER_HOUR] ?: 8,
            reminderMinute = p[Keys.REMINDER_MINUTE] ?: 0,
            reminderEnabled = p[Keys.REMINDER_ENABLED] ?: false,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            themeMode = p[Keys.THEME_MODE] ?: DEFAULT_THEME_MODE,
        )
    }

    suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun setApiConfig(baseUrl: String, apiKey: String, model: String) {
        context.dataStore.edit {
            it[Keys.API_BASE_URL] = baseUrl.trim()
            it[Keys.API_KEY] = apiKey.trim()
            it[Keys.API_MODEL] = model.trim()
        }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.REMINDER_ENABLED] = enabled
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setThemeMode(modeKey: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = modeKey }
    }

    companion object {
        const val DEFAULT_THEME_MODE = "brand_green"
    }
}
