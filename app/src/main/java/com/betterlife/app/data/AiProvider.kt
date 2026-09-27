package com.betterlife.app.data

import kotlinx.serialization.Serializable

/** AI 供应商卡片：一张卡一组 OpenAI 兼容配置，可启用/禁用，多张卡里挑一张「当前使用」 */
@Serializable
data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean = true,
)

/** 新装时播种的两张预设卡：地址与默认模型预填，无 key、默认禁用，填了 key 再启用 */
val SEED_PROVIDERS: List<AiProvider> = listOf(
    AiProvider(
        id = "kimi",
        name = "Kimi (月之暗面)",
        baseUrl = "https://api.moonshot.cn",
        apiKey = "",
        model = "moonshot-v1-8k",
        enabled = false,
    ),
    AiProvider(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        apiKey = "",
        model = "deepseek-chat",
        enabled = false,
    ),
)

/** 当前生效的供应商：active 且 enabled 优先，否则回退到第一个 enabled；全禁用或空列表返回 null */
fun resolveActiveProvider(providers: List<AiProvider>, activeId: String): AiProvider? {
    providers.firstOrNull { it.id == activeId && it.enabled }?.let { return it }
    return providers.firstOrNull { it.enabled }
}

/**
 * 聊天页要并行提问的供应商：选中的 ∩ 已启用；一个都没选（或选中的都被禁用）时
 * 回退到「当前使用」的那张，保持单供应商的现有行为。
 */
fun resolveChatProviders(
    providers: List<AiProvider>,
    selectedIds: List<String>,
    activeId: String,
): List<AiProvider> {
    val selected = providers.filter { it.enabled && it.id in selectedIds }
    if (selected.isNotEmpty()) return selected
    return listOfNotNull(resolveActiveProvider(providers, activeId))
}
