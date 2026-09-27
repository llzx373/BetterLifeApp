package com.betterlife.app.ai

import com.betterlife.app.data.SettingsGateway
import com.betterlife.app.data.resolveActiveProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.3,
    val stream: Boolean = false,
) {
    @Serializable
    data class Message(val role: String, val content: String)
}

@Serializable
private data class ChatResponse(
    val choices: List<Choice> = emptyList(),
) {
    @Serializable
    data class Choice(val message: Message? = null)

    @Serializable
    data class Message(val content: String = "")
}

@Serializable
private data class ModelsResponse(
    val data: List<ModelEntry> = emptyList(),
) {
    @Serializable
    data class ModelEntry(val id: String = "")
}

@Serializable
private data class ErrorResponse(
    val error: ErrorBody? = null,
) {
    @Serializable
    data class ErrorBody(val message: String = "", @SerialName("type") val type: String = "")
}

/**
 * OpenAI 兼容的 chat completions 客户端。
 * 供应商配置每次调用时从 SettingsGateway 读取（取「当前使用」的那张卡），改设置立即生效。
 */
class LlmClient(private val settings: SettingsGateway) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 走设置里「当前使用」的供应商 */
    suspend fun chat(messages: List<ChatMessage>, model: String): Result<String> =
        withContext(Dispatchers.IO) {
            val cfg = settings.current()
            val provider = resolveActiveProvider(cfg.aiProviders, cfg.activeProviderId)
            if (provider == null || provider.apiKey.isBlank()) {
                return@withContext Result.failure(LlmException("还没有配置 API Key，请先到「设置」里填写"))
            }
            if (provider.baseUrl.isBlank()) {
                return@withContext Result.failure(LlmException("还没有配置 API 地址，请先到「设置」里填写"))
            }
            doChat(messages, model.ifBlank { provider.model }, provider.baseUrl, provider.apiKey)
        }

    /** 显式指定配置：设置页测试某张卡时用，不要求它是当前供应商 */
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        baseUrl: String,
        apiKey: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(LlmException("还没有配置 API Key，请先在这张卡里填写"))
        }
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(LlmException("还没有配置 API 地址，请先在这张卡里填写"))
        }
        doChat(messages, model, baseUrl, apiKey)
    }

    /** 拉取供应商的模型列表（OpenAI 兼容的 GET /models），给设置页「扫描模型」用 */
    suspend fun listModels(baseUrl: String, apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(LlmException("还没有配置 API 地址，请先在这张卡里填写"))
            }
            val builder = Request.Builder().url(buildModelsUrl(baseUrl)).get()
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")

            try {
                client.newCall(builder.build()).execute().use { resp ->
                    val text = resp.body.string()
                    if (resp.isSuccessful) {
                        val ids = runCatching {
                            json.decodeFromString(ModelsResponse.serializer(), text)
                                .data.map { it.id }.filter { it.isNotBlank() }
                        }.getOrNull()
                        if (ids.isNullOrEmpty()) {
                            Result.failure(LlmException("未能识别模型列表，请手动输入"))
                        } else {
                            Result.success(ids)
                        }
                    } else {
                        Result.failure(mapHttpError(resp.code, text))
                    }
                }
            } catch (e: IOException) {
                Result.failure(LlmException("网络连接失败，请检查网络后重试"))
            } catch (e: Exception) {
                Result.failure(LlmException("请求失败：${e.message ?: "未知错误"}"))
            }
        }

    /**
     * 流式聊天：请求体加 "stream": true，按 SSE 逐行读增量内容（见 SseParser）。
     * 冷 Flow，被收集时才在 IO 线程同步阻塞读；HTTP 错误或连接失败抛 LlmException，
     * 由调用方决定降级（如回退单次请求）。
     */
    fun chatStream(
        messages: List<ChatMessage>,
        model: String,
        baseUrl: String,
        apiKey: String,
    ): Flow<String> = flow {
        val body = json.encodeToString(
            ChatRequest(
                model = model,
                messages = messages.map { ChatRequest.Message(it.role, it.content) },
                stream = true,
            )
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(buildChatUrl(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw mapHttpError(resp.code, resp.body.string())
                val source = resp.body.source()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when (val event = parseSseLine(line)) {
                        is SseEvent.Data -> extractDeltaContent(event.payload)?.let { emit(it) }
                        SseEvent.Done -> break
                        null -> Unit
                    }
                }
            }
        } catch (e: IOException) {
            throw LlmException("网络连接失败，请检查网络后重试")
        }
    }.flowOn(Dispatchers.IO)

    private fun doChat(
        messages: List<ChatMessage>,
        model: String,
        baseUrl: String,
        apiKey: String,
    ): Result<String> {
        val body = json.encodeToString(
            ChatRequest(
                model = model,
                messages = messages.map { ChatRequest.Message(it.role, it.content) },
            )
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(buildChatUrl(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                // OkHttp 5 起 Response.body 是非空的，这里不再需要安全调用
                val text = resp.body.string()
                if (resp.isSuccessful) {
                    val parsed = json.decodeFromString(ChatResponse.serializer(), text)
                    val content = parsed.choices.firstOrNull()?.message?.content
                    if (content.isNullOrBlank()) {
                        Result.failure(LlmException("服务返回了空内容，请稍后再试"))
                    } else {
                        Result.success(content)
                    }
                } else {
                    Result.failure(mapHttpError(resp.code, text))
                }
            }
        } catch (e: IOException) {
            Result.failure(LlmException("网络连接失败，请检查网络后重试"))
        } catch (e: Exception) {
            Result.failure(LlmException("请求失败：${e.message ?: "未知错误"}"))
        }
    }

    private fun mapHttpError(code: Int, text: String): LlmException {
        val detail = runCatching {
            json.decodeFromString(ErrorResponse.serializer(), text).error?.message
        }.getOrNull().orEmpty()
        val msg = when (code) {
            401 -> "API Key 无效或已过期，请检查设置"
            429 -> "请求太频繁或额度不足，请稍后再试"
            else -> "服务返回错误（HTTP $code）${if (detail.isNotBlank()) "：$detail" else ""}"
        }
        return LlmException(msg)
    }

    /** baseUrl 已含 /v1 时不重复拼 */
    private fun v1Base(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/v1")) base else "$base/v1"
    }

    private fun buildChatUrl(baseUrl: String): String = "${v1Base(baseUrl)}/chat/completions"

    private fun buildModelsUrl(baseUrl: String): String = "${v1Base(baseUrl)}/models"
}

class LlmException(message: String) : Exception(message)
