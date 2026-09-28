package com.betterlife.app.tasks

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.content.SyncResult
import java.util.concurrent.TimeUnit

/**
 * 内容库周期同步（每天一次，需联网）：拉 GitHub Releases 的 content-latest 清单，
 * 有新版本就增量/全量落 Room（见 ContentSyncRepository）。
 * 失败一律 retry，交给 WorkManager 的指数退避；UpToDate/Updated 都算 success。
 * 依赖与 DailyReminderWorker 同款：从 Application.container 取。
 */
class ContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BetterLifeApp).container
        return when (container.contentSyncRepository.syncOnce()) {
            is SyncResult.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "content_sync"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** 应用启动时注册周期同步；KEEP：已排队的（含退避中的）不被重置 */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ContentSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** 设置页「检查更新」：立刻跑一次，与周期队列互不影响 */
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<ContentSyncWorker>()
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
