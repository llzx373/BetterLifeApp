package com.betterlife.app.data

import android.content.Context
import kotlinx.serialization.json.Json

/** 条目 + 规则文件的内存视图，带索引 */
class EntriesData(
    val entriesFile: EntriesFile,
    val rulesFile: RulesFile,
) {
    val entries: List<EntryDto> = entriesFile.entries
    val sections: List<SectionDto> = entriesFile.sections
    val byId: Map<String, EntryDto> = entries.associateBy { it.id }
    val bySection: Map<Int, List<EntryDto>> = entries.groupBy { it.sec }
    val dailyEntryIds: List<String> = rulesFile.dailyEntryIds
    val rules: RulesFile = rulesFile
}

/**
 * 从 assets 加载 entries.json 与 relevance_rules.json。
 * 加载结果缓存一次，之后所有读取走内存。
 */
class EntryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: EntriesData? = null

    /** 非阻塞式线程安全加载；首次调用会做 IO + 解析，建议在 IO 调度器里首次触发 */
    fun entriesData(): EntriesData =
        cache ?: synchronized(this) {
            cache ?: load().also { cache = it }
        }

    private fun load(): EntriesData {
        val entries = context.assets.open("entries.json").bufferedReader().use {
            json.decodeFromString(EntriesFile.serializer(), it.readText())
        }
        val rules = context.assets.open("relevance_rules.json").bufferedReader().use {
            json.decodeFromString(RulesFile.serializer(), it.readText())
        }
        return EntriesData(entries, rules)
    }
}
