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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.MainActivity
import com.betterlife.app.R
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
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(ReminderScheduler.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 权限被收回等情况，忽略
        }
    }

    /**
     * 点通知回到 App。
     * targetSdk 31 起 PendingIntent 必须显式声明可变性；这里不需要外部修改，用 FLAG_IMMUTABLE。
     */
    private fun contentIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
