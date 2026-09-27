// assets/health_rules.json 的加载器：与 EntryRepository 同款，加载一次后走内存缓存。
package com.betterlife.app.data.health

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Health Connect 自动核销规则（entryId → 可验证的健康数据条件）的仓库。
 * 规则刻意保守：只收录语义无歧义、能用 HC 数据直接验证的条目。
 */
class HealthRulesRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: HealthRulesFile? = null

    /** 非阻塞式线程安全加载；首次调用会做 IO + 解析 */
    fun rules(): HealthRulesFile =
        cache ?: synchronized(this) {
            cache ?: load().also { cache = it }
        }

    private fun load(): HealthRulesFile =
        context.assets.open("health_rules.json").bufferedReader().use {
            json.decodeFromString(HealthRulesFile.serializer(), it.readText())
        }
}
