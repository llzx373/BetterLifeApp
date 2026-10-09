package com.betterlife.app.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
 * N4 每周日晚周报：自续链 one-time work——每次执行末尾按 [nextWeeklyReportMillis]
 * 重锚下一个周日 20:07（unique + REPLACE），App 启动 KEEP 兜底播种，不复用
 * DailyReminderWorker——周报的触发时刻固定为周日晚，不应跟随用户的每日提醒时间。
 *
 * 不用周期 work：它的下一周期从上次实际执行时刻起算，设备在触发窗口关机会导致
 * 锚点逐次滑动，一旦长期落在非周日，dayOfWeek 门控就会让周报永久错过；
 * 一过一算（ZonedDateTime）才始终对准周日 20:07。
 *
 * 开关只做「发不发」的门控（doWork 里读设置），不增删 work。
 */
class WeeklyReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
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
        } finally {
            // 自续链：无论本轮是否发报（开关关/非周日早退/0 打卡周）都重锚下一个周日 20:07。
            // 放在所有副作用之后——REPLACE 会撤掉同名在跑的 work（即本轮自己），排完即返回
            enqueueNext(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "weekly_report"

        /**
         * App 启动兜底播种：已排队/在跑的锚点不动（KEEP——进程可能正是被链上 work
         * 唤醒的，REPLACE 会误撤它）；没有才补。旧版周期 work 升级后首次触发时被
         * doWork 末尾的 REPLACE 自然替换收敛。
         */
        fun enqueue(context: Context) = enqueueOnce(context, ExistingWorkPolicy.KEEP)

        /** 每轮执行末尾自续：重锚下一个周日 20:07（REPLACE，撤掉已跑完/漂移的旧锚点） */
        private fun enqueueNext(context: Context) = enqueueOnce(context, ExistingWorkPolicy.REPLACE)

        private fun enqueueOnce(context: Context, policy: ExistingWorkPolicy) {
            val now = System.currentTimeMillis()
            val delayMs = nextWeeklyReportMillis(now, ZoneId.systemDefault()) - now
            val request = OneTimeWorkRequestBuilder<WeeklyReportWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
