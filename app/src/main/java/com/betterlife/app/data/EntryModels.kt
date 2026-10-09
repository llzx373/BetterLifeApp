package com.betterlife.app.data

import kotlinx.serialization.Serializable

/** 对应 assets/entries.json 的顶层结构；contentVersion 同时是上游 commit 短哈希 */
@Serializable
data class EntriesFile(
    val source: String = "",
    val contentVersion: String = "",
    val generatedAt: String = "",
    val sections: List<SectionDto> = emptyList(),
    val entries: List<EntryDto> = emptyList(),
)

@Serializable
data class SectionDto(
    val n: Int,
    val title: String,
    val intro: String = "",
    val entries: Int = 0,
    /** 节标题派生的稳定 key */
    val key: String = "",
)

@Serializable
data class EntryDto(
    /**
     * 运行时即稳定 key（从 Room 组装时 id = key）；
     * 只有 bootstrap/备份迁移直接反序列化 assets 时，id 才是旧版位置序号 "SS-NN"。
     */
    val id: String,
    val sec: Int,
    val n: Int,
    val title: String,
    /** 标题派生的稳定 key（sha1 前 12 hex） */
    val key: String = "",
    val secKey: String = "",
    /** 内容字段哈希，同步时判断条目是否变化用 */
    val hash: String = "",
    /** 上游已下架：详情页仍可显示快照，但不再进入推荐/规划 */
    val removed: Boolean = false,
    val cost: String = "",
    val human: String = "",  // 说人话
    val gain: String = "",   // 收益
    val grade: String = "",  // A|B|C
    val src: String = "",
    val note: String = "",   // 备注（含禁忌人群）
    val money: String = "",  // 0|少|多
    val time: String = "",   // 少|中|多
    val will: String = "",   // 否|些|是
    val level: String = "",  // 大|中|小
    val lens: String = "",   // 死亡率|金钱|时间|自由
    val cs: Int = 0,
    val ratio: String = "",  // 极高|高|一般
    val dispute: Boolean = false,
    val todo: Boolean = false,
    val hay: String = "",    // 小写拼接的检索字段
)

/** 对应 assets/articles.json 的顶层结构(长文,随内容包一同发布) */
@Serializable
data class ArticlesFile(
    val contentVersion: String = "",
    val articles: List<ArticleDto> = emptyList(),
)

/** 一篇长文(上游 docs/ 下的深度文章);body 是 markdown 原文,不含 H1 */
@Serializable
data class ArticleDto(
    /** 文件名派生的稳定 key,与条目 key 同一套生成规则 */
    val key: String,
    /** 上游 docs/ 下的文件名(如 "家庭应急装备清单.md");备注/引言里的 docs 链接靠它对上 */
    val file: String,
    val title: String,
    /** 与本文相关的节号(一篇可挂多节) */
    val secs: List<Int> = emptyList(),
    /** 内容哈希,同步校验用(与条目同策略) */
    val hash: String = "",
    val body: String = "",
)

/** 对应 assets/relevance_rules.json */
@Serializable
data class RulesFile(
    val version: Int = 1,
    /** 每日习惯种子池：用户还没有自选习惯时按档案挑 3 条示例播种，之后不再参与每天安排 */
    val seedEntryIds: List<String> = emptyList(),
    val rules: List<RuleDto> = emptyList(),
)

@Serializable
data class RuleDto(
    val `when`: Map<String, List<String>> = emptyMap(), // {} = 所有人
    val boostEntryIds: List<String> = emptyList(),
    /** 节 key 列表（v2 起；v1 是节号 Int，已随内容入 Room 废弃） */
    val boostSections: List<String> = emptyList(),
    val excludeEntryIds: List<String> = emptyList(),
    val weight: Int = 0,
    val reason: String = "",
)
