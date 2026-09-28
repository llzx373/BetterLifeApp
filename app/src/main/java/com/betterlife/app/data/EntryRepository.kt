package com.betterlife.app.data

import android.content.Context
import com.betterlife.app.data.content.ContentBootstrap
import com.betterlife.app.data.db.ContentDao
import com.betterlife.app.data.db.ContentEntryEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** 条目 + 规则文件的内存视图，带索引 */
class EntriesData(
    val entriesFile: EntriesFile,
    val rulesFile: RulesFile,
) {
    val entries: List<EntryDto> = entriesFile.entries
    val sections: List<SectionDto> = entriesFile.sections

    /** removed（已下架）条目也保留在这里：历史任务与详情页要能按 id 读到快照 */
    val byId: Map<String, EntryDto> = entries.associateBy { it.id }

    /** 浏览索引不含下架条目：用户不应在浏览/搜索里看到它们（详情页走 byId 不受影响） */
    val bySection: Map<Int, List<EntryDto>> = entries.filter { !it.removed }.groupBy { it.sec }
    val bySectionKey: Map<String, List<EntryDto>> = entries.filter { !it.removed }.groupBy { it.secKey }
    val seedEntryIds: List<String> = rulesFile.seedEntryIds
    val rules: RulesFile = rulesFile
}

/**
 * 条目与规则的读取门面：条目内容来自 Room（首启由 [ContentBootstrap] 从 assets 播种），
 * 规则文件仍从 assets 读（规则随 APK 更新，不入库）。
 * 加载结果缓存一次，之后所有读取走内存；内容更新后由调用方调 [invalidate]。
 */
class EntryRepository(
    private val bootstrap: ContentBootstrap,
    private val contentDao: ContentDao,
    private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: EntriesData? = null

    private val loadMutex = Mutex()

    /** 首次调用会先确保内容已播种（IO 较重），之后走内存缓存 */
    suspend fun entriesData(): EntriesData {
        cache?.let { return it }
        return loadMutex.withLock {
            cache ?: load().also { cache = it }
        }
    }

    /** 内容更新（如同步 Worker 写入新快照）后清缓存，下次读取重新从 Room 组装 */
    fun invalidate() {
        cache = null
    }

    private suspend fun load(): EntriesData {
        bootstrap.ensureReady()
        val sections = contentDao.allSections().map {
            SectionDto(n = it.n, title = it.title, intro = it.intro, entries = it.entryCount, key = it.key)
        }
        val entries = contentDao.allEntries().map { it.toDto() }
        val rules = context.assets.open("relevance_rules.json").bufferedReader().use {
            json.decodeFromString(RulesFile.serializer(), it.readText())
        }
        return EntriesData(EntriesFile(sections = sections, entries = entries), rules)
    }
}

/** Room 行 → 运行模型：id 填稳定 key（见 EntryDto.id 约定） */
private fun ContentEntryEntity.toDto() = EntryDto(
    id = key, sec = sec, n = n, title = title,
    key = key, secKey = secKey, hash = hash, removed = removed,
    cost = cost, human = human, gain = gain, grade = grade, src = src, note = note,
    money = money, time = time, will = will, level = level, lens = lens,
    cs = cs, ratio = ratio, dispute = dispute, todo = todo, hay = hay,
)
