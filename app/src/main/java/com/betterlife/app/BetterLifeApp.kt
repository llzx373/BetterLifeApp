package com.betterlife.app

import android.app.Application
import android.content.Context
import com.betterlife.app.ai.AiAdvisor
import com.betterlife.app.ai.EntryRetriever
import com.betterlife.app.ai.LlmClient
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.NetworkMonitor
import com.betterlife.app.data.ProfileRepository
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.AppDatabase
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

    val settingsStore: SettingsStore by lazy { SettingsStore(context) }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(context) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(database.profileDao()) }

    val recommendationEngine: RecommendationEngine by lazy { RecommendationEngine() }

    val dailyTaskPlanner: DailyTaskPlanner by lazy { DailyTaskPlanner(maxDaily = 3) }

    val taskManager: TaskManager by lazy {
        TaskManager(
            taskDao = database.taskDao(),
            entryStateDao = database.entryStateDao(),
            planner = dailyTaskPlanner,
            entryRepository = entryRepository,
            reminderScheduler = reminderScheduler,
        )
    }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context) }

    val llmClient: LlmClient by lazy { LlmClient(settingsStore) }

    val entryRetriever: EntryRetriever by lazy { EntryRetriever() }

    val aiAdvisor: AiAdvisor by lazy { AiAdvisor(llmClient, entryRetriever, settingsStore) }
}
