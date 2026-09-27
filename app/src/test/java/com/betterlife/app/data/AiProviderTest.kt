package com.betterlife.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 当前生效供应商的解析：active 且 enabled 优先，否则第一个 enabled，全禁用/空列表为 null */
class AiProviderTest {

    private fun provider(id: String, enabled: Boolean = true) = AiProvider(
        id = id,
        name = id,
        baseUrl = "https://example.com",
        apiKey = "key-$id",
        model = "model-$id",
        enabled = enabled,
    )

    @Test
    fun `active 生效时返回 active 那张`() {
        val providers = listOf(provider("a"), provider("b"))
        assertEquals("b", resolveActiveProvider(providers, "b")?.id)
    }

    @Test
    fun `active 被禁用时回退到第一个 enabled`() {
        val providers = listOf(provider("a"), provider("b", enabled = false), provider("c"))
        assertEquals("a", resolveActiveProvider(providers, "b")?.id)
    }

    @Test
    fun `active id 不存在时回退到第一个 enabled`() {
        val providers = listOf(provider("a", enabled = false), provider("b"))
        assertEquals("b", resolveActiveProvider(providers, "missing")?.id)
    }

    @Test
    fun `全部禁用时返回 null`() {
        val providers = listOf(provider("a", enabled = false), provider("b", enabled = false))
        assertNull(resolveActiveProvider(providers, "a"))
    }

    @Test
    fun `空列表返回 null`() {
        assertNull(resolveActiveProvider(emptyList(), "a"))
    }
}
