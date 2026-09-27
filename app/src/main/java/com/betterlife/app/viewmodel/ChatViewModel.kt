package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.ai.AiAdvisor
import com.betterlife.app.ai.AiSource
import com.betterlife.app.ai.AiStreamEvent
import com.betterlife.app.ai.ChatMessage
import com.betterlife.app.data.AiProvider
import com.betterlife.app.data.ChatSettingsGateway
import com.betterlife.app.data.EntriesData
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.ProfileRepo
import com.betterlife.app.data.RulesFile
import com.betterlife.app.data.db.ChatMessageDao
import com.betterlife.app.data.db.ChatMessageEntity
import com.betterlife.app.data.resolveChatProviders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class ChatViewModel(
    private val aiAdvisor: AiAdvisor,
    private val profileRepository: ProfileRepo,
    private val entriesData: () -> EntriesData,
    private val settingsStore: ChatSettingsGateway,
    private val chatMessageDao: ChatMessageDao,
) : ViewModel() {

    data class ChatUiMessage(
        val role: String,       // user | assistant
        val content: String,
        val isError: Boolean = false,
        /** content 为空时要展示的文案资源（如「该供应商未配置 API Key」），null = 用 content */
        val contentRes: Int? = null,
        /** 多供应商并行时标注这张气泡是谁答的；单供应商为 null，UI 不显示 */
        val providerName: String? = null,
        val sources: List<AiSource> = emptyList(),
    )

    data class UiState(
        val messages: List<ChatUiMessage> = emptyList(),
        val asking: Boolean = false,
        /** 本地条目数,思考态文案用它说清「检索了多少条」 */
        val entryCount: Int = 0,
        /** 已启用的供应商（选择对话框的可选项） */
        val enabledProviders: List<AiProvider> = emptyList(),
        /** 聊天页选中的并行供应商；空 = 只用「当前使用」 */
        val selectedProviderIds: Set<String> = emptySet(),
        /** 来源开关：true = 互联网搜索，false = 本地知识库 */
        val webSearch: Boolean = false,
        /** 搜索 key 是否已配置；没配时互联网开关降级为知识库 */
        val searchConfigured: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // 启动时回放本地聊天记录：只取首帧，之后的消息由本会话自己维护。
        // 落库只存 role/content/providerName，sources、错误标记等 UI 态重载时不还原。
        viewModelScope.launch(Dispatchers.IO) {
            val history = chatMessageDao.allFlow().first()
                .map { ChatUiMessage(role = it.role, content = it.content, providerName = it.providerName) }
            if (history.isEmpty()) return@launch
            _uiState.update { it.copy(messages = history + it.messages) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val count = entriesData().entries.size
            _uiState.update { it.copy(entryCount = count) }
        }
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { cfg ->
                _uiState.update {
                    it.copy(
                        enabledProviders = cfg.aiProviders.filter { p -> p.enabled },
                        selectedProviderIds = cfg.chatProviderIds.toSet(),
                        webSearch = cfg.chatWebSearch,
                        searchConfigured = cfg.searchApiKey.isNotBlank(),
                    )
                }
            }
        }
    }

    fun ask(question: String) {
        if (question.isBlank() || _uiState.value.asking) return
        _uiState.update {
            it.copy(
                asking = true,
                messages = it.messages + ChatUiMessage(ChatMessage.ROLE_USER, question),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                persist(ChatMessage.ROLE_USER, question)
                val profile = profileRepository.profileFlow.first() ?: Profile()
                val data = entriesData()
                val cfg = settingsStore.current()
                // 一个启用的供应商都没有时，用无 key 的占位供应商走 AiAdvisor 的规则降级回答
                val providers = resolveChatProviders(cfg.aiProviders, cfg.chatProviderIds, cfg.activeProviderId)
                    .ifEmpty { listOf(FALLBACK_PROVIDER) }
                val useWeb = cfg.chatWebSearch && cfg.searchApiKey.isNotBlank()
                val multi = providers.size > 1
                // 每个供应商一个协程并行提问，谁先回来谁先上屏；全部完成才收起思考态
                val pending = AtomicInteger(providers.size)
                providers.forEach { provider ->
                    launch {
                        try {
                            val name = provider.name.takeIf { multi }
                            askProvider(provider, name, profile, question, data.entries, data.rules, useWeb)
                        } finally {
                            // 任何单点失败都不能让 asking 卡在 true
                            val allDone = pending.decrementAndGet() == 0
                            _uiState.update { it.copy(asking = !allDone) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendMessage(ChatUiMessage(ChatMessage.ROLE_ASSISTANT, e.message.orEmpty(), isError = true))
                _uiState.update { it.copy(asking = false) }
            }
        }
    }

    /**
     * 单个供应商：流式上屏（气泡边收边长），流式整体失败时 AiAdvisor 内已回退单次；完整回答落库。
     * 空 key 不在这里拦截：AiAdvisor 会走无 Key 降级（知识库 top3 / 原始搜索结果）直接作答。
     */
    private suspend fun askProvider(
        provider: AiProvider,
        name: String?,
        profile: Profile,
        question: String,
        entries: List<EntryDto>,
        rules: RulesFile,
        useWeb: Boolean,
    ) {
        // 先放空气泡，流式增量就地填充
        var bubble = ChatUiMessage(ChatMessage.ROLE_ASSISTANT, "", providerName = name)
        appendMessage(bubble)
        var failure: String? = null
        try {
            aiAdvisor.askStream(profile, question, entries, rules, provider, useWeb)
                .collect { event ->
                    val next = when (event) {
                        is AiStreamEvent.Delta -> bubble.copy(content = bubble.content + event.text)
                        is AiStreamEvent.Done -> bubble.copy(content = event.fullText, sources = event.sources)
                    }
                    replaceMessage(bubble, next)
                    bubble = next
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e.message.orEmpty()
        }
        val err = failure
        if (err != null && bubble.content.isEmpty()) {
            val next = bubble.copy(content = err, isError = true)
            replaceMessage(bubble, next)
            bubble = next
        }
        // 完整（或断流后残缺但非空）的回答落库；纯错误不入库
        if (!bubble.isError) {
            persist(ChatMessage.ROLE_ASSISTANT, bubble.content, name)
        }
    }

    private fun appendMessage(msg: ChatUiMessage) {
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    /** 就地替换气泡：流式更新靠对象身份定位，不给 ChatUiMessage 引入额外 id 字段 */
    private fun replaceMessage(old: ChatUiMessage, new: ChatUiMessage) {
        _uiState.update { s ->
            s.copy(messages = s.messages.map { if (it === old) new else it })
        }
    }

    /** 落库一条消息并封顶保留最新 [MAX_HISTORY] 条；空内容（占位错误等）不入库 */
    private suspend fun persist(role: String, content: String, providerName: String? = null) {
        if (content.isBlank()) return
        chatMessageDao.insert(
            ChatMessageEntity(
                role = role,
                content = content,
                providerName = providerName,
                createdAt = System.currentTimeMillis(),
            )
        )
        chatMessageDao.trimToLatest(MAX_HISTORY)
    }

    /** 清空聊天记录（DB + 当前界面），设置页/聊天页的「清空」入口共用 */
    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            chatMessageDao.clear()
            _uiState.update { it.copy(messages = emptyList()) }
        }
    }

    /** 聊天页并行供应商选择：空列表 = 只用「当前使用」 */
    fun setChatProviders(ids: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setChatProviderIds(ids.toList())
        }
    }

    fun toggleWebSearch() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setChatWebSearch(!settingsStore.current().chatWebSearch)
        }
    }

    /** 已触发过解读的条目：旋转重建 Composition 会重放 LaunchedEffect,不能重复发 */
    private val explainedEntryIds = mutableSetOf<String>()

    /** 条目详情页跳进来的自动解读:只发 assistant 消息,不伪造一条用户提问 */
    fun explainEntry(entryId: String) {
        if (!explainedEntryIds.add(entryId) || _uiState.value.asking) return
        _uiState.update { it.copy(asking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = profileRepository.profileFlow.first() ?: Profile()
                val data = entriesData()
                val result = aiAdvisor.explainEntry(profile, entryId, data.entries)
                val reply = result.fold(
                    onSuccess = { ChatUiMessage(ChatMessage.ROLE_ASSISTANT, it) },
                    onFailure = { ChatUiMessage(ChatMessage.ROLE_ASSISTANT, it.message.orEmpty(), isError = true) },
                )
                _uiState.update {
                    it.copy(asking = false, messages = it.messages + reply)
                }
                if (!reply.isError) persist(ChatMessage.ROLE_ASSISTANT, reply.content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        asking = false,
                        messages = it.messages + ChatUiMessage(ChatMessage.ROLE_ASSISTANT, e.message.orEmpty(), isError = true),
                    )
                }
            }
        }
    }

    companion object {
        /** 聊天记录封顶条数，超出后从最旧的删起 */
        private const val MAX_HISTORY = 200

        /** 零启用供应商时的占位卡：无 key，走 AiAdvisor 的规则降级回答 */
        private val FALLBACK_PROVIDER = AiProvider(id = "fallback", name = "", baseUrl = "", apiKey = "", model = "")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                ChatViewModel(
                    c.aiAdvisor,
                    c.profileRepository,
                    c.entryRepository::entriesData,
                    c.settingsStore,
                    c.chatMessageDao,
                )
            }
        }
    }
}
