package com.betterlife.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 内容库（上游书库）落 Room 的三张表。
 *
 * 主键是标题派生的稳定 key（sha1 前 12 hex，由 tools/build_content.py 生成），
 * 不是旧版位置序号 "SS-NN" —— 上游插入条目会让序号顺延，标题不变，所以序号不能当主键。
 * 用户表（tasks/entry_states 等）里的 entryId 在首启迁移后存的也是这里的 key。
 */

/** 一节（章）；entryCount 是该节条目数的冗余快照，目录页不再现算 */
@Entity(tableName = "content_sections")
data class ContentSectionEntity(
    @PrimaryKey val key: String,
    val n: Int,
    val title: String,
    val intro: String,
    val entryCount: Int,
)

/**
 * 一条内容。removed = 上游已下架：行保留（详情页/历史任务要能显示快照），
 * 但不再进入推荐与每日规划。hash 是内容字段哈希，同步时跳过未变化条目用。
 */
@Entity(
    tableName = "content_entries",
    indices = [Index("secKey"), Index("removed")],
)
data class ContentEntryEntity(
    @PrimaryKey val key: String,
    val secKey: String,
    val sec: Int,
    val n: Int,
    val title: String,
    val cost: String = "",
    val human: String = "",
    val gain: String = "",
    val grade: String = "",
    val src: String = "",
    val note: String = "",
    val money: String = "",
    val time: String = "",
    val will: String = "",
    val level: String = "",
    val lens: String = "",
    val dispute: Boolean = false,
    val todo: Boolean = false,
    val cs: Int = 0,
    val ratio: String = "",
    val hay: String = "",
    val hash: String = "",
    val removed: Boolean = false,
    val updatedAt: Long,
)

/** 内容库版本信息，单行（id 固定为 1）；meta 为空 = 尚未播种，ContentBootstrap 据此判断 */
@Entity(tableName = "content_meta")
data class ContentMetaEntity(
    @PrimaryKey val id: Int = 1,
    val contentVersion: String,
    val upstreamCommit: String,
    val updatedAt: Long,
)
