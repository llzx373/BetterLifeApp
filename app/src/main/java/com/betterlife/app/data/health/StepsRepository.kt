// 今日步数的统一门面：Health Connect 优先，彻底不可用时降级本机计步传感器。
//
// 两个数据源各抽象成一个窄接口（项目没有 mock 库，照 ProfileRepo 先例，测试喂 fake）。
// 对上层只暴露一个状态流；「该走哪种授权流程」由 Unauthorized 里的 source 决定。
package com.betterlife.app.data.health

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** 步数来源：Health Connect 平台 或 本机计步传感器 */
enum class StepsSource { HEALTH_CONNECT, SENSOR }

sealed interface StepsState {
    data object Loading : StepsState

    /** 数据源可用但还没授权；UI 按 source 发起 Health Connect 授权或运行时权限申请 */
    data class Unauthorized(val source: StepsSource) : StepsState

    data class Available(val steps: Long, val source: StepsSource) : StepsState

    /** 两头都不可用（无 Health Connect 且无计步传感器，比如模拟器）—— UI 不渲染卡片 */
    data object Unavailable : StepsState
}

enum class HealthConnectStatus { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

interface HealthConnectSteps {
    suspend fun status(): HealthConnectStatus
    suspend fun hasStepsPermission(): Boolean

    /** 当天 0 点（设备本地时区）到现在的步数合计 */
    suspend fun readTodaySteps(): Long
}

interface SensorSteps {
    /** 设备有没有计步传感器（部分模拟器没有） */
    val available: Boolean

    /** API 29+ 需要 ACTIVITY_RECOGNITION 运行时权限 */
    fun hasPermission(): Boolean

    /** 今日步数实时流：传感器来一个新读数就算一次（含跨天基线重置） */
    fun todayStepsFlow(): Flow<Long>
}

class StepsRepository(
    private val healthConnect: HealthConnectSteps,
    private val sensor: SensorSteps,
) {

    /**
     * 状态流。Health Connect 可用即不再看传感器：
     * 可用 + 已授权 → 读一次快照；可用 + 未授权 → Unauthorized；
     * 不可用（未安装 / 需更新 / API 26-27）→ 降级传感器，传感器路径的步数随事件实时更新。
     *
     * 每次收集都会重新判定状态，权限变化后 VM 重新收集即可生效。
     */
    fun stepsFlow(): Flow<StepsState> = flow {
        when (healthConnect.status()) {
            HealthConnectStatus.AVAILABLE -> emit(
                if (healthConnect.hasStepsPermission()) {
                    StepsState.Available(healthConnect.readTodaySteps(), StepsSource.HEALTH_CONNECT)
                } else {
                    StepsState.Unauthorized(StepsSource.HEALTH_CONNECT)
                },
            )

            else -> when {
                !sensor.available -> emit(StepsState.Unavailable)
                !sensor.hasPermission() -> emit(StepsState.Unauthorized(StepsSource.SENSOR))
                else -> emitAll(
                    sensor.todayStepsFlow().map { StepsState.Available(it, StepsSource.SENSOR) },
                )
            }
        }
    }
}
