package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.data.Profile
import com.betterlife.app.data.ProfileRepository
import com.betterlife.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    /** 当前编辑中的档案；打开页面时已存档案会覆盖进来 */
    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

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
        _saved.value = false
    }

    /** 保存档案；markOnboardingDone 用于引导页最后一步 */
    fun save(markOnboardingDone: Boolean = false) {
        val snapshot = _profile.value
        viewModelScope.launch(Dispatchers.IO) {
            profileRepository.save(snapshot)
            if (markOnboardingDone) settingsStore.setOnboardingDone(true)
            _saved.value = true
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
