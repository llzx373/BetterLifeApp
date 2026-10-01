package com.betterlife.app.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.recommend.ProfileQuestions
import com.betterlife.app.recommend.nextDailyContentMillis
import com.betterlife.app.recommend.pickDailyContent
import com.betterlife.app.recommend.prunePushedLines
import com.betterlife.app.recommend.pushedIdsInWindow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * N5 每日一条内容推送：独立周期 work（24h + 1h flex，锚定到每天 08:07，见
 * [nextDailyContentMillis]），不复用 DailyReminderWorker——每日一条的触发时刻固定为
 * 早 8 点档，不应跟随用户的每日提醒时间；且它默认关，开关只门控发不发、不增删 work
 * （KEEP 语义下周期锚点稳定，打开 App 不会把当天已排的推送重置）。
 *
 * 选条全在纯函数 [pickDailyContent]：无可推时当天不发（不硬凑）；发出去才写已推记录
 * （30 天滚动窗口去重）。
 */
class DailyContentWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BetterLifeApp).container
        val settings = container.settingsStore.current()
        // 廉价的门控先走：开关关闭/没看过引导（装完从未打开过的人不该收到内容推送）时不读库
        if (!settings.dailyContentEnabled || !settings.onboardingDone) return Result.success()

        val today = LocalDate.now()
        val pushedLines = container.settingsStore.dailyContentPushedLines()
        val data = container.entryRepository.entriesData()
        // 与 DailyReminderWorker 同一口径：渐进档案未答的字段按未知处理
        val profile = ProfileQuestions.effectiveProfile(
            container.profileRepository.getProfile(),
            container.settingsStore.profileQuestionsAnsweredFlow.first(),
        )
        val pick = pickDailyContent(
            profile = profile,
            entries = data.entries,
            rules = data.rules,
            excludedIds = container.database.entryStateDao().excludedIds().toSet(),
            pushedIds = pushedIdsInWindow(pushedLines, today),
        ) ?: return Result.success() // 无可推时当天不发，不硬凑

        container.reminderScheduler.notifyDailyContent(pick.id, pick.title, pick.human)
        // 发出去才记；写回的是剪过窗口的旧记录 + 本条，滚动窗口随写收缩
        container.settingsStore.setDailyContentPushed(
            prunePushedLines(pushedLines, today) + "${pick.id},$today",
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_content"

        /** 应用启动时注册周期推送；KEEP：已排队的（锚定次日 08:07）不被 App 重启重置 */
        fun enqueuePeriodic(context: Context) {
            val now = System.currentTimeMillis()
            val initialDelayMs = nextDailyContentMillis(now, ZoneId.systemDefault()) - now
            val request = PeriodicWorkRequestBuilder<DailyContentWorker>(24, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
