package com.betterlife.app.ai

import com.betterlife.app.data.SettingsGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
private data class BochaResponse(
    val data: Data? = null,
) {
    @Serializable
    data class Data(val webPages: WebPages? = null)

    @Serializable
    data class WebPages(val value: List<Value> = emptyList())

    @Serializable
    data class Value(
        val name: String = "",
        val url: String = "",
        val snippet: String = "",
        val datePublished: String? = null,
    )
}

@Serializable
private data class BochaRequest(
    val query: String,
    val count: Int,
    val summary: Boolean = true,
)

private val parseJson = Json { ignoreUnknownKeys = true }

/** 解析博查 web-search 响应：data.webPages.value[] → WebSearchItem；坏 JSON 返回空列表 */
internal fun parseWebSearchResponse(text: String): List<WebSearchItem> =
    runCatching {
        parseJson.decodeFromString(BochaResponse.serializer(), text)
            .data?.webPages?.value.orEmpty()
            .filter { it.url.isNotBlank() }
            .map { WebSearchItem(it.name, it.url, it.snippet, it.datePublished) }
    }.getOrDefault(emptyList())

/**
 * 博查（Bocha）web-search 实现，移植自 HeartKindle 的 BochaWebSearcher（只取 web-search 部分）。
 * key/endpoint 每次调用时从设置现读，改设置立即生效；key 为空视为未配置。
 */
class BochaWebSearcher(private val settings: SettingsGateway) : WebSearcher {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String, count: Int): Result<List<WebSearchItem>> =
        withContext(Dispatchers.IO) {
            val cfg = settings.current()
            if (cfg.searchApiKey.isBlank()) {
                return@withContext Result.failure(LlmException("还没有配置 AI 搜索的 API Key，请先到「设置」里填写"))
            }
            val endpoint = cfg.searchEndpoint.ifBlank { DEFAULT_ENDPOINT }
            val body = json.encodeToString(BochaRequest.serializer(), BochaRequest(query, count))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${cfg.searchApiKey}")
                .post(body)
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body.string()
                    if (!resp.isSuccessful) {
                        val msg = when (resp.code) {
                            401 -> "AI 搜索的 API Key 无效或已过期，请检查设置"
                            429 -> "搜索请求太频繁或额度不足，请稍后再试"
                            else -> "搜索服务返回错误（HTTP ${resp.code}）"
                        }
                        return@withContext Result.failure(LlmException(msg))
                    }
                    val items = parseWebSearchResponse(text)
                    if (items.isEmpty()) {
                        Result.failure(LlmException("没有搜到相关结果，可以换个说法再试试"))
                    } else {
                        Result.success(items)
                    }
                }
            } catch (e: IOException) {
                Result.failure(LlmException("网络连接失败，请检查网络后重试"))
            } catch (e: Exception) {
                Result.failure(LlmException("搜索请求失败：${e.message ?: "未知错误"}"))
            }
        }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.bochaai.com/v1/web-search"
    }
}
