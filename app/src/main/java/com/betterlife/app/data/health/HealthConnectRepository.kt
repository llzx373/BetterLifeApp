// Health Connect 路径的步数读取。
//
// SDK 状态由 HealthConnectClient.getSdkStatus 判定：API 26/27、未安装、
// 需要更新都会落到非 AVAILABLE，由门面降级到传感器路径，这里不做补偿。
package com.betterlife.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectRepository(private val context: Context) : HealthConnectSteps {

    override suspend fun status(): HealthConnectStatus =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectStatus.UPDATE_REQUIRED
            else -> HealthConnectStatus.UNAVAILABLE
        }

    override suspend fun hasStepsPermission(): Boolean =
        READ_STEPS_PERMISSION in client().permissionController.getGrantedPermissions()

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

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    companion object {
        val READ_STEPS_PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)
    }
}
