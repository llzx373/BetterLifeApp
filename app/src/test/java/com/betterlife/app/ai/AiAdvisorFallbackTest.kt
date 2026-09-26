package com.betterlife.app.ai

import com.betterlife.app.data.AppSettings
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.SettingsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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
        override suspend fun current(): AppSettings = AppSettings()
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
        return AiAdvisor(LlmClient(settings), EntryRetriever(), settings)
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
}
