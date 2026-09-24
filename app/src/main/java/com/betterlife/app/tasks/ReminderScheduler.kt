package com.betterlife.app.tasks

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.betterlife.app.BetterLifeApp
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 每日提醒：WorkManager 周期任务（24h + flex）触发 ensureTodayTasks 并发本地通知。
 */
class ReminderScheduler(private val context: Context) {

    companion object {
        const val WORK_NAME = "daily_reminder"
        const val CHANNEL_ID = "daily"
        const val NOTIFICATION_ID = 1001

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "每日任务", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "每天提醒你完成今日的人生任务"
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
}

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BetterLifeApp).container
        val profile = container.profileRepository.getProfile()
        if (profile != null) {
            container.taskManager.ensureTodayTasks(profile)
        }
        notifyUndone(container.taskManager.todayUndoneCount())
        return Result.success()
    }

    private fun notifyUndone(undone: Int) {
        // Android 13+ 需要运行时通知权限，未授权则静默跳过
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ReminderScheduler.ensureChannel(applicationContext)
        val text = if (undone > 0) "今天还有 $undone 条任务未完成" else "今日任务已全部完成，继续保持"
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("BetterLife 每日任务")
            .setContentText(text)
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
