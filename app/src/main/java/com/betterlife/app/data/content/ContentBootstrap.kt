package com.betterlife.app.data.content

import android.content.Context
import androidx.room.withTransaction
import com.betterlife.app.data.EntriesFile
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.SectionDto
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.AppDatabase
import com.betterlife.app.data.db.ContentEntryEntity
import com.betterlife.app.data.db.ContentMetaEntity
import com.betterlife.app.data.db.ContentSectionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** 捆绑 entries.json 的解析缓存：首启播种、旧 id 迁移、备份导入的 id 改写共用同一份 */
internal object BundledContent {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: EntriesFile? = null

    fun entriesFile(context: Context): EntriesFile =
        cached ?: synchronized(this) {
            cached ?: context.assets.open("entries.json").bufferedReader().use {
                json.decodeFromString(EntriesFile.serializer(), it.readText())
            }.also { cached = it }
        }

    /** 旧版位置序号 id（形如 "02-01"）→ 稳定 key；assets 里 id 仍是 SS-NN（见 EntryDto.id 约定） */
    fun legacyIdToKey(context: Context): Map<String, String> =
        entriesFile(context).entries.associate { it.id to it.key }
}

/** 存条目 id 的五张用户表：id 迁移与 key 别名改写共用同一份清单 */
internal val ENTRY_ID_TABLES = listOf("tasks", "entry_states", "weekly_habits", "entry_notes", "streak_leaves")

/** 旧版位置序号 id（SS-NN）判定与改写：首启迁移与备份导入共用 */
internal object LegacyEntryId {
    val REGEX = Regex("""\d{2}-\d{2}""")

    /** 形如 "02-01" 且在映射里才改写；已是稳定 key / "custom:" 前缀 / 映射不上都原样保留 */
    fun remap(id: String, idToKey: Map<String, String>): String =
        if (REGEX.matches(id)) idToKey[id] ?: id else id
}

/** assets/patch 的条目 DTO → Room 行（同步 upsert 时 removed 一律复位为 false：在包里就是在架） */
internal fun EntryDto.toContentEntity(removed: Boolean, updatedAt: Long) = ContentEntryEntity(
    key = key, secKey = secKey, sec = sec, n = n, title = title,
    cost = cost, human = human, gain = gain, grade = grade,
    src = src, note = note, money = money, time = time,
    will = will, level = level, lens = lens, dispute = dispute,
    todo = todo, cs = cs, ratio = ratio, hay = hay, hash = hash,
    removed = removed, updatedAt = updatedAt,
)

internal fun SectionDto.toContentEntity() =
    ContentSectionEntity(key = key, n = n, title = title, intro = intro, entryCount = entries)

/**
 * 内容库首启引导：把捆绑 assets/entries.json 播进 Room，并把五张用户表里的
 * 条目 id 从旧版位置序号（SS-NN）一次性改写成稳定 key。
 *
 * 幂等：播种以 content_meta 是否有行为准，id 迁移以 DataStore 标记为准；
 * Mutex + ready 双检防止 TodayViewModel、小组件、Worker 并发触发重复执行。
 */
class ContentBootstrap(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
) {

    private val mutex = Mutex()

    @Volatile
    private var ready = false

    suspend fun ensureReady() {
        if (ready) return
        mutex.withLock {
            if (ready) return
            seedIfNeeded()
            migrateEntryIdsIfNeeded()
            ready = true
        }
    }

    /** content_meta 为空 = 从未播种：整库导入捆绑内容 */
    private suspend fun seedIfNeeded() {
        val dao = database.contentDao()
        if (dao.meta() != null) return
        val file = BundledContent.entriesFile(context)
        val now = System.currentTimeMillis()
        dao.applySnapshot(
            sections = file.sections.map { it.toContentEntity() },
            entries = file.entries.map { it.toContentEntity(removed = false, updatedAt = now) },
            removedKeys = emptyList(),
            meta = ContentMetaEntity(
                contentVersion = file.contentVersion,
                upstreamCommit = file.contentVersion,
                updatedAt = now,
            ),
        )
    }

    /**
     * 旧版本地数据里的 entryId 是位置序号 "SS-NN"，上游插入条目后会顺延失配；
     * 用捆绑 entries.json 的 id→key 映射改写五张表。"custom:" 前缀的自定义任务
     * 天然不会命中映射。UPDATE 走 SupportSQLiteDatabase，包在一个事务里。
     */
    private suspend fun migrateEntryIdsIfNeeded() {
        if (settingsStore.isContentIdMigrated()) return
        val idToKey = BundledContent.legacyIdToKey(context)
        if (idToKey.isNotEmpty()) {
            database.withTransaction {
                val db = database.openHelper.writableDatabase
                for (table in ENTRY_ID_TABLES) {
                    for ((oldId, key) in idToKey) {
                        db.execSQL("UPDATE $table SET entryId = ? WHERE entryId = ?", arrayOf(key, oldId))
                    }
                }
            }
        }
        settingsStore.setContentIdMigrated()
    }
}
