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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.MainActivity
import com.betterlife.app.R
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.recommend.ProfileQuestions
import com.betterlife.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 每日提醒：WorkManager 周期任务（24h + flex）触发 ensureTodayTasks 并发本地通知。
 */
class ReminderScheduler(private val context: Context) {

    companion object {
        const val WORK_NAME = "daily_reminder"
        const val CHANNEL_ID = "daily"
        const val NOTIFICATION_ID = 1001

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

    /** 按设定时间入队（已存在则更新）。首次触发时间对齐到最近的 hour:minute。 */
    fun enqueue(hour: Int, minute: Int) {
        ensureChannel(context)
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val initialDelayMs = Duration.between(now, next).toMillis()

        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
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
        // N1：没档案也能规划（空档案走普惠推荐 + 种子池兜底），门槛改为「看过引导」——
        // 装完从未打开过的用户不应被静默播种和提醒
        if (container.settingsStore.current().onboardingDone) {
            val profile = container.profileRepository.getProfile()
            val answered = container.settingsStore.profileQuestionsAnsweredFlow.first()
            container.taskManager.ensureTodayTasks(ProfileQuestions.effectiveProfile(profile, answered))
        }
        // B1：先用 Health Connect 数据自动核销达标的每日任务，再统计未完成数发通知；
        // 尽力而为——HC 不可用/没权限/出异常都安静跳过
        runCatching { container.taskManager.autoCompleteByHealth() }
        // B5：新一天任务已生成 / 自动核销已落库，同步刷新桌面小部件
        WidgetUpdater.refresh(applicationContext)
        notifyUndone(container.taskManager.todayUndoneCount())
        return Result.success()
    }

    private fun notifyUndone(undone: Int) {
        if (!hasNotificationPermission(applicationContext)) return

        ReminderScheduler.ensureChannel(applicationContext)
        val text = if (undone > 0) {
            applicationContext.getString(R.string.notification_daily_undone, undone)
        } else {
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
 * 点通知回到 App。
 * targetSdk 31 起 PendingIntent 必须显式声明可变性；这里不需要外部修改，用 FLAG_IMMUTABLE。
 */
private fun reminderContentIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
