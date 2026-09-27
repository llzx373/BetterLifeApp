package com.betterlife.app.ai

/** 网络搜索结果条目：标题、链接、给模型读的摘要、发布时间（可能没有） */
data class WebSearchItem(
    val title: String,
    val url: String,
    val snippet: String,
    val datePublished: String? = null,
)

/**
 * 搜索服务抽象：刻意不出现服务商词汇，换搜索服务时只换实现。
 * 参考 HeartKindle 的 domain 层设计。
 */
interface WebSearcher {
    suspend fun search(query: String, count: Int = 5): Result<List<WebSearchItem>>
}
