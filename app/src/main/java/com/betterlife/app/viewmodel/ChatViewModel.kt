package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.ai.AiAdvisor
import com.betterlife.app.ai.ChatMessage
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val aiAdvisor: AiAdvisor,
    private val profileRepository: ProfileRepository,
    private val entryRepository: EntryRepository,
) : ViewModel() {

    data class ChatUiMessage(
        val role: String,       // user | assistant
        val content: String,
        val isError: Boolean = false,
    )

    data class UiState(
        val messages: List<ChatUiMessage> = emptyList(),
        val asking: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun ask(question: String) {
        if (question.isBlank() || _uiState.value.asking) return
        _uiState.value = _uiState.value.copy(
            asking = true,
            messages = _uiState.value.messages + ChatUiMessage(ChatMessage.ROLE_USER, question),
        )
        viewModelScope.launch(Dispatchers.IO) {
            val profile = profileRepository.profileFlow.first()
                ?: com.betterlife.app.data.Profile()
            val data = entryRepository.entriesData()
            val result = aiAdvisor.ask(profile, question, data.entries, data.rules)
            val reply = result.fold(
                onSuccess = { ChatUiMessage(ChatMessage.ROLE_ASSISTANT, it) },
                onFailure = { ChatUiMessage(ChatMessage.ROLE_ASSISTANT, it.message ?: "出错了", isError = true) },
            )
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    asking = false,
                    messages = _uiState.value.messages + reply,
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                ChatViewModel(c.aiAdvisor, c.profileRepository, c.entryRepository)
            }
        }
    }
}
