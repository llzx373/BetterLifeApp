package com.betterlife.app.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.stats.StatsTaskRow
import com.betterlife.app.stats.computeStats
import com.betterlife.app.stats.decideWeeklyReport
import com.betterlife.app.stats.nextWeeklyReportMillis
import com.betterlife.app.stats.periodReport
import com.betterlife.app.stats.weeklyReportText
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * N4 每周日晚周报：独立周期 work（7 天 + 6h flex，锚定到周日 20:07，见
 * [nextWeeklyReportMillis]），不复用 DailyReminderWorker——周报的触发时刻固定为
 * 周日晚，不应跟随用户的每日提醒时间。
 *
 * 开关只做「发不发」的门控（doWork 里读设置），不增删 work：KEEP 语义下周期锚点稳定，
 * 打开 App 不会把本周报重置到下周。
 */
class WeeklyReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BetterLifeApp).container
        val settings = container.settingsStore.current()
        val today = LocalDate.now()
        // 廉价的门控先走，非周日/开关关闭时不读库；完整决策（含 0 打卡、同周频控）在纯函数里
        if (!settings.weeklyReportEnabled || today.dayOfWeek != DayOfWeek.SUNDAY) {
            return Result.success()
        }

        val rows = container.database.taskDao().allFlow().first().map {
            StatsTaskRow(entryId = it.entryId, type = it.type, date = it.date, done = it.done, note = it.note)
        }
        // 本周 = 周一~触发日（周日）；环比基线是上一周。口径与统计页 periodReport 一致
        val (weekStart, weekEnd) = weekRange(today)
        val (prevStart, prevEnd) = weekRange(today.minusWeeks(1))
        val doneThisWeek = periodReport(rows, emptyMap(), weekStart, weekEnd).totalDone
        val donePrevWeek = periodReport(rows, emptyMap(), prevStart, prevEnd).totalDone

        val leaveMap = container.database.streakLeaveDao().allFlow().first()
            .groupBy({ it.entryId }, { it.date })
            .mapValues { (_, v) -> v.toSet() }
        val currentStreak = computeStats(rows, emptyMap(), leaveMap, today)
            .dailyStreaks.maxOfOrNull { it.current } ?: 0

        val sentWeek = container.settingsStore.weeklyReportSentWeek()
        if (decideWeeklyReport(today.toString(), doneThisWeek, settings.weeklyReportEnabled, sentWeek)) {
            container.reminderScheduler.notifyWeeklyReport(
                weeklyReportText(doneThisWeek, donePrevWeek, currentStreak)
            )
            container.settingsStore.setWeeklyReportSentWeek(weekStart.toString())
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "weekly_report"

        /** 应用启动时注册周期周报；KEEP：已排队的（锚定本周日 20:07）不被 App 重启重置 */
        fun enqueuePeriodic(context: Context) {
            val now = System.currentTimeMillis()
            val initialDelayMs = nextWeeklyReportMillis(now, ZoneId.systemDefault()) - now
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS, 6, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
