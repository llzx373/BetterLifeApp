package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.Profile
import com.betterlife.app.data.ProfileRepo
import com.betterlife.app.data.SettingsGateway
import com.betterlife.app.recommend.ProfileQuestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepo,
    private val settingsStore: SettingsGateway,
) : ViewModel() {

    /** 当前编辑中的档案；打开页面时已存档案会覆盖进来 */
    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    /** 保存流程的状态，交给界面翻成进度态/错误文案 */
    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data object Failed : SaveState
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            profileRepository.profileFlow.collect { p ->
                if (p != null) _profile.value = p
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.settingsFlow.collect { _onboardingDone.value = it.onboardingDone }
        }
    }

    fun update(transform: (Profile) -> Profile) {
        _profile.value = transform(_profile.value)
        _saveState.value = SaveState.Idle
    }

    /** 保存档案；markOnboardingDone 用于引导页最后一步。落库失败置 Failed，界面提示重试 */
    fun save(markOnboardingDone: Boolean = false) {
        val snapshot = _profile.value
        _saveState.value = SaveState.Saving
        viewModelScope.launch(Dispatchers.IO) {
            try {
                profileRepository.save(snapshot)
                if (markOnboardingDone) settingsStore.setOnboardingDone(true)
                // 完整向导保存 = 全部字段已填（N1）：每日一问就此终止
                settingsStore.markAllProfileQuestionsAnswered(ProfileQuestions.ORDER.toSet())
                _saveState.value = SaveState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveState.value = SaveState.Failed
            }
        }
    }

    /** 「先随便看看」（N1）：只标记看过引导，不写档案——空档案照样出推荐 */
    fun skipOnboarding() {
        _saveState.value = SaveState.Saving
        viewModelScope.launch(Dispatchers.IO) {
            try {
                settingsStore.setOnboardingDone(true)
                _saveState.value = SaveState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveState.value = SaveState.Failed
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                ProfileViewModel(c.profileRepository, c.settingsStore)
            }
        }
    }
}
