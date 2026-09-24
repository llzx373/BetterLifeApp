package com.betterlife.app.data

import kotlinx.serialization.Serializable

/** 对应 assets/entries.json 的顶层结构 */
@Serializable
data class EntriesFile(
    val source: String = "",
    val sections: List<SectionDto> = emptyList(),
    val entries: List<EntryDto> = emptyList(),
)

@Serializable
data class SectionDto(
    val n: Int,
    val title: String,
    val intro: String = "",
    val entries: Int = 0,
)

@Serializable
data class EntryDto(
    val id: String,          // 形如 "02-01"：节号-条号
    val sec: Int,
    val n: Int,
    val title: String,
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

/** 对应 assets/relevance_rules.json */
@Serializable
data class RulesFile(
    val version: Int = 1,
    val dailyEntryIds: List<String> = emptyList(),
    val rules: List<RuleDto> = emptyList(),
)

@Serializable
data class RuleDto(
    val `when`: Map<String, List<String>> = emptyMap(), // {} = 所有人
    val boostEntryIds: List<String> = emptyList(),
    val boostSections: List<Int> = emptyList(),
    val excludeEntryIds: List<String> = emptyList(),
    val weight: Int = 0,
    val reason: String = "",
)
