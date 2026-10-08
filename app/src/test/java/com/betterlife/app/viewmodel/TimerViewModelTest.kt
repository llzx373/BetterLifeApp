package com.betterlife.app.viewmodel

import com.betterlife.app.data.AppSettings
import com.betterlife.app.data.SettingsGateway
import com.betterlife.app.tasks.TimerTaskGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 计时结束必须自动打卡且只打一次；放弃绝不打卡 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {

    private class FakeGateway : TimerTaskGateway {
        var title: String? = "晚饭后走 20 分钟"
        val completedTaskIds = mutableListOf<Long>()
        override suspend fun taskTitle(taskId: Long): String? = title
        override suspend fun completeTask(taskId: Long) {
            completedTaskIds += taskId
        }
    }

    private class FakeSettingsGateway : SettingsGateway {
        override val settingsFlow: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override suspend fun setOnboardingDone(done: Boolean) = Unit
        override suspend fun markAllProfileQuestionsAnswered(fields: Set<String>) = Unit
        override suspend fun current(): AppSettings = AppSettings()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** init 里读标题走 Dispatchers.IO（真线程，不受测试调度器控制），轮询等它落地 */
    private fun awaitSetup(vm: TimerViewModel) {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.uiState.value !is TimerViewModel.UiState.Setup &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        assertTrue(vm.uiState.value is TimerViewModel.UiState.Setup)
    }

    private fun awaitFinished(vm: TimerViewModel, scheduler: TestCoroutineScheduler) {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.uiState.value !is TimerViewModel.UiState.Finished &&
            System.currentTimeMillis() < deadline
        ) {
            // completeTask 走完真线程后，回到 Main（测试调度器）的恢复要有人推进才执行
            scheduler.runCurrent()
            Thread.sleep(10)
        }
    }

    @Test
    fun `加载后进入选时长态并带出任务标题`() = runTest {
        val vm = TimerViewModel(taskId = 7, gateway = FakeGateway(), settings = FakeSettingsGateway())

        awaitSetup(vm)

        assertEquals("晚饭后走 20 分钟", (vm.uiState.value as TimerViewModel.UiState.Setup).title)
    }

    @Test
    fun `任务已删除时退回空标题的选时长态`() = runTest {
        val vm = TimerViewModel(taskId = 7, gateway = FakeGateway().apply { title = null }, settings = FakeSettingsGateway())

        awaitSetup(vm)

        assertEquals("", (vm.uiState.value as TimerViewModel.UiState.Setup).title)
    }

    @Test
    fun `时钟走过时长后自动打卡并进入完成态`() = runTest {
        val gateway = FakeGateway()
        var now = 0L
        val vm = TimerViewModel(taskId = 7, gateway = gateway, settings = FakeSettingsGateway(), clock = { now })
        awaitSetup(vm)

        vm.start(60_000L)
        // tick 循环是无限的，不能用 advanceUntilIdle（永远闲不下来）；
        // runCurrent 只跑当前这一拍：发射 Running 后停在 delay 里
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value is TimerViewModel.UiState.Running)

        // 时钟推进超过时长（等价于切后台再回来），推进到下一拍判定结束
        now += 61_000L
        testScheduler.advanceTimeBy(TICK + 1)
        testScheduler.runCurrent()
        // finish 里 completeTask 走 Dispatchers.IO（真线程），轮询 + 推进调度器等它落地
        awaitFinished(vm, testScheduler)

        assertTrue(vm.uiState.value is TimerViewModel.UiState.Finished)
        assertEquals(listOf(7L), gateway.completedTaskIds)
    }

    @Test
    fun `放弃不打卡`() = runTest {
        val gateway = FakeGateway()
        // 默认时钟是 SystemClock.elapsedRealtime（Android 端），JVM 单测必须注入假时钟
        val vm = TimerViewModel(
            taskId = 7, gateway = gateway, settings = FakeSettingsGateway(), clock = { 0L },
        )
        awaitSetup(vm)

        vm.start(60_000L)
        testScheduler.runCurrent()
        vm.giveUp()
        testScheduler.advanceTimeBy(10_000L)

        assertTrue(gateway.completedTaskIds.isEmpty())
    }

    private companion object {
        const val TICK = 1_000L
    }
}
