package com.betterlife.app.data.content

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.betterlife.app.data.ArticlesFile
import com.betterlife.app.data.EntriesFile
import com.betterlife.app.data.ArticleDto
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.SectionDto
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.AppDatabase
import com.betterlife.app.data.db.ContentArticleEntity
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

    @Volatile
    private var cachedArticles: ArticlesFile? = null

    /** 捆绑长文(assets/articles.json);老 APK 没有该文件时按空处理,不挡播种主流程 */
    fun articlesFile(context: Context): ArticlesFile =
        cachedArticles ?: synchronized(this) {
            cachedArticles ?: runCatching {
                context.assets.open("articles.json").bufferedReader().use {
                    json.decodeFromString(ArticlesFile.serializer(), it.readText())
                }
            }.getOrDefault(ArticlesFile()).also { cachedArticles = it }
        }

    /** 旧版位置序号 id（形如 "02-01"）→ 稳定 key；assets 里 id 仍是 SS-NN（见 EntryDto.id 约定） */
    fun legacyIdToKey(context: Context): Map<String, String> =
        entriesFile(context).entries.associate { it.id to it.key }
}

/** 存条目 id 的五张用户表：id 迁移与 key 别名改写共用同一份清单 */
internal val ENTRY_ID_TABLES = listOf("tasks", "entry_states", "weekly_habits", "entry_notes", "streak_leaves")

/**
 * 冲突安全的 entryId 改写：先删掉会撞主键的旧 key 行，再 UPDATE 其余行。
 *
 * 直接 UPDATE 在主键含 entryId 的表上，当用户对新旧 key 都有数据时抛
 * SQLiteConstraintException（entry_states 主键 (entryId,state)、entry_notes /
 * weekly_habits 主键 entryId、streak_leaves 主键 (entryId,date)），同步/迁移路径
 * 每次走到同一行都炸 → WorkManager 永久退避、首启 ensureReady 永久失败。
 * 冲突语义：保留新 key 行（它属于当前在架 key），丢弃撞主键的旧 key 行。
 * tasks 主键是自增 taskId，entryId 只是索引列，无冲突。
 */
internal fun rewriteEntryId(db: SupportSQLiteDatabase, oldKey: String, newKey: String) {
    if (oldKey == newKey) return
    for (table in ENTRY_ID_TABLES) {
        when (table) {
            "tasks" -> Unit
            "entry_states" -> db.execSQL(
                "DELETE FROM entry_states WHERE entryId = ? AND EXISTS (" +
                    "SELECT 1 FROM entry_states n WHERE n.entryId = ? AND n.state = entry_states.state)",
                arrayOf(oldKey, newKey),
            )

            "streak_leaves" -> db.execSQL(
                "DELETE FROM streak_leaves WHERE entryId = ? AND EXISTS (" +
                    "SELECT 1 FROM streak_leaves n WHERE n.entryId = ? AND n.date = streak_leaves.date)",
                arrayOf(oldKey, newKey),
            )

            else -> db.execSQL(
                "DELETE FROM $table WHERE entryId = ? AND EXISTS " +
                    "(SELECT 1 FROM $table n WHERE n.entryId = ?)",
                arrayOf(oldKey, newKey),
            )
        }
        db.execSQL("UPDATE $table SET entryId = ? WHERE entryId = ?", arrayOf(newKey, oldKey))
    }
}

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

/** 长文 DTO → Room 行:secs 列表压成逗号分隔字符串(如 "8,13") */
internal fun ArticleDto.toContentEntity() = ContentArticleEntity(
    key = key, file = file, title = title,
    secs = secs.joinToString(","), hash = hash, body = body,
)

/**
 * 内容库首启引导：把捆绑 assets/entries.json(条目)与 assets/articles.json(长文)
 * 播进 Room，并把五张用户表里的条目 id 从旧版位置序号（SS-NN）一次性改写成稳定 key。
 *
 * 幂等：条目播种以 content_meta 是否有行为准,长文补播以 content_articles 是否为空为准
 * （v7 老库升级路径）,id 迁移以 DataStore 标记为准；
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
            seedArticlesIfNeeded()
            migrateEntryIdsIfNeeded()
            ready = true
        }
    }

    /** content_meta 为空 = 从未播种：整库导入捆绑内容（条目与长文同一事务） */
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
            articles = BundledContent.articlesFile(context).articles.map { it.toContentEntity() },
        )
    }

    /**
     * 长文补播：v7 及以前的老库 meta 已存在、seedIfNeeded 不会重播，
     * 而 v8 新建的 content_articles 是空表 —— 以「表为空」为准单独补播捆绑长文。
     * 首装路径已由 seedIfNeeded 在同事务播过,这里自然跳过,幂等。
     */
    private suspend fun seedArticlesIfNeeded() {
        val dao = database.contentDao()
        if (dao.allArticles().isNotEmpty()) return
        val articles = BundledContent.articlesFile(context).articles
        if (articles.isEmpty()) return
        dao.upsertArticles(articles.map { it.toContentEntity() })
    }

    /**
     * 旧版本地数据里的 entryId 是位置序号 "SS-NN"，上游插入条目后会顺延失配；
     * 用捆绑 entries.json 的 id→key 映射改写五张表。"custom:" 前缀的自定义任务
     * 天然不会命中映射。改写走冲突安全的 [rewriteEntryId]（新旧 key 都有行时不炸），
     * 包在一个事务里。
     */
    private suspend fun migrateEntryIdsIfNeeded() {
        if (settingsStore.isContentIdMigrated()) return
        val idToKey = BundledContent.legacyIdToKey(context)
        if (idToKey.isNotEmpty()) {
            database.withTransaction {
                val db = database.openHelper.writableDatabase
                for ((oldId, key) in idToKey) {
                    rewriteEntryId(db, oldId, key)
                }
            }
        }
        settingsStore.setContentIdMigrated()
    }
}
