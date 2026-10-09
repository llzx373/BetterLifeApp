package com.betterlife.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSections(sections: List<ContentSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<ContentEntryEntity>)

    /** 上游下架的条目：行不删，只打 removed 标记（历史任务/详情页还要能读快照） */
    @Query("UPDATE content_entries SET removed = 1 WHERE `key` IN (:keys)")
    suspend fun markRemoved(keys: List<String>)

    @Query("SELECT * FROM content_entries ORDER BY sec, n")
    suspend fun allEntries(): List<ContentEntryEntity>

    /** 按 key 批量取行（含已下架的）：下架重挂时要读旧行的 title/secKey */
    @Query("SELECT * FROM content_entries WHERE `key` IN (:keys)")
    suspend fun entriesByKeys(keys: List<String>): List<ContentEntryEntity>

    @Query("SELECT * FROM content_sections ORDER BY n")
    suspend fun allSections(): List<ContentSectionEntity>

    @Query("SELECT * FROM content_meta WHERE id = 1")
    suspend fun meta(): ContentMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: ContentMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticles(articles: List<ContentArticleEntity>)

    @Query("SELECT * FROM content_articles")
    suspend fun allArticles(): List<ContentArticleEntity>

    /** 长文全量替换的收口:清掉不在新快照里的行(同步语义是整份替换,不做下架保留) */
    @Query("DELETE FROM content_articles WHERE `key` NOT IN (:keys)")
    suspend fun deleteArticlesNotIn(keys: List<String>)

    @Query("DELETE FROM content_articles")
    suspend fun clearArticles()

    /** 一整套内容快照原子落库：首启播种与后续同步 Worker 共用 */
    @Transaction
    suspend fun applySnapshot(
        sections: List<ContentSectionEntity>,
        entries: List<ContentEntryEntity>,
        removedKeys: List<String>,
        meta: ContentMetaEntity,
        articles: List<ContentArticleEntity> = emptyList(),
    ) {
        upsertSections(sections)
        upsertEntries(entries)
        if (removedKeys.isNotEmpty()) markRemoved(removedKeys)
        if (articles.isNotEmpty()) upsertArticles(articles)
        upsertMeta(meta)
    }
}
