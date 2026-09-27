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
import com.betterlife.app.data.db.AppDatabase
import com.betterlife.app.data.db.ChatMessageDao
import com.betterlife.app.data.health.HealthConnectRepository
import com.betterlife.app.data.health.HealthRulesRepository
import com.betterlife.app.data.health.SensorStepsRepository
import com.betterlife.app.data.health.StepsRepository
import com.betterlife.app.recommend.DailyTaskPlanner
import com.betterlife.app.recommend.RecommendationEngine
import com.betterlife.app.tasks.ReminderScheduler
import com.betterlife.app.tasks.TaskManager

class BetterLifeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 手动依赖注入容器（单例） */
class AppContainer(private val context: Context) {

    val entryRepository: EntryRepository by lazy { EntryRepository(context) }

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    /** 聊天记录表入口，ChatViewModel 持久化用 */
    val chatMessageDao: ChatMessageDao by lazy { database.chatMessageDao() }

    val settingsStore: SettingsStore by lazy { SettingsStore(context) }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(context) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(database.profileDao()) }

    val recommendationEngine: RecommendationEngine by lazy { RecommendationEngine() }

    val dailyTaskPlanner: DailyTaskPlanner by lazy { DailyTaskPlanner(maxDaily = 3) }

    val taskManager: TaskManager by lazy {
        TaskManager(
            database = database,
            taskDao = database.taskDao(),
            entryStateDao = database.entryStateDao(),
            weeklyHabitDao = database.weeklyHabitDao(),
            streakLeaveDao = database.streakLeaveDao(),
            customEntryDao = database.customEntryDao(),
            planner = dailyTaskPlanner,
            entryRepository = entryRepository,
            reminderScheduler = reminderScheduler,
            healthConnect = healthConnectRepository,
            healthRulesRepository = healthRulesRepository,
        )
    }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context) }

    /** 本地 JSON 备份导出/导入（不含 chat_messages 与 DataStore 敏感配置） */
    val backupManager: BackupManager by lazy { BackupManager(database, reminderScheduler) }

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
