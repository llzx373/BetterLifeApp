// Health Connect 路径的健康数据读取：步数（今日步数卡片）+ 运动/睡眠（B1 自动核销）。
//
// SDK 状态由 HealthConnectClient.getSdkStatus 判定：API 26/27、未安装、
// 需要更新都会落到非 AVAILABLE，由门面降级到传感器路径，这里不做补偿。
package com.betterlife.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectRepository(private val context: Context) : HealthConnectSteps, HealthConnectMetrics {

    override suspend fun status(): HealthConnectStatus =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectStatus.UPDATE_REQUIRED
            else -> HealthConnectStatus.UNAVAILABLE
        }

    override suspend fun hasStepsPermission(): Boolean =
        READ_STEPS_PERMISSION in client().permissionController.getGrantedPermissions()

    override suspend fun hasExercisePermission(): Boolean =
        READ_EXERCISE_PERMISSION in client().permissionController.getGrantedPermissions()

    override suspend fun hasSleepPermission(): Boolean =
        READ_SLEEP_PERMISSION in client().permissionController.getGrantedPermissions()

    override suspend fun readTodaySteps(): Long {
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val response = client().readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(dayStart, Instant.now()),
            ),
        )
        return response.records.sumOf { it.count }
    }

    override suspend fun readTodayExerciseMinutes(): Long {
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val response = client().readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(dayStart, Instant.now()),
            ),
        )
        return response.records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
    }

    /**
     * 「昨晚」的睡眠：窗口固定为 [昨天 18:00, 今天 12:00)，与查询时刻无关，保证确定性。
     * HC 的 between 过滤命中与窗口有交集的 session，时长按 session 全长合计（不裁剪到窗口）。
     */
    override suspend fun readLastNightSleepHours(): Double {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val windowStart = today.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val windowEnd = today.atTime(12, 0).atZone(zone).toInstant()
        val response = client().readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(windowStart, windowEnd),
            ),
        )
        val millis = response.records.sumOf { Duration.between(it.startTime, it.endTime).toMillis() }
        return millis / 3_600_000.0
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    companion object {
        val READ_STEPS_PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)
        val READ_EXERCISE_PERMISSION: String =
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val READ_SLEEP_PERMISSION: String =
            HealthPermission.getReadPermission(SleepSessionRecord::class)

        /** B1 自动核销涉及的全部读权限，UI 发起授权时一次申请 */
        val AUTO_COMPLETE_PERMISSIONS: Set<String> =
            setOf(READ_STEPS_PERMISSION, READ_EXERCISE_PERMISSION, READ_SLEEP_PERMISSION)
    }
}
