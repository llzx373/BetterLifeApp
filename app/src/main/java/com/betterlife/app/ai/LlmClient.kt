package com.betterlife.app.ai

import com.betterlife.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
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
private data class ErrorResponse(
    val error: ErrorBody? = null,
) {
    @Serializable
    data class ErrorBody(val message: String = "", @SerialName("type") val type: String = "")
}

/**
 * OpenAI 兼容的 chat completions 客户端。
 * baseUrl 与 apiKey 每次调用时从 SettingsStore 读取，改设置立即生效。
 */
class LlmClient(private val settings: SettingsStore) {

    companion object {
        /** 预设服务商 */
        val KIMI = Preset("Kimi (月之暗面)", "https://api.moonshot.cn", "moonshot-v1-8k")
        val DEEPSEEK = Preset("DeepSeek", "https://api.deepseek.com", "deepseek-chat")

        data class Preset(val label: String, val baseUrl: String, val model: String)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun chat(messages: List<ChatMessage>, model: String): Result<String> =
        withContext(Dispatchers.IO) {
            val cfg = settings.current()
            if (cfg.apiKey.isBlank()) {
                return@withContext Result.failure(LlmException("还没有配置 API Key，请先到「设置」里填写"))
            }
            if (cfg.apiBaseUrl.isBlank()) {
                return@withContext Result.failure(LlmException("还没有配置 API 地址，请先到「设置」里填写"))
            }
            val usedModel = model.ifBlank { cfg.apiModel }

            val url = buildUrl(cfg.apiBaseUrl)
            val body = json.encodeToString(
                ChatRequest(
                    model = usedModel,
                    messages = messages.map { ChatRequest.Message(it.role, it.content) },
                )
            ).toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${cfg.apiKey}")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val parsed = json.decodeFromString(ChatResponse.serializer(), text)
                        val content = parsed.choices.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Result.failure(LlmException("服务返回了空内容，请稍后再试"))
                        } else {
                            Result.success(content)
                        }
                    } else {
                        val detail = runCatching {
                            json.decodeFromString(ErrorResponse.serializer(), text).error?.message
                        }.getOrNull().orEmpty()
                        val msg = when (resp.code) {
                            401 -> "API Key 无效或已过期，请检查设置"
                            429 -> "请求太频繁或额度不足，请稍后再试"
                            else -> "服务返回错误（HTTP ${resp.code}）${if (detail.isNotBlank()) "：$detail" else ""}"
                        }
                        Result.failure(LlmException(msg))
                    }
                }
            } catch (e: IOException) {
                Result.failure(LlmException("网络连接失败，请检查网络后重试"))
            } catch (e: Exception) {
                Result.failure(LlmException("请求失败：${e.message ?: "未知错误"}"))
            }
        }

    /** baseUrl 已含 /v1 时不重复拼 */
    private fun buildUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
    }
}

class LlmException(message: String) : Exception(message)
