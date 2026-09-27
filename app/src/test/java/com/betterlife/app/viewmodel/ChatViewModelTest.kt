package com.betterlife.app.viewmodel

import com.betterlife.app.ai.AiAdvisor
import com.betterlife.app.ai.ChatMessage
import com.betterlife.app.ai.EntryRetriever
import com.betterlife.app.ai.LlmClient
import com.betterlife.app.ai.WebSearchItem
import com.betterlife.app.ai.WebSearcher
import com.betterlife.app.data.AiProvider
import com.betterlife.app.data.AppSettings
import com.betterlife.app.data.ChatSettingsGateway
import com.betterlife.app.data.EntriesData
import com.betterlife.app.data.EntriesFile
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.ProfileRepo
import com.betterlife.app.data.RulesFile
import com.betterlife.app.data.SEED_PROVIDERS
import com.betterlife.app.data.db.ChatMessageDao
import com.betterlife.app.data.db.ChatMessageEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** ask 的异常安全与无 Key 降级：任何单点失败都不能让 asking 卡死，空 key 走规则回答 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private class FakeProfileRepo : ProfileRepo {
        override val profileFlow: Flow<Profile?> = MutableStateFlow(null)
        override suspend fun save(profile: Profile) = Unit
    }

    private class FakeChatSettings(settings: AppSettings) : ChatSettingsGateway {
        override val settingsFlow = MutableStateFlow(settings)
        override suspend fun setOnboardingDone(done: Boolean) = Unit
        override suspend fun current(): AppSettings = settingsFlow.value
        override suspend fun setChatProviderIds(ids: List<String>) {
            settingsFlow.value = settingsFlow.value.copy(chatProviderIds = ids)
        }
        override suspend fun setChatWebSearch(enabled: Boolean) {
            settingsFlow.value = settingsFlow.value.copy(chatWebSearch = enabled)
        }
    }

    private class FakeChatMessageDao : ChatMessageDao {
        val saved = mutableListOf<ChatMessageEntity>()
        var failOnInsert = false
        override fun allFlow(): Flow<List<ChatMessageEntity>> = MutableStateFlow(emptyList())
        override suspend fun insert(msg: ChatMessageEntity): Long {
            if (failOnInsert) throw RuntimeException("db write failed")
            saved += msg
            return saved.size.toLong()
        }
        override suspend fun clear() = saved.clear()
        override suspend fun trimToLatest(keep: Int) = Unit
    }

    /** 不触网的搜索器；本组测试都走知识库分支，它只是摆设 */
    private class FakeWebSearcher : WebSearcher {
        override suspend fun search(query: String, count: Int): Result<List<WebSearchItem>> =
            Result.success(emptyList())
    }

    private val entry = EntryDto(
        id = "02-01",
        sec = 2,
        n = 1,
        title = "戒烟是第一收益",
        human = "戒烟一年,心血管风险明显下降",
        gain = "省钱又延寿",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(settings: AppSettings, dao: FakeChatMessageDao): ChatViewModel {
        val chatSettings = FakeChatSettings(settings)
        val advisor = AiAdvisor(LlmClient(chatSettings), EntryRetriever(), chatSettings, FakeWebSearcher())
        val data = EntriesData(EntriesFile(entries = listOf(entry)), RulesFile())
        return ChatViewModel(advisor, FakeProfileRepo(), { data }, chatSettings, dao)
    }

    /** ask 内部走 Dispatchers.IO（真线程，不受测试调度器控制），轮询等思考态收起 */
    private fun awaitNotAsking(vm: ChatViewModel) {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.uiState.value.asking && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    @Test
    fun `零启用供应商时走降级回答且不卡住`() = runTest {
        val dao = FakeChatMessageDao()
        val vm = newViewModel(AppSettings(aiProviders = SEED_PROVIDERS, activeProviderId = "kimi"), dao)

        vm.ask("怎么戒烟")
        awaitNotAsking(vm)

        assertFalse(vm.uiState.value.asking)
        val reply = vm.uiState.value.messages.last()
        assertEquals(ChatMessage.ROLE_ASSISTANT, reply.role)
        assertFalse(reply.isError)
        assertTrue(reply.content.contains("还没有配置 API Key"))
        assertTrue(dao.saved.any { it.role == ChatMessage.ROLE_ASSISTANT && it.content.isNotBlank() })
    }

    @Test
    fun `空Key的启用供应商走规则降级并落库`() = runTest {
        val dao = FakeChatMessageDao()
        val noKeyProvider = AiProvider(id = "a", name = "A", baseUrl = "https://x", apiKey = "", model = "m")
        val vm = newViewModel(AppSettings(aiProviders = listOf(noKeyProvider), activeProviderId = "a"), dao)

        vm.ask("怎么戒烟")
        awaitNotAsking(vm)

        assertFalse(vm.uiState.value.asking)
        val reply = vm.uiState.value.messages.last()
        assertFalse(reply.isError)
        assertTrue(reply.content.contains("还没有配置 API Key"))
        // 用户提问与降级回答都正常落库
        assertEquals(
            listOf(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT),
            dao.saved.map { it.role },
        )
    }

    @Test
    fun `落库异常时asking复位且给出错误气泡`() = runTest {
        val dao = FakeChatMessageDao().apply { failOnInsert = true }
        val noKeyProvider = AiProvider(id = "a", name = "A", baseUrl = "https://x", apiKey = "", model = "m")
        val vm = newViewModel(AppSettings(aiProviders = listOf(noKeyProvider), activeProviderId = "a"), dao)

        vm.ask("怎么戒烟")
        awaitNotAsking(vm)

        assertFalse(vm.uiState.value.asking)
        assertTrue(vm.uiState.value.messages.last().isError)
    }
}
