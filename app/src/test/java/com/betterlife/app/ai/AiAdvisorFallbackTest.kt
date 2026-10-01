package com.betterlife.app.ai

import com.betterlife.app.data.AiProvider
import com.betterlife.app.data.AppSettings
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RulesFile
import com.betterlife.app.data.SettingsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * explainEntry 的降级路径：没有 apiKey 时不触网（LlmClient 不会被调用），
 * 返回基于条目本身的降级文本；条目不存在要失败而不是静默。
 */
class AiAdvisorFallbackTest {

    /** 空 apiKey 的设置：AiAdvisor 走 fallback，LlmClient 只是摆设 */
    private class FakeSettingsGateway : SettingsGateway {
        override val settingsFlow: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override suspend fun setOnboardingDone(done: Boolean) = Unit
        override suspend fun markAllProfileQuestionsAnswered(fields: Set<String>) = Unit
        override suspend fun current(): AppSettings = AppSettings()
    }

    /** 不会真触网的搜索器：fallback 测试里 ask 不走互联网分支，它只是摆设 */
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

    private fun newAdvisor(): AiAdvisor {
        val settings = FakeSettingsGateway()
        return AiAdvisor(LlmClient(settings), EntryRetriever(), settings, FakeWebSearcher())
    }

    @Test
    fun `无Key时explainEntry返回降级文本并含条目标题`() = runTest {
        val result = newAdvisor().explainEntry(Profile(), entry.id, listOf(entry))

        assertTrue(result.isSuccess)
        val text = result.getOrThrow()
        assertTrue(text.contains(entry.title))
        assertTrue(text.contains(entry.human))
    }

    @Test
    fun `条目不存在时返回失败`() = runTest {
        val result = newAdvisor().explainEntry(Profile(), "99-99", listOf(entry))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `无Key时askStream直接发降级Done不触网`() = runTest {
        val noKeyProvider = AiProvider(id = "a", name = "A", baseUrl = "https://x", apiKey = "", model = "m")
        val events = newAdvisor()
            .askStream(Profile(), "怎么戒烟", listOf(entry), RulesFile(), noKeyProvider, useWeb = false)
            .toList()

        assertEquals(1, events.size)
        val done = events.single() as AiStreamEvent.Done
        assertTrue(done.fullText.contains("还没有配置 API Key"))
        assertTrue(done.fullText.contains(entry.title))
    }
}
