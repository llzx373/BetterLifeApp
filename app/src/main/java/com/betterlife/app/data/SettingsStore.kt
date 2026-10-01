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
    /** N2b：HC 自动核销达标后是否发报喜通知，默认开 */
    val hcPraiseEnabled: Boolean = true,
    /** N2c：3 日未打开时是否发挽回通知，默认开 */
    val reengageEnabled: Boolean = true,
    /** N4：每周日晚是否发周报通知，默认开 */
    val weeklyReportEnabled: Boolean = true,
    /** N5：每天早 8 点档是否推一条内容，默认关（唯一默认关的推送：每日触达翻倍是打扰红线） */
    val dailyContentEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    /** N7：长辈模式（大字号 + 双 tab 极简导航），默认关 */
    val seniorMode: Boolean = false,
    /** N7：onboarding 完成后的长辈模式询问是否已问过（只问一次，跳过也算问过） */
    val seniorModeAsked: Boolean = false,
    val themeMode: String = SettingsStore.DEFAULT_THEME_MODE,
    val motionLevel: String = SettingsStore.DEFAULT_MOTION_LEVEL,
)

/** 设置存取的窄接口：ViewModel 只依赖它，测试里可用内存 fake 替换 */
interface SettingsGateway {
    val settingsFlow: Flow<AppSettings>
    suspend fun setOnboardingDone(done: Boolean)
    /** 完整档案向导保存 = 全部字段已填，每日一问就此终止（N1） */
    suspend fun markAllProfileQuestionsAnswered(fields: Set<String>)
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

        // N2b:HC 自动核销报喜开关(默认开)与当日已发标记(yyyy-MM-dd,空串 = 今天没发过)
        val HC_PRAISE_ENABLED = booleanPreferencesKey("hc_praise_enabled")
        val HC_PRAISE_SENT_DATE = stringPreferencesKey("hc_praise_sent_date")

        // N2c:挽回通知开关(默认开)、最近一次打开 App 的日期、上次发挽回通知的日期(均 yyyy-MM-dd)
        val REENGAGE_ENABLED = booleanPreferencesKey("reengage_enabled")
        val LAST_ACTIVE_DATE = stringPreferencesKey("last_active_date")
        val REENGAGE_SENT_DATE = stringPreferencesKey("reengage_sent_date")

        // N4:周报开关(默认开)与同周频控标记(已发周报所属的周一日期,yyyy-MM-dd,空串 = 从未发过)
        val WEEKLY_REPORT_ENABLED = booleanPreferencesKey("weekly_report_enabled")
        val WEEKLY_REPORT_SENT_WEEK = stringPreferencesKey("weekly_report_sent_week")

        // N5:每日一条开关(默认关)与已推记录(每行 "entryId,yyyy-MM-dd",30 天滚动窗口去重)
        val DAILY_CONTENT_ENABLED = booleanPreferencesKey("daily_content_enabled")
        val DAILY_CONTENT_PUSHED = stringPreferencesKey("daily_content_pushed")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        // N7:长辈模式开关(默认关)与「一次性询问已问过」标记
        val SENIOR_MODE = booleanPreferencesKey("senior_mode")
        val SENIOR_MODE_ASKED = booleanPreferencesKey("senior_mode_asked")
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

        // 推荐「换一批」的轮次:每组候选按此偏移轮转取 topN,持久化避免进程重启后跳回第一批
        val RECOMMEND_OFFSET = intPreferencesKey("recommend_offset")

        // 已提示过「已养成」的每周习惯条目 id,逗号分隔;无顺序要求
        val GRADUATION_PROMPTED = stringPreferencesKey("graduation_prompted")

        // 每日一问（N1 渐进式档案收集）:已答字段 / 「暂不回答」暂缓字段(逗号分隔) /
        // 当日已问日期(yyyy-MM-dd,空串 = 今天还没问) / 老用户一次性迁移标记
        val PROFILE_QUESTIONS_ANSWERED = stringPreferencesKey("profile_questions_answered")
        val PROFILE_QUESTIONS_DEFERRED = stringPreferencesKey("profile_questions_deferred")
        val PROFILE_QUESTION_ASKED_DATE = stringPreferencesKey("profile_question_asked_date")
        val PROFILE_QUESTIONS_MIGRATED = booleanPreferencesKey("profile_questions_migrated")
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
            hcPraiseEnabled = p[Keys.HC_PRAISE_ENABLED] ?: true,
            reengageEnabled = p[Keys.REENGAGE_ENABLED] ?: true,
            weeklyReportEnabled = p[Keys.WEEKLY_REPORT_ENABLED] ?: true,
            dailyContentEnabled = p[Keys.DAILY_CONTENT_ENABLED] ?: false,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            seniorMode = p[Keys.SENIOR_MODE] ?: false,
            seniorModeAsked = p[Keys.SENIOR_MODE_ASKED] ?: false,
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

    /** N2b：「自动打卡报喜」开关 */
    suspend fun setHcPraiseEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HC_PRAISE_ENABLED] = enabled }
    }

    /**
     * N2b 当日已发报喜的日期（yyyy-MM-dd），空串 = 今天没发过。
     * 与 searchHistory 同理不进 AppSettings —— 它是通知频控的局部状态，只需读一次。
     */
    suspend fun hcPraiseSentDate(): String =
        context.dataStore.data.first()[Keys.HC_PRAISE_SENT_DATE].orEmpty()

    suspend fun setHcPraiseSentDate(date: String) {
        context.dataStore.edit { it[Keys.HC_PRAISE_SENT_DATE] = date }
    }

    /** N2c：「久未打开提醒」开关 */
    suspend fun setReengageEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REENGAGE_ENABLED] = enabled }
    }

    /**
     * N2c 最近一次打开 App 的日期（yyyy-MM-dd），空串 = 从未记录过。
     * 与 hcPraiseSentDate 同理不进 AppSettings —— 它是挽回通知频控的局部状态。
     */
    suspend fun lastActiveDate(): String =
        context.dataStore.data.first()[Keys.LAST_ACTIVE_DATE].orEmpty()

    /** MainActivity.onResume 每次写入当天日期；写入即重置挽回通知的计时 */
    suspend fun setLastActiveDate(date: String) {
        context.dataStore.edit { it[Keys.LAST_ACTIVE_DATE] = date }
    }

    /** N2c 上次发挽回通知的日期（yyyy-MM-dd），空串 = 从未发过；7 天频控依据 */
    suspend fun reengageSentDate(): String =
        context.dataStore.data.first()[Keys.REENGAGE_SENT_DATE].orEmpty()

    suspend fun setReengageSentDate(date: String) {
        context.dataStore.edit { it[Keys.REENGAGE_SENT_DATE] = date }
    }

    /** N4：「每周总结」开关；只门控发不发，周期 work 不动（WeeklyReportWorker 里读） */
    suspend fun setWeeklyReportEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WEEKLY_REPORT_ENABLED] = enabled }
    }

    /**
     * N4 已发周报所属的周一日期（yyyy-MM-dd），空串 = 从未发过；同周频控依据。
     * 与 hcPraiseSentDate 同理不进 AppSettings —— 它是通知频控的局部状态，只需读一次。
     */
    suspend fun weeklyReportSentWeek(): String =
        context.dataStore.data.first()[Keys.WEEKLY_REPORT_SENT_WEEK].orEmpty()

    suspend fun setWeeklyReportSentWeek(weekStart: String) {
        context.dataStore.edit { it[Keys.WEEKLY_REPORT_SENT_WEEK] = weekStart }
    }

    /** N5：「每日一条」开关；只门控发不发，周期 work 不动（DailyContentWorker 里读） */
    suspend fun setDailyContentEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DAILY_CONTENT_ENABLED] = enabled }
    }

    /**
     * N5 每日一条的已推记录：每行 "entryId,yyyy-MM-dd"，30 天滚动窗口裁剪是纯函数
     * 做的事（DailyContentWorker 经 prunePushedLines 剪完后整表替换写回）。
     * 与 hcPraiseSentDate 同理不进 AppSettings —— 它是推送去重的局部状态，只需读一次。
     */
    suspend fun dailyContentPushedLines(): List<String> =
        context.dataStore.data.first()[Keys.DAILY_CONTENT_PUSHED].orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }

    suspend fun setDailyContentPushed(lines: List<String>) {
        context.dataStore.edit { it[Keys.DAILY_CONTENT_PUSHED] = lines.joinToString("\n") }
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    /** N7：「长辈模式」开关；settingsFlow 全链路透出,切换即时生效不重启 */
    suspend fun setSeniorMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SENIOR_MODE] = enabled }
    }

    /** N7：长辈模式询问只问一次——无论选「开启」还是「暂不」都记下已问过 */
    suspend fun markSeniorModeAsked() {
        context.dataStore.edit { it[Keys.SENIOR_MODE_ASKED] = true }
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
     * 推荐「换一批」的轮次偏移：LibraryViewModel 的推荐组合随它重算。
     * 与 searchHistory 同理不进 AppSettings —— 它是推荐区的局部状态。
     */
    val recommendOffsetFlow: Flow<Int> = context.dataStore.data.map { p ->
        p[Keys.RECOMMEND_OFFSET] ?: 0
    }

    suspend fun bumpRecommendOffset() {
        context.dataStore.edit { p ->
            p[Keys.RECOMMEND_OFFSET] = (p[Keys.RECOMMEND_OFFSET] ?: 0) + 1
        }
    }

    /**
     * 已提示过「已养成」的每周习惯条目 id：提示是一次性的，点过「知道了」就不再打扰。
     * 与 celebratedMilestones 同理不进 AppSettings。
     */
    val graduationPromptedFlow: Flow<Set<String>> = context.dataStore.data.map { p ->
        p[Keys.GRADUATION_PROMPTED].orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()
    }

    suspend fun addGraduationPrompted(entryId: String) {
        context.dataStore.edit { p ->
            val old = p[Keys.GRADUATION_PROMPTED].orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
            p[Keys.GRADUATION_PROMPTED] = (old + entryId).joinToString(",")
        }
    }

    // ---------- 每日一问（N1 渐进式档案收集） ----------
    // 与 searchHistory 同理不进 AppSettings：是今日页问题卡片的局部状态。

    /** 已答过的档案字段（Profile.fieldValues 的字段名） */
    val profileQuestionsAnsweredFlow: Flow<Set<String>> = context.dataStore.data.map { p ->
        p[Keys.PROFILE_QUESTIONS_ANSWERED].toFieldSet()
    }

    /** 「暂不回答」暂缓的字段：当天不再出现，次日起排在未问过的字段之后 */
    val profileQuestionsDeferredFlow: Flow<Set<String>> = context.dataStore.data.map { p ->
        p[Keys.PROFILE_QUESTIONS_DEFERRED].toFieldSet()
    }

    /** 最近一次问问题的日期（yyyy-MM-dd），空串 = 还没问过；一天最多问一条 */
    val profileQuestionAskedDateFlow: Flow<String> = context.dataStore.data.map { p ->
        p[Keys.PROFILE_QUESTION_ASKED_DATE].orEmpty()
    }

    /** 答完一题：记入已答、移出暂缓、记下当日日期 */
    suspend fun answerProfileQuestion(field: String, date: String) {
        context.dataStore.edit { p ->
            p[Keys.PROFILE_QUESTIONS_ANSWERED] = (p[Keys.PROFILE_QUESTIONS_ANSWERED].toFieldSet() + field)
                .joinToString(",")
            p[Keys.PROFILE_QUESTIONS_DEFERRED] = (p[Keys.PROFILE_QUESTIONS_DEFERRED].toFieldSet() - field)
                .joinToString(",")
            p[Keys.PROFILE_QUESTION_ASKED_DATE] = date
        }
    }

    /** 暂不回答：当天不再出现，次日换下一题（该字段排到队尾） */
    suspend fun deferProfileQuestion(field: String, date: String) {
        context.dataStore.edit { p ->
            p[Keys.PROFILE_QUESTIONS_DEFERRED] = (p[Keys.PROFILE_QUESTIONS_DEFERRED].toFieldSet() + field)
                .joinToString(",")
            p[Keys.PROFILE_QUESTION_ASKED_DATE] = date
        }
    }

    /** 完整档案向导保存（含编辑）= 全部字段已填：问题卡片就此消失 */
    override suspend fun markAllProfileQuestionsAnswered(fields: Set<String>) {
        context.dataStore.edit { p ->
            p[Keys.PROFILE_QUESTIONS_ANSWERED] = fields.joinToString(",")
            p[Keys.PROFILE_QUESTIONS_DEFERRED] = ""
        }
    }

    /**
     * 老用户迁移标记：N1 之前已有档案的用户不补问——首启时若档案存在就把全部字段
     * 标为已答。只需读一次，不进 AppSettings。
     */
    suspend fun isProfileQuestionsMigrated(): Boolean =
        context.dataStore.data.first()[Keys.PROFILE_QUESTIONS_MIGRATED] ?: false

    suspend fun setProfileQuestionsMigrated() {
        context.dataStore.edit { it[Keys.PROFILE_QUESTIONS_MIGRATED] = true }
    }

    private fun String?.toFieldSet(): Set<String> =
        orEmpty().split(",").filter { it.isNotBlank() }.toSet()

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
