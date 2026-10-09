package com.betterlife.app.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
 * N5 每日一条内容推送：自续链 one-time work——每次执行末尾按 [nextDailyContentMillis]
 * 重锚次日 08:07（unique + REPLACE），App 启动 KEEP 兜底播种，不复用
 * DailyReminderWorker——每日一条的触发时刻固定为早 8 点档，不应跟随用户的每日提醒时间；
 * 且它默认关，开关只门控发不发、不增删 work。
 *
 * 不用周期 work：它的下一周期从上次实际执行时刻起算，触发窗口遇关机/延迟会让
 * 锚点逐次滑离 08:07；一过一算（ZonedDateTime）才始终对准墙钟 08:07。
 *
 * 选条全在纯函数 [pickDailyContent]：无可推时当天不发（不硬凑）；发出去才写已推记录
 * （30 天滚动窗口去重）。
 */
class DailyContentWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
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
        } finally {
            // 自续链：无论本轮是否推送（开关关/无可推）都重锚次日 08:07。
            // 放在所有副作用之后——REPLACE 会撤掉同名在跑的 work（即本轮自己），排完即返回
            enqueueNext(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_content"

        /**
         * App 启动兜底播种：已排队/在跑的锚点不动（KEEP——进程可能正是被链上 work
         * 唤醒的，REPLACE 会误撤它）；没有才补。旧版周期 work 升级后首次触发时被
         * doWork 末尾的 REPLACE 自然替换收敛。
         */
        fun enqueue(context: Context) = enqueueOnce(context, ExistingWorkPolicy.KEEP)

        /** 每轮执行末尾自续：重锚次日 08:07（REPLACE，撤掉已跑完/漂移的旧锚点） */
        private fun enqueueNext(context: Context) = enqueueOnce(context, ExistingWorkPolicy.REPLACE)

        private fun enqueueOnce(context: Context, policy: ExistingWorkPolicy) {
            val now = System.currentTimeMillis()
            val delayMs = nextDailyContentMillis(now, ZoneId.systemDefault()) - now
            val request = OneTimeWorkRequestBuilder<DailyContentWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
