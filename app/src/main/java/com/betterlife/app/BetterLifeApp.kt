package com.betterlife.app

import android.app.Application
import android.content.Context
import com.betterlife.app.ai.AiAdvisor
import com.betterlife.app.ai.BochaWebSearcher
import com.betterlife.app.ai.EntryRetriever
import com.betterlife.app.ai.LlmClient
import com.betterlife.app.ai.WebSearcher
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.NetworkMonitor
import com.betterlife.app.data.ProfileRepository
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.backup.BackupManager
import com.betterlife.app.data.content.ContentBootstrap
import com.betterlife.app.data.content.ContentSyncRepository
import com.betterlife.app.data.db.AppDatabase
import com.betterlife.app.data.db.ChatMessageDao
import com.betterlife.app.data.health.HealthConnectRepository
import com.betterlife.app.data.health.HealthRulesRepository
import com.betterlife.app.data.health.SensorStepsRepository
import com.betterlife.app.data.health.StepsRepository
import com.betterlife.app.recommend.DailySeedPicker
import com.betterlife.app.recommend.RecommendationEngine
import com.betterlife.app.tasks.ContentSyncWorker
import com.betterlife.app.tasks.DailyContentWorker
import com.betterlife.app.tasks.ReminderScheduler
import com.betterlife.app.tasks.TaskManager
import com.betterlife.app.tasks.WeeklyReportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BetterLifeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 内容库周期同步（每天一次、联网才跑）；KEEP 语义，重复注册不会重置已排队的任务
        ContentSyncWorker.enqueuePeriodic(this)
        // N4：每周日晚周报（自续链，锚定周日 20:07）；KEEP 语义，已排队的锚点不被重置
        WeeklyReportWorker.enqueue(this)
        // N5：每日一条内容推送（自续链，锚定 08:07，默认关——开关只在 doWork 里门控）
        DailyContentWorker.enqueue(this)
        // 每日 tick（汇总/报喜/挽回共用载体）：任一依赖开关开着就确保 work 在——
        // 报喜/挽回默认开，不依赖「每日提醒」开关；读设置需挂起，放 IO 协程
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.reminderScheduler.ensureScheduled()
        }
    }
}

/** 手动依赖注入容器（单例） */
class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val settingsStore: SettingsStore by lazy { SettingsStore(context) }

    /** 内容库首启播种 + 旧 entryId（SS-NN）迁移；依赖 database 与 settingsStore，不成环 */
    val contentBootstrap: ContentBootstrap by lazy { ContentBootstrap(context, database, settingsStore) }

    val entryRepository: EntryRepository by lazy {
        EntryRepository(contentBootstrap, database.contentDao(), context)
    }

    /** 内容库运行时同步（GitHub Releases content-latest）；Worker 与设置页「检查更新」共用 */
    val contentSyncRepository: ContentSyncRepository by lazy {
        ContentSyncRepository(database, entryRepository, contentBootstrap, context)
    }

    /** 聊天记录表入口，ChatViewModel 持久化用 */
    val chatMessageDao: ChatMessageDao by lazy { database.chatMessageDao() }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(context) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(database.profileDao()) }

    val recommendationEngine: RecommendationEngine by lazy { RecommendationEngine() }

    val dailySeedPicker: DailySeedPicker by lazy { DailySeedPicker() }

    val taskManager: TaskManager by lazy {
        TaskManager(
            database = database,
            taskDao = database.taskDao(),
            entryStateDao = database.entryStateDao(),
            weeklyHabitDao = database.weeklyHabitDao(),
            streakLeaveDao = database.streakLeaveDao(),
            customEntryDao = database.customEntryDao(),
            seedPicker = dailySeedPicker,
            entryRepository = entryRepository,
            settingsStore = settingsStore,
            reminderScheduler = reminderScheduler,
            healthConnect = healthConnectRepository,
            healthRulesRepository = healthRulesRepository,
        )
    }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context) }

    /** 本地 JSON 备份导出/导入（不含 chat_messages 与 DataStore 敏感配置） */
    val backupManager: BackupManager by lazy { BackupManager(database, reminderScheduler, context) }

    /** Health Connect 读取仓库：步数门面与 B1 自动核销共用一个实例 */
    val healthConnectRepository: HealthConnectRepository by lazy { HealthConnectRepository(context) }

    /** B1 自动核销规则（assets/health_rules.json，加载一次走内存） */
    val healthRulesRepository: HealthRulesRepository by lazy { HealthRulesRepository(context) }

    /** 今日步数：Health Connect 优先，传感器兜底，门面内部决定走哪条 */
    val stepsRepository: StepsRepository by lazy {
        StepsRepository(healthConnectRepository, SensorStepsRepository(context))
    }

    val llmClient: LlmClient by lazy { LlmClient(settingsStore) }

    val entryRetriever: EntryRetriever by lazy { EntryRetriever() }

    val webSearcher: WebSearcher by lazy { BochaWebSearcher(settingsStore) }

    val aiAdvisor: AiAdvisor by lazy { AiAdvisor(llmClient, entryRetriever, settingsStore, webSearcher) }
}
