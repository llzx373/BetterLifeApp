package com.betterlife.app.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** SSE 一行解析出的事件：data 载荷，或流结束标记 */
sealed interface SseEvent {
    data class Data(val payload: String) : SseEvent
    data object Done : SseEvent
}

@Serializable
private data class StreamChunk(
    val choices: List<Choice> = emptyList(),
) {
    @Serializable
    data class Choice(val delta: Delta? = null)

    @Serializable
    data class Delta(val content: String? = null)
}

private val chunkJson = Json { ignoreUnknownKeys = true }

/**
 * 解析 SSE 的一行。空行（事件分隔）、注释（":" 开头的 keep-alive）、
 * event:/id:/retry: 等非 data 字段都返回 null；"data: [DONE]" 返回 Done，
 * 其余 data 行返回 Data(载荷)。兼容 "data:" 后不带空格的写法。
 */
fun parseSseLine(line: String): SseEvent? {
    val t = line.trimEnd('\r')
    if (t.isEmpty() || t.startsWith(":")) return null
    if (!t.startsWith("data:")) return null
    val payload = t.removePrefix("data:").trim()
    if (payload.isEmpty()) return null
    return if (payload == "[DONE]") SseEvent.Done else SseEvent.Data(payload)
}

/**
 * 从 data 载荷 JSON 里取增量文本（OpenAI 兼容的 choices[0].delta.content）。
 * 首块只带 role 没有 content、或 JSON 损坏时返回 null。
 */
fun extractDeltaContent(payloadJson: String): String? =
    runCatching {
        chunkJson.decodeFromString(StreamChunk.serializer(), payloadJson)
            .choices.firstOrNull()?.delta?.content
    }.getOrNull()?.takeIf { it.isNotEmpty() }
