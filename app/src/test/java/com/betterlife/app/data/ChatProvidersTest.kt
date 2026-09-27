package com.betterlife.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 聊天页并行供应商解析：选中 ∩ 启用；空选择回退「当前使用」 */
class ChatProvidersTest {

    private fun provider(id: String, enabled: Boolean = true) =
        AiProvider(id = id, name = id, baseUrl = "https://x", apiKey = "k", model = "m", enabled = enabled)

    private val providers = listOf(provider("a"), provider("b"), provider("c", enabled = false))

    @Test
    fun `选中的启用供应商全部返回`() {
        val result = resolveChatProviders(providers, listOf("a", "b"), activeId = "a")
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `选中被禁用的供应商被剔除,剩下的仍生效`() {
        val result = resolveChatProviders(providers, listOf("b", "c"), activeId = "a")
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `空选择回退到当前使用的供应商`() {
        val result = resolveChatProviders(providers, emptyList(), activeId = "b")
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `选中的全被禁用时也回退到当前使用`() {
        val result = resolveChatProviders(providers, listOf("c"), activeId = "a")
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `当前使用被禁用时回退到第一个启用`() {
        val result = resolveChatProviders(providers, emptyList(), activeId = "c")
        assertEquals(listOf("a"), result.map { it.id })
    }
}
