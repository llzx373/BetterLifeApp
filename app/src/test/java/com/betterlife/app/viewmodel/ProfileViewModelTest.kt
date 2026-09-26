package com.betterlife.app.viewmodel

import com.betterlife.app.data.AppSettings
import com.betterlife.app.data.Goal
import com.betterlife.app.data.Profile
import com.betterlife.app.data.ProfileRepo
import com.betterlife.app.data.SettingsGateway
import com.betterlife.app.data.toggleGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 「我的」页目标修改必须落库；update 只改内存，save 才落库（引导页编辑中不落库） */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private class FakeProfileRepo : ProfileRepo {
        override val profileFlow: Flow<Profile?> = MutableStateFlow(null)
        val saved = mutableListOf<Profile>()
        var failOnSave = false
        override suspend fun save(profile: Profile) {
            if (failOnSave) throw RuntimeException("db write failed")
            saved += profile
        }
    }

    private class FakeSettingsGateway : SettingsGateway {
        override val settingsFlow: Flow<AppSettings> = MutableStateFlow(AppSettings())
        val onboardingDoneCalls = mutableListOf<Boolean>()
        override suspend fun setOnboardingDone(done: Boolean) {
            onboardingDoneCalls += done
        }
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

    @Test
    fun `toggleGoal 勾选后 save 落库`() = runTest {
        val repo = FakeProfileRepo()
        val vm = ProfileViewModel(repo, FakeSettingsGateway())

        vm.update { it.toggleGoal(Goal.HEALTH, true) }
        vm.save()

        // save 内部走 Dispatchers.IO（真线程，不受测试调度器控制），轮询等它落地
        val deadline = System.currentTimeMillis() + 5_000
        while (repo.saved.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)

        assertEquals(1, repo.saved.size)
        assertTrue(Goal.HEALTH in repo.saved.single().goals)
    }

    @Test
    fun `toggleGoal 取消勾选幂等移除`() {
        val base = Profile(goals = setOf(Goal.HEALTH, Goal.MONEY))

        assertEquals(setOf(Goal.MONEY), base.toggleGoal(Goal.HEALTH, false).goals)
        // 移除不存在的目标、勾选已存在的目标，都不改变集合
        assertEquals(base.goals, base.toggleGoal(Goal.TIME, false).goals)
        assertEquals(base.goals, base.toggleGoal(Goal.HEALTH, true).goals)
    }

    @Test
    fun `update 不触发落库`() = runTest {
        val repo = FakeProfileRepo()
        val vm = ProfileViewModel(repo, FakeSettingsGateway())

        vm.update { it.toggleGoal(Goal.HEALTH, true) }

        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `save成功后状态流转到Success`() = runTest {
        val repo = FakeProfileRepo()
        val gateway = FakeSettingsGateway()
        val vm = ProfileViewModel(repo, gateway)

        vm.save(markOnboardingDone = true)

        // save 内部走 Dispatchers.IO（真线程），轮询等状态落地
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.saveState.value !is ProfileViewModel.SaveState.Success &&
            System.currentTimeMillis() < deadline
        ) Thread.sleep(10)

        assertEquals(ProfileViewModel.SaveState.Success, vm.saveState.value)
        assertEquals(listOf(true), gateway.onboardingDoneCalls)
    }

    @Test
    fun `save失败置Failed且不标记onboardingDone`() = runTest {
        val repo = FakeProfileRepo().apply { failOnSave = true }
        val gateway = FakeSettingsGateway()
        val vm = ProfileViewModel(repo, gateway)

        vm.save(markOnboardingDone = true)

        val deadline = System.currentTimeMillis() + 5_000
        while (vm.saveState.value !is ProfileViewModel.SaveState.Failed &&
            System.currentTimeMillis() < deadline
        ) Thread.sleep(10)

        assertEquals(ProfileViewModel.SaveState.Failed, vm.saveState.value)
        assertTrue(gateway.onboardingDoneCalls.isEmpty())
    }
}
