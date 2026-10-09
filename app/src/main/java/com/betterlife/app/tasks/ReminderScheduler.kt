package com.betterlife.app.tasks

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.MainActivity
import com.betterlife.app.R
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.recommend.ProfileQuestions
import com.betterlife.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 每日 tick 调度：一个 WorkManager work 承载三类每日通知——每日汇总（每日提醒开关）、
 * N2b 报喜（hcPraiseEnabled）、N2c 挽回（reengageEnabled）。work 的去留由「任一开关开着」
 * 决定（见 ensureScheduled / cancel），发哪条由 worker 内按各自开关门控：
 * 报喜/挽回默认开，不应随每日提醒开关一起消失。
 *
 * 调度用自续链而非周期 work：每次执行末尾由 worker 按 [nextTriggerMillis] 重排下一个
 * 墙钟锚点（unique + REPLACE）。周期 work 的固定 24h 是真实时间间隔，DST 切换后触发
 * 时刻在墙钟上偏移且不再回正；一过一算（ZonedDateTime）才始终对准 hour:minute。
 */
class ReminderScheduler(private val context: Context) {

    companion object {
        const val WORK_NAME = "daily_reminder"
        const val CHANNEL_ID = "daily"
        const val NOTIFICATION_ID = 1001

        /** N2b 报喜通知的 id，与每日汇总（[NOTIFICATION_ID]）互不覆盖 */
        const val PRAISE_NOTIFICATION_ID = 1002

        /** N2c 挽回通知的 id，与汇总（1001）/报喜（1002）互不覆盖 */
        const val REENGAGE_NOTIFICATION_ID = 1003

        /** N4 周报的 id，与汇总（1001）/报喜（1002）/挽回（1003）互不覆盖 */
        const val WEEKLY_REPORT_NOTIFICATION_ID = 1004

        /** N5 每日一条的 id，与汇总/报喜/挽回/周报互不覆盖 */
        const val DAILY_CONTENT_NOTIFICATION_ID = 1005

        /** 单任务提醒的 unique work 名前缀，按 taskId 一一对应 */
        const val TASK_WORK_PREFIX = "remind_task_"

        /** 单任务通知的 id 基准，与全局汇总通知（[NOTIFICATION_ID]）区分开 */
        const val TASK_NOTIFICATION_ID_BASE = 20000

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_daily_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_channel_daily_desc)
                }
            )
        }
    }

    /**
     * 排下一次每日 tick：锚定最近的 hour:minute 墙钟时刻（已过点顺延明天），REPLACE 重锚。
     * 设置页改时间、worker 每轮执行末尾的自续都走这里。
     */
    fun enqueue(hour: Int, minute: Int) {
        enqueueOnce(hour, minute, ExistingWorkPolicy.REPLACE)
    }

    /**
     * 应用启动时兜底注册：任一依赖开关（每日提醒/报喜/挽回）开着就确保链在
     * （KEEP：已排队/在跑的锚点不动——进程可能正是被链上 work 唤醒的，REPLACE 会误撤它）；
     * 全关则不管（cancel 已撤）。触发时刻用存储的 reminderHour/reminderMinute（默认 8:00）。
     */
    suspend fun ensureScheduled() {
        val settings = SettingsStore(context).current()
        if (settings.reminderEnabled || settings.hcPraiseEnabled || settings.reengageEnabled) {
            enqueueOnce(settings.reminderHour, settings.reminderMinute, ExistingWorkPolicy.KEEP)
        }
    }

    private fun enqueueOnce(hour: Int, minute: Int, policy: ExistingWorkPolicy) {
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val triggerAt = nextTriggerMillis(now, hour * 60 + minute, ZoneId.systemDefault())
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay((triggerAt - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /**
     * 关掉「每日提醒」时调用。work 是汇总/报喜/挽回的共用载体：报喜或挽回还开着就
     * 保留 work（发不发由 worker 按各自开关门控），全关才真正撤掉。
     */
    fun cancel() {
        val keep = runBlocking {
            val settings = SettingsStore(context).current()
            settings.hcPraiseEnabled || settings.reengageEnabled
        }
        if (!keep) WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * N2b 报喜通知：HC 自动核销达标且今日任务全部完成时发一条（证据文案如「今日步数 9234 ≥ 7000」）。
     * 复用 "daily" 渠道——同属每日任务提醒，单开渠道会让用户多管一个开关。
     * 同日重发抑制由调用方（decideAutoNotify + SettingsStore 的当日标记）负责，这里只管发。
     */
    fun notifyAutoCompletePraise(evidenceText: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_praise_title))
            .setContentText(context.getString(R.string.notification_praise_body, evidenceText))
            .setContentIntent(reminderContentIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(PRAISE_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 权限被收回等情况，忽略
        }
    }

    /**
     * N2c 挽回通知：3 日未打开时发一条（触发窗口与 7 天频控由 decideReengageNotify +
     * SettingsStore 的日期标记负责，这里只管发）。复用 "daily" 渠道，点击深链到今日页
     * （「补昨天的卡」入口在今日页，不在待办页）。
     */
    fun notifyReengage() {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_reengage_title))
            .setContentText(context.getString(R.string.notification_reengage_body))
            .setContentIntent(reminderContentIntent(context, MainActivity.ROUTE_TODAY))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(REENGAGE_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 权限被收回等情况，忽略
        }
    }

    /**
     * N4 每周日晚周报：一句话总结本周打卡（文案由 stats/WeeklyReport.kt 的纯函数生成）。
     * 复用 "daily" 渠道，点击深链到统计页；触发时机与 0 打卡/同周频控由
     * WeeklyReportWorker + decideWeeklyReport 负责，这里只管发。
     */
    fun notifyWeeklyReport(text: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_weekly_title))
            .setContentText(text)
            .setContentIntent(reminderContentIntent(context, MainActivity.ROUTE_STATS))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(WEEKLY_REPORT_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 权限被收回等情况，忽略
        }
    }

    /**
     * N5 每日一条内容推送：标题 + 一句「说人话」（选条与 30 天去重由
     * DailyContentWorker + pickDailyContent 负责，这里只管发）。复用 "daily" 渠道，
     * 点击深链条目详情页（"entry/<entryId>"，见 MainActivity.ROUTE_ENTRY_PREFIX）。
     */
    fun notifyDailyContent(entryId: String, title: String, human: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(human)
            .setContentIntent(reminderContentIntent(context, MainActivity.ROUTE_ENTRY_PREFIX + entryId))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(DAILY_CONTENT_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 权限被收回等情况，忽略
        }
    }

    /**
     * 给单条任务排到点精准触发的一次性 work（按 taskId 命名，重排即 REPLACE）。
     * [minutesOfDay] 是一天内的分钟数；[date] 非空（一次性待办的截止日）时定在该日期触发，
     * 否则已过点顺延到明天（见 [nextTriggerMillis]）。
     */
    fun scheduleTaskReminder(taskId: Long, minutesOfDay: Int, date: LocalDate? = null) {
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val triggerAt = nextTriggerMillis(now, minutesOfDay, ZoneId.systemDefault(), date)
        // 截止日的目标时刻可能已经过去，负延迟按 0 处理，work 立即执行、由 worker 自查
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay((triggerAt - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(TaskReminderWorker.KEY_TASK_ID to taskId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(TASK_WORK_PREFIX + taskId, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelTaskReminder(taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(TASK_WORK_PREFIX + taskId)
    }
}

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BetterLifeApp).container
        val settings = container.settingsStore.current()
        val today = LocalDate.now().toString()
        // 本 work 是汇总/报喜/挽回的共用载体，即使每日提醒关着也可能在跑（报喜/挽回还开着）。
        // 任务流水线（播种/核销/部件刷新/汇总/报喜）只在「每日提醒」或「报喜」开着时跑——
        // 只剩挽回开着时不必读 HC、建任务
        if (settings.reminderEnabled || settings.hcPraiseEnabled) {
            // N1：没档案也能规划（空档案走普惠推荐 + 种子池兜底），门槛改为「看过引导」——
            // 装完从未打开过的用户不应被静默播种和提醒
            if (settings.onboardingDone) {
                val profile = container.profileRepository.getProfile()
                val answered = container.settingsStore.profileQuestionsAnsweredFlow.first()
                container.taskManager.ensureTodayTasks(ProfileQuestions.effectiveProfile(profile, answered))
            }
            // B1：先用 Health Connect 数据自动核销达标的每日任务，再统计未完成数发通知；
            // 尽力而为——HC 不可用/没权限/出异常都安静跳过（按无核销处理）
            val completed = runCatching { container.taskManager.autoCompleteByHealth() }
                .getOrDefault(TaskManager.AutoCompleteResult.NONE)
            // B5：新一天任务已生成 / 自动核销已落库，同步刷新桌面小部件
            WidgetUpdater.refresh(applicationContext)
            // N2b：核销结果交给纯函数决策——全部完成只报喜、有未完成合并进汇总、同日重发抑制
            val decision = decideAutoNotify(
                completions = completed.evidences,
                undoneCount = container.taskManager.todayUndoneCount(),
                praiseEnabled = settings.hcPraiseEnabled,
                praiseSentToday = container.settingsStore.hcPraiseSentDate() == today,
            )
            when (decision) {
                AutoNotifyType.PRAISE -> {
                    container.reminderScheduler.notifyAutoCompletePraise(completed.evidenceText)
                    container.settingsStore.setHcPraiseSentDate(today)
                }
                // 每日汇总只认「每日提醒」开关；关着时（work 为报喜/挽回而跑）不发汇总
                AutoNotifyType.SUMMARY -> if (settings.reminderEnabled) {
                    notifyUndone(
                        undone = container.taskManager.todayUndoneCount(),
                        praiseText = completed.evidenceText.takeIf { completed.count > 0 },
                    )
                }
                AutoNotifyType.NONE -> {}
            }
        }
        // N2c：3 日未打开发挽回通知（7 天频控）；用户打开 App 写 last_active_date 即自然重置
        if (decideReengageNotify(
                lastActiveDate = container.settingsStore.lastActiveDate(),
                lastSentDate = container.settingsStore.reengageSentDate(),
                today = today,
                enabled = settings.reengageEnabled,
            )
        ) {
            container.reminderScheduler.notifyReengage()
            container.settingsStore.setReengageSentDate(today)
        }
        // 自续链：按当前设置重排下一天的墙钟锚点（DST 安全），全关则断链。
        // 放在所有副作用之后、return 之前——REPLACE 会撤掉同名在跑的 work（即本轮自己），
        // 排完立即返回，不打断上面的写库/通知
        if (settings.reminderEnabled || settings.hcPraiseEnabled || settings.reengageEnabled) {
            container.reminderScheduler.enqueue(settings.reminderHour, settings.reminderMinute)
        }
        return Result.success()
    }

    /** [praiseText] 非空（本轮有核销且有未完成）时把报喜证据并进汇总文案，一条通知说完 */
    private fun notifyUndone(undone: Int, praiseText: String? = null) {
        if (!hasNotificationPermission(applicationContext)) return

        ReminderScheduler.ensureChannel(applicationContext)
        val text = when {
            undone > 0 && praiseText != null ->
                applicationContext.getString(R.string.notification_daily_undone_with_praise, praiseText, undone)
            undone > 0 ->
                applicationContext.getString(R.string.notification_daily_undone, undone)
            else ->
                applicationContext.getString(R.string.notification_daily_all_done)
        }
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            // 必须是单色白剪影资源；此前用的 android.R.drawable 是框架资源，
            // 在部分 OEM 的通知栏里会渲染成空白方块。
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notification_daily_title))
            .setContentText(text)
            .setContentIntent(reminderContentIntent(applicationContext))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(ReminderScheduler.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 权限被收回等情况，忽略
        }
    }
}

/** 单任务提醒：到点查任务，没完成才发一条带任务标题的通知，发完置 notified。 */
class TaskReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0) return Result.success()
        val container = (applicationContext as BetterLifeApp).container
        val task = container.taskManager.getTask(taskId) ?: return Result.success()
        if (task.done || task.notified) return Result.success()
        val todayStr = LocalDate.now().toString()
        // DAILY 任务只在自己的日期当天提醒：昨天任务的遗留 work 今天触发时直接跳过，
        // 次日的新任务会在创建时（ensureTodayTasks）继承提醒时间并排自己的 work。
        if (task.type == TaskEntity.TYPE_DAILY && task.date != todayStr) {
            return Result.success()
        }
        // ONCE 带截止日：到期当天及之后才提醒；没到截止日的遗留 work（比如改了时间但日期还没到）直接跳过。
        // ONCE 不带截止日维持原行为：排到点（今天/明天）就提醒。
        if (task.type == TaskEntity.TYPE_ONCE && task.dueDate != null && task.dueDate > todayStr) {
            return Result.success()
        }
        // taskTitle 内部会先查条目库再回退 custom_entries，自定义任务显示用户输入的标题
        val title = container.taskManager.taskTitle(taskId) ?: task.entryId
        notifyTask(task.taskId, title)
        container.taskManager.markNotified(task.taskId)
        return Result.success()
    }

    private fun notifyTask(taskId: Long, entryTitle: String) {
        if (!hasNotificationPermission(applicationContext)) return

        ReminderScheduler.ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notification_task_title))
            .setContentText(applicationContext.getString(R.string.notification_task_body, entryTitle))
            .setContentIntent(reminderContentIntent(applicationContext))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(ReminderScheduler.TASK_NOTIFICATION_ID_BASE + taskId.toInt(), notification)
        } catch (_: SecurityException) {
            // 权限被收回等情况，忽略
        }
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
    }
}

/** Android 13+ 需要运行时通知权限，未授权则静默跳过 */
private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 点通知回到 App。[openRoute] 非空时带上深链目标（见 MainActivity.EXTRA_OPEN_ROUTE），
 * AppNav 消费后导航过去。
 * targetSdk 31 起 PendingIntent 必须显式声明可变性；这里不需要外部修改，用 FLAG_IMMUTABLE。
 */
private fun reminderContentIntent(context: Context, openRoute: String? = null): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (openRoute != null) putExtra(MainActivity.EXTRA_OPEN_ROUTE, openRoute)
    }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
