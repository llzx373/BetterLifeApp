package com.betterlife.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val aiProviders: List<AiProvider> = emptyList(),
    val activeProviderId: String = "",
    /** 聊天页并行提问的供应商 id；空 = 只用「当前使用」的那张 */
    val chatProviderIds: List<String> = emptyList(),
    /** AI 搜索（博查 web-search）的 key 与端点；endpoint 空 = 博查默认端点 */
    val searchApiKey: String = "",
    val searchEndpoint: String = "",
    /** 聊天页来源开关：true = 互联网搜索，false = 本地知识库 */
    val chatWebSearch: Boolean = false,
    val timerRingtoneUri: String = "",
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val reminderEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    val themeMode: String = SettingsStore.DEFAULT_THEME_MODE,
    val motionLevel: String = SettingsStore.DEFAULT_MOTION_LEVEL,
)

/** 设置存取的窄接口：ViewModel 只依赖它，测试里可用内存 fake 替换 */
interface SettingsGateway {
    val settingsFlow: Flow<AppSettings>
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun current(): AppSettings
}

/** 聊天页的设置读写面：比 SettingsGateway 多两个聊天开关 setter */
interface ChatSettingsGateway : SettingsGateway {
    suspend fun setChatProviderIds(ids: List<String>)
    suspend fun setChatWebSearch(enabled: Boolean)
}

class SettingsStore(private val context: Context) : ChatSettingsGateway {

    private object Keys {
        // 旧版单组 AI 配置,只用于一次性迁移,不再对外暴露
        val LEGACY_API_BASE_URL = stringPreferencesKey("api_base_url")
        val LEGACY_API_KEY = stringPreferencesKey("api_key")
        val LEGACY_API_MODEL = stringPreferencesKey("api_model")
        val AI_PROVIDERS_JSON = stringPreferencesKey("ai_providers_json")
        val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
        val CHAT_PROVIDER_IDS = stringPreferencesKey("chat_provider_ids")
        val SEARCH_API_KEY = stringPreferencesKey("search_api_key")
        val SEARCH_ENDPOINT = stringPreferencesKey("search_endpoint")
        val CHAT_WEB_SEARCH = booleanPreferencesKey("chat_web_search")
        val TIMER_RINGTONE_URI = stringPreferencesKey("timer_ringtone_uri")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MOTION_LEVEL = stringPreferencesKey("motion_level")

        // 有序历史:stringSet 不保序,用单条 string 以 \n 分隔存列表(新词在前)
        val SEARCH_HISTORY = stringPreferencesKey("search_history")

        // 已一次性高亮过的成就里程碑 key,逗号分隔;无顺序要求
        val CELEBRATED_MILESTONES = stringPreferencesKey("celebrated_milestones")

        // 条目 id 已从 SS-NN 改写成稳定 key 的一次性标记（ContentBootstrap 用）
        val CONTENT_ID_MIGRATED = booleanPreferencesKey("content_id_migrated")

        // 每日习惯是否已首次播种过示例的一次性标记（TaskManager.ensureTodayTasks 用）
        val DAILY_HABITS_SEEDED = booleanPreferencesKey("daily_habits_seeded")
    }

    private val json = Json { ignoreUnknownKeys = true }

    override val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            aiProviders = readProviders(p),
            activeProviderId = readActiveProviderId(p),
            chatProviderIds = p[Keys.CHAT_PROVIDER_IDS]
                ?.let { raw -> runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull() }
                .orEmpty(),
            searchApiKey = p[Keys.SEARCH_API_KEY] ?: "",
            searchEndpoint = p[Keys.SEARCH_ENDPOINT] ?: "",
            chatWebSearch = p[Keys.CHAT_WEB_SEARCH] ?: false,
            timerRingtoneUri = p[Keys.TIMER_RINGTONE_URI] ?: "",
            reminderHour = p[Keys.REMINDER_HOUR] ?: 8,
            reminderMinute = p[Keys.REMINDER_MINUTE] ?: 0,
            reminderEnabled = p[Keys.REMINDER_ENABLED] ?: false,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            themeMode = p[Keys.THEME_MODE] ?: DEFAULT_THEME_MODE,
            motionLevel = p[Keys.MOTION_LEVEL] ?: DEFAULT_MOTION_LEVEL,
        )
    }

    override suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun upsertProvider(provider: AiProvider) {
        context.dataStore.edit { p ->
            val list = readProviders(p).toMutableList()
            val index = list.indexOfFirst { it.id == provider.id }
            if (index >= 0) list[index] = provider else list += provider
            p[Keys.AI_PROVIDERS_JSON] = json.encodeToString(list)
            // 还没有有效的当前供应商时,新存的这张顶上来
            if (list.none { it.id == readActiveProviderId(p) }) {
                p[Keys.ACTIVE_PROVIDER_ID] = provider.id
            }
        }
    }

    suspend fun deleteProvider(id: String) {
        context.dataStore.edit { p ->
            val list = readProviders(p).filterNot { it.id == id }
            p[Keys.AI_PROVIDERS_JSON] = json.encodeToString(list)
            // 删的是当前那张时回退到剩下的第一张
            if (readActiveProviderId(p) == id) {
                p[Keys.ACTIVE_PROVIDER_ID] = list.firstOrNull()?.id.orEmpty()
            }
        }
    }

    suspend fun setProviderEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { p ->
            val list = readProviders(p).map { if (it.id == id) it.copy(enabled = enabled) else it }
            p[Keys.AI_PROVIDERS_JSON] = json.encodeToString(list)
        }
    }

    suspend fun setActiveProvider(id: String) {
        context.dataStore.edit { p ->
            p[Keys.ACTIVE_PROVIDER_ID] = id
        }
    }

    /** 聊天页并行提问的供应商选择 */
    override suspend fun setChatProviderIds(ids: List<String>) {
        context.dataStore.edit { p ->
            p[Keys.CHAT_PROVIDER_IDS] = json.encodeToString(ids)
        }
    }

    /** AI 搜索（博查）的 key 与端点；endpoint 传空串 = 用默认端点 */
    suspend fun setSearchConfig(apiKey: String, endpoint: String) {
        context.dataStore.edit { p ->
            p[Keys.SEARCH_API_KEY] = apiKey.trim()
            p[Keys.SEARCH_ENDPOINT] = endpoint.trim()
        }
    }

    /** 聊天页来源开关：互联网搜索 / 本地知识库 */
    override suspend fun setChatWebSearch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CHAT_WEB_SEARCH] = enabled }
    }

    /** 空串 = 系统默认通知音 */
    suspend fun setTimerRingtone(uri: String) {
        context.dataStore.edit { it[Keys.TIMER_RINGTONE_URI] = uri }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.REMINDER_ENABLED] = enabled
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setThemeMode(modeKey: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = modeKey }
    }

    suspend fun setMotionLevel(levelKey: String) {
        context.dataStore.edit { it[Keys.MOTION_LEVEL] = levelKey }
    }

    /** 最近搜索,新词在前。不进 AppSettings:它是条目库的局部状态,不该每次设置变化都跟着重组 */
    val searchHistoryFlow: Flow<List<String>> = context.dataStore.data.map { p ->
        p[Keys.SEARCH_HISTORY].orEmpty()
            .split(SEARCH_HISTORY_SEPARATOR)
            .filter { it.isNotBlank() }
    }

    suspend fun addSearchHistory(query: String) {
        context.dataStore.edit { p ->
            val old = p[Keys.SEARCH_HISTORY].orEmpty()
                .split(SEARCH_HISTORY_SEPARATOR)
                .filter { it.isNotBlank() }
            val updated = (listOf(query.trim()) + old.filter { it != query.trim() })
                .take(MAX_SEARCH_HISTORY)
            p[Keys.SEARCH_HISTORY] = updated.joinToString(SEARCH_HISTORY_SEPARATOR)
        }
    }

    /**
     * 已「回顾过」的成就里程碑 key：统计页对新达成的里程碑做一次性高亮后写进这里。
     * 与 searchHistory 同理不进 AppSettings —— 它是统计页的局部状态。
     */
    val celebratedMilestonesFlow: Flow<Set<String>> = context.dataStore.data.map { p ->
        p[Keys.CELEBRATED_MILESTONES].orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()
    }

    suspend fun addCelebratedMilestones(keys: Set<String>) {
        if (keys.isEmpty()) return
        context.dataStore.edit { p ->
            val old = p[Keys.CELEBRATED_MILESTONES].orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
            p[Keys.CELEBRATED_MILESTONES] = (old + keys).joinToString(",")
        }
    }

    /**
     * 条目 id 迁移标记：五张用户表的 entryId 是否已从 SS-NN 改写成稳定 key。
     * 与 searchHistory 同理不进 AppSettings —— 它是首启迁移的局部状态，只需读一次。
     */
    suspend fun isContentIdMigrated(): Boolean =
        context.dataStore.data.first()[Keys.CONTENT_ID_MIGRATED] ?: false

    suspend fun setContentIdMigrated() {
        context.dataStore.edit { it[Keys.CONTENT_ID_MIGRATED] = true }
    }

    /**
     * 每日习惯首次播种标记：用户还没有任何 STATE_DAILY 时是否已播种过示例。
     * 与 contentIdMigrated 同理不进 AppSettings —— 它是一次性播种的局部状态，只需读一次。
     * 无论实际挑到几条都只播一次：用户主动删光习惯后不应再被塞回来。
     */
    suspend fun isDailyHabitsSeeded(): Boolean =
        context.dataStore.data.first()[Keys.DAILY_HABITS_SEEDED] ?: false

    suspend fun setDailyHabitsSeeded() {
        context.dataStore.edit { it[Keys.DAILY_HABITS_SEEDED] = true }
    }

    /**
     * 否则播种 Kimi/DeepSeek 两张预设卡;首次写供应商时迁移结果随之落盘
     */
    private fun readProviders(p: Preferences): List<AiProvider> {
        decodeProviders(p[Keys.AI_PROVIDERS_JSON])?.let { return it }
        val baseUrl = p[Keys.LEGACY_API_BASE_URL].orEmpty()
        val apiKey = p[Keys.LEGACY_API_KEY].orEmpty()
        val model = p[Keys.LEGACY_API_MODEL].orEmpty()
        if (baseUrl.isNotBlank() || apiKey.isNotBlank() || model.isNotBlank()) {
            return listOf(
                AiProvider(
                    id = MIGRATED_PROVIDER_ID,
                    name = MIGRATED_PROVIDER_NAME,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    enabled = true,
                )
            )
        }
        return SEED_PROVIDERS
    }

    private fun readActiveProviderId(p: Preferences): String {
        p[Keys.ACTIVE_PROVIDER_ID]?.let { return it }
        // 迁移路径上 active 还没落盘:旧配置迁移卡 > 种子卡第一张
        return readProviders(p).firstOrNull()?.id.orEmpty()
    }

    private fun decodeProviders(raw: String?): List<AiProvider>? =
        raw?.let { runCatching { json.decodeFromString<List<AiProvider>>(it) }.getOrNull() }

    companion object {
        const val DEFAULT_THEME_MODE = "brand_blue_purple"

        /** 默认「标准」。降级是给需要的用户的选项,不该是所有人的默认 */
        const val DEFAULT_MOTION_LEVEL = "standard"

        const val MAX_SEARCH_HISTORY = 10
        private const val SEARCH_HISTORY_SEPARATOR = "\n"

        private const val MIGRATED_PROVIDER_ID = "migrated"
        private const val MIGRATED_PROVIDER_NAME = "自定义供应商"
    }
}
