package com.betterlife.app.data.health

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 门面降级：Health Connect 优先；不可用才轮到传感器；两头都不可用才 Unavailable */
class StepsRepositoryTest {

    private class FakeHealthConnect(
        var status: HealthConnectStatus = HealthConnectStatus.AVAILABLE,
        var granted: Boolean = true,
        var steps: Long = 0L,
    ) : HealthConnectSteps {
        override suspend fun status(): HealthConnectStatus = status
        override suspend fun hasStepsPermission(): Boolean = granted
        override suspend fun readTodaySteps(): Long = steps
    }

    private class FakeSensor(
        override val available: Boolean = true,
        val hasPermission: Boolean = true,
        val stepsFlow: Flow<Long> = flowOf(0L),
    ) : SensorSteps {
        override fun hasPermission(): Boolean = hasPermission
        override fun todayStepsFlow(): Flow<Long> = stepsFlow
    }

    @Test
    fun `HealthConnect可用且已授权则读平台步数`() = runTest {
        val repo = StepsRepository(
            FakeHealthConnect(steps = 8432),
            FakeSensor(stepsFlow = flowOf(999)), // 传感器有值也不该被读到
        )

        assertEquals(
            StepsState.Available(8432, StepsSource.HEALTH_CONNECT),
            repo.stepsFlow().first(),
        )
    }

    @Test
    fun `HealthConnect可用但未授权则给可发起授权的状态`() = runTest {
        val repo = StepsRepository(FakeHealthConnect(granted = false), FakeSensor())

        assertEquals(
            StepsState.Unauthorized(StepsSource.HEALTH_CONNECT),
            repo.stepsFlow().first(),
        )
    }

    @Test
    fun `HealthConnect不可用降级到传感器`() = runTest {
        val repo = StepsRepository(
            FakeHealthConnect(status = HealthConnectStatus.UNAVAILABLE),
            FakeSensor(stepsFlow = flowOf(1204)),
        )

        assertEquals(
            StepsState.Available(1204, StepsSource.SENSOR),
            repo.stepsFlow().first(),
        )
    }

    @Test
    fun `HealthConnect需要更新同样降级到传感器`() = runTest {
        val repo = StepsRepository(
            FakeHealthConnect(status = HealthConnectStatus.UPDATE_REQUIRED),
            FakeSensor(stepsFlow = flowOf(56)),
        )

        assertEquals(
            StepsState.Available(56, StepsSource.SENSOR),
            repo.stepsFlow().first(),
        )
    }

    @Test
    fun `传感器路径缺ACTIVITY_RECOGNITION权限时给传感器授权态`() = runTest {
        val repo = StepsRepository(
            FakeHealthConnect(status = HealthConnectStatus.UNAVAILABLE),
            FakeSensor(hasPermission = false),
        )

        assertEquals(
            StepsState.Unauthorized(StepsSource.SENSOR),
            repo.stepsFlow().first(),
        )
    }

    @Test
    fun `两头都不可用则Unavailable`() = runTest {
        // 模拟器常见情况：没装 Health Connect，也没有计步传感器
        val repo = StepsRepository(
            FakeHealthConnect(status = HealthConnectStatus.UNAVAILABLE),
            FakeSensor(available = false),
        )

        assertEquals(StepsState.Unavailable, repo.stepsFlow().first())
    }
}
