package com.betterlife.app.data.content

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.betterlife.app.data.ArticleDto
import com.betterlife.app.data.ArticlesFile
import com.betterlife.app.data.EntriesFile
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.SectionDto
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.db.AppDatabase
import com.betterlife.app.data.db.ContentMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** 内容发布包的清单（tools/build_content_pack.py 生成，随 GitHub Release content-latest 分发） */
@Serializable
data class ContentManifest(
    val contentVersion: String = "",
    val upstreamCommit: String = "",
    val entryCount: Int = 0,
    val entries: List<EntryHash> = emptyList(),
    val removed: List<String> = emptyList(),
    /** 旧key→新key 别名表,随包在线分发(老 APK 也能收到新别名);与 assets 内置表合并应用 */
    val aliases: Map<String, String> = emptyMap(),
    /** 长文清单(key→hash),应用后对照校验,与 entries 同策略;旧包没有此字段 */
    val articles: List<EntryHash> = emptyList(),
    /** 长文全量包 articles.json.gz;为空 = 本包不含长文更新,本地已有长文保留不动 */
    val articlesUrl: String? = null,
    val fullUrl: String = "",
    val patchUrl: String? = null,
    val patchBase: String? = null,
) {
    @Serializable
    data class EntryHash(val key: String = "", val hash: String = "")
}

/** 增量包：sections 是全量节表（新条目可能属于新节），updated 只含新增/变更条目;
 * articles 在包内是全量长文列表(长文只有 9 篇级别,不做增量),空列表 = 本包不动长文 */
@Serializable
private data class ContentPatch(
    val base: String = "",
    val version: String = "",
    val sections: List<SectionDto> = emptyList(),
    val updated: List<EntryDto> = emptyList(),
    val removedKeys: List<String> = emptyList(),
    val articles: List<ArticleDto> = emptyList(),
)

/** assets/key_aliases.json：上游改标题导致 key 变化时登记 旧key→新key */
@Serializable
private data class KeyAliases(val aliases: Map<String, String> = emptyMap())

sealed interface SyncResult {
    data object UpToDate : SyncResult
    data class Updated(val version: String) : SyncResult
    data class Failed(val reason: String) : SyncResult
}

/** 本轮同步走哪条路（纯函数，单测覆盖决策矩阵） */
internal enum class SyncPath { UP_TO_DATE, PATCH, FULL }

internal fun chooseSyncPath(localVersion: String?, manifest: ContentManifest): SyncPath = when {
    // 本地还没播种（meta 为空）时同步顺带完成首装，直接全量
    localVersion == null -> SyncPath.FULL
    localVersion == manifest.contentVersion -> SyncPath.UP_TO_DATE
    manifest.patchUrl != null && manifest.patchBase == localVersion -> SyncPath.PATCH
    else -> SyncPath.FULL
}

/**
 * 在架条目（key→hash）与清单的全量比对：一致返回 null，不一致返回原因文案。
 * 比对用 Map 相等：缺失、多出、hash 不同三种偏离一次全覆盖。
 */
internal fun contentMismatch(active: Map<String, String>, manifest: ContentManifest): String? {
    val expected = manifest.entries.associate { it.key to it.hash }
    return if (active == expected) {
        null
    } else {
        "内容校验失败：本地在架 ${active.size} 条 / 清单 ${expected.size} 条"
    }
}

/** 长文的 key→hash 全量比对,与 [contentMismatch] 同策略(缺失/多出/hash 不同一次全覆盖) */
internal fun articlesMismatch(active: Map<String, String>, manifest: ContentManifest): String? {
    val expected = manifest.articles.associate { it.key to it.hash }
    return if (active == expected) {
        null
    } else {
        "长文校验失败：本地 ${active.size} 篇 / 清单 ${expected.size} 篇"
    }
}

/**
 * 运行时内容同步：从 GitHub Releases（滚动 tag content-latest）拉 manifest，
 * 版本相同即 UpToDate；本地版本等于 patchBase 走增量包，否则下全量 gzip 包。
 *
 * 写库一律在单事务里（key 别名改写与内容应用同事务，校验不过整体回滚，
 * 用户数据不会悬空指向不存在的新 key），收尾用 manifest.entries 对 content_entries
 * 做 key→hash 全量校验（600 条级别很便宜）：增量校验不过回滚后自动改走全量，
 * 全量校验不过则整轮放弃（返回 Failed，WorkManager 指数退避重试）。
 * 下架条目里有用户数据的，先在事务内按标题相似度保守重挂（见 reattachUserData）。
 * 长文随包同步：全量路下载 manifest.articlesUrl 指向的 articles.json.gz、增量路用
 * patch.articles 全量列表,都是整份替换(长文无下架保留语义);清单带 articlesUrl 时
 * 按 manifest.articles 做 key→hash 校验,与条目校验同事务同回滚;旧包无该字段则跳过,
 * 本地已有长文不清空。
 * 任何网络/解析失败都不写库，返回 [SyncResult.Failed]。
 */
class ContentSyncRepository(
    private val database: AppDatabase,
    private val entryRepository: EntryRepository,
    private val bootstrap: ContentBootstrap,
    private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** manifest 很小，10s 超时；全量包下载单独放宽读超时 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val fullDownloadClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 设置页展示用：当前内容版本与上次同步时间；先确保已播种再读 meta */
    suspend fun currentMeta(): ContentMetaEntity? {
        bootstrap.ensureReady()
        return database.contentDao().meta()
    }

    suspend fun syncOnce(): SyncResult = withContext(Dispatchers.IO) {
        try {
            bootstrap.ensureReady()
            val manifest = fetchJson(MANIFEST_URL, ContentManifest.serializer(), client)
            val local = database.contentDao().meta()
            val path = chooseSyncPath(local?.contentVersion, manifest)
            if (path == SyncPath.UP_TO_DATE) {
                return@withContext SyncResult.UpToDate
            }
            val aliases = resolveKeyAliases(manifest.aliases)
            val now = System.currentTimeMillis()
            if (path == SyncPath.PATCH) {
                try {
                    applyPatch(manifest, manifest.patchUrl!!, now, aliases)
                } catch (e: VerificationException) {
                    // 增量结果对不上清单：本地内容可能已偏离基线，回滚后改走全量
                    applyFull(manifest, now, aliases)
                }
            } else {
                applyFull(manifest, now, aliases)
            }
            entryRepository.invalidate()
            SyncResult.Updated(manifest.contentVersion)
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * key 别名改写必须在应用新内容之前做：旧 key 下架后用户数据会脱钩，
     * 先按别名表把五张用户表的 entryId 挂到新 key 上。
     * 别名有两个来源：manifest 在线下发（[remoteAliases]，老 APK 也能收到新别名）
     * 和 assets 内置表（APK 断网首装时的兜底）；冲突时以在线表为准。两表皆空返回空表。
     *
     * 这里只做解析合并；改写发生在 applyPatch/applyFull 的事务内部（见两者的 aliases 参数）——
     * 之前独立事务先落库，后续 apply 失败回滚会让用户数据悬空指向不存在的新 key。
     */
    private fun resolveKeyAliases(remoteAliases: Map<String, String>): Map<String, String> {
        val assetAliases = runCatching {
            context.assets.open("key_aliases.json").bufferedReader().use {
                json.decodeFromString(KeyAliases.serializer(), it.readText())
            }.aliases
        }.getOrDefault(emptyMap())
        return assetAliases + remoteAliases
    }

    private suspend fun applyPatch(manifest: ContentManifest, patchUrl: String, now: Long, aliases: Map<String, String>) {
        val patch = fetchJson(patchUrl, ContentPatch.serializer(), client)
        database.withTransaction {
            applyAliases(aliases)
            val dao = database.contentDao()
            dao.upsertSections(patch.sections.map { it.toContentEntity() })
            dao.upsertEntries(patch.updated.map { it.toContentEntity(removed = false, updatedAt = now) })
            if (patch.removedKeys.isNotEmpty()) {
                dao.markRemoved(patch.removedKeys)
                reattachUserData(patch.removedKeys)
            }
            // patch.articles 是全量长文列表:整份替换(空 = 本包不动长文)
            if (patch.articles.isNotEmpty()) replaceArticles(patch.articles)
            dao.upsertMeta(
                ContentMetaEntity(
                    contentVersion = patch.version,
                    upstreamCommit = manifest.upstreamCommit,
                    updatedAt = now,
                )
            )
            verifyAgainst(manifest)
        }
    }

    private suspend fun applyFull(manifest: ContentManifest, now: Long, aliases: Map<String, String>) {
        val file = fetchGzipJson(manifest.fullUrl, EntriesFile.serializer())
        // 清单带 articlesUrl 才下载长文包;旧包没有该字段时跳过,本地已有长文保留不清空
        val articles = manifest.articlesUrl
            ?.let { fetchGzipJson(it, ArticlesFile.serializer()).articles }
        database.withTransaction {
            applyAliases(aliases)
            val dao = database.contentDao()
            dao.upsertSections(file.sections.map { it.toContentEntity() })
            dao.upsertEntries(file.entries.map { it.toContentEntity(removed = false, updatedAt = now) })
            // DB 有而清单没有的 key = 上游已下架：行保留打 removed 标记，不删（详情/历史要读快照）
            val manifestKeys = manifest.entries.mapTo(HashSet()) { it.key }
            val stale = dao.allEntries().map { it.key }.filter { it !in manifestKeys }
            if (stale.isNotEmpty()) {
                dao.markRemoved(stale)
                reattachUserData(stale)
            }
            if (articles != null) replaceArticles(articles)
            dao.upsertMeta(
                ContentMetaEntity(
                    contentVersion = manifest.contentVersion,
                    upstreamCommit = manifest.upstreamCommit,
                    updatedAt = now,
                )
            )
            verifyAgainst(manifest)
        }
    }

    /**
     * 长文整份替换(须在 applyPatch/applyFull 的事务内调用):upsert 新快照 +
     * 删掉不在快照里的行。与条目不同,长文没有「下架保留」语义——它不被用户数据引用。
     */
    private suspend fun replaceArticles(articles: List<ArticleDto>) {
        val dao = database.contentDao()
        if (articles.isEmpty()) {
            dao.clearArticles()
        } else {
            dao.upsertArticles(articles.map { it.toContentEntity() })
            dao.deleteArticlesNotIn(articles.map { it.key })
        }
    }

    /**
     * 事务内的别名改写：必须在 applyPatch/applyFull 的 withTransaction 里调用，
     * 校验不过时随内容一起回滚。改写冲突安全（新旧 key 都有行时不抛约束异常）。
     * 增量校验失败改走全量时会再次调用——幂等（旧 key 行已不存在，等于空转）。
     */
    private fun applyAliases(aliases: Map<String, String>) {
        if (aliases.isEmpty()) return
        val db = database.openHelper.writableDatabase
        for ((oldKey, newKey) in aliases) {
            rewriteEntryId(db, oldKey, newKey)
        }
    }

    /** 在架条目（removed=false）的 key→hash 必须与清单完全一致，否则抛 [VerificationException] 回滚;
     * 清单带 articlesUrl 时长文同样校验(旧包无此字段则跳过长文校验) */
    private suspend fun verifyAgainst(manifest: ContentManifest) {
        val dao = database.contentDao()
        val active = dao.allEntries()
            .filter { !it.removed }
            .associate { it.key to it.hash }
        contentMismatch(active, manifest)?.let { throw VerificationException(it) }
        if (manifest.articlesUrl != null) {
            val activeArticles = dao.allArticles().associate { it.key to it.hash }
            articlesMismatch(activeArticles, manifest)?.let { throw VerificationException(it) }
        }
    }

    /**
     * 下架重挂（运行时兜底）：构建期的 0.8 阈值没接住、也没人登记别名时，
     * 被删条目若还挂着用户数据（任务/收藏/笔记等），在同节在架条目里按标题
     * bigram 相似度找唯一候选（阈值 0.85 + 分差 0.05，比构建期更保守），
     * 命中就把五张用户表的 entryId 改写到新 key（冲突安全：目标 key 已有同主键行时
     * 保留目标行）。被重挂的旧行保持 removed，详情/历史仍读旧快照。
     * 必须在 markRemoved 之后、同一事务内调用。
     */
    private suspend fun reattachUserData(removedKeys: List<String>) {
        val dao = database.contentDao()
        val removedRows = dao.entriesByKeys(removedKeys)
        if (removedRows.isEmpty()) return
        val db = database.openHelper.writableDatabase
        val active = dao.allEntries().filter { !it.removed }
        for (row in removedRows) {
            if (!hasUserData(db, row.key)) continue
            val candidates = active.filter { it.secKey == row.secKey }.map { it.key to it.title }
            val target = TitleSimilarity.pickCandidate(row.title, candidates, REATTACH_THRESHOLD)
                ?: continue
            rewriteEntryId(db, row.key, target)
            Log.i(TAG, "下架条目重挂: ${row.key}《${row.title}》→ $target")
        }
    }

    /** 五张用户表任一引用该条目 key 即算「有用户数据」 */
    private fun hasUserData(db: androidx.sqlite.db.SupportSQLiteDatabase, key: String): Boolean =
        ENTRY_ID_TABLES.any { table ->
            db.query("SELECT EXISTS(SELECT 1 FROM $table WHERE entryId = ?)", arrayOf(key))
                .use { it.moveToFirst() && it.getInt(0) == 1 }
        }

    private fun <T> fetchJson(
        url: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        client: OkHttpClient,
    ): T {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("GET $url → HTTP ${it.code}")
            val body = it.body.string()
            return json.decodeFromString(serializer, body)
        }
    }

    private fun <T> fetchGzipJson(
        url: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T {
        val response = fullDownloadClient.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("GET $url → HTTP ${it.code}")
            val text = GZIPInputStream(it.body.byteStream()).bufferedReader().use { r -> r.readText() }
            return json.decodeFromString(serializer, text)
        }
    }

    /** 增量校验失败专用：syncOnce 只对它兜底走全量，其他异常直接 Failed */
    private class VerificationException(message: String) : Exception(message)

    companion object {
        const val MANIFEST_URL =
            "https://github.com/llzx373/BetterLifeApp/releases/download/content-latest/manifest.json"

        /** 下架重挂的相似度阈值：比构建期继承（0.8）更保守，宁缺毋滥 */
        private const val REATTACH_THRESHOLD = 0.85

        private const val TAG = "ContentSync"
    }
}
