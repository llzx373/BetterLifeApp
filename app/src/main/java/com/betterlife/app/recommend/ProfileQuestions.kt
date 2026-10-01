package com.betterlife.app.recommend

import com.betterlife.app.data.Profile

/**
 * 渐进式档案收集（N1「每天一问」）的纯逻辑，无 Android 依赖，可单测。
 *
 * 问题状态存在 SettingsStore（已答集合 / 暂缓集合 / 当日已问日期），
 * 这里只做「今天该问哪一题」的挑选与「按已答集合重建有效档案」。
 */
object ProfileQuestions {

    /**
     * 提问顺序，按对推荐命中率的贡献排（relevance_rules.json 里 when 条件的字段出现次数，
     * 2026-10-01 统计：ageRange 10、chronic 7、goals 6、children/pregnant 4、
     * smoking/alcohol/exercise/hasElderly 3 ……），需求定调的前缀是
     * 年龄段 → 吸烟 → 饮酒 → 运动睡眠，其余按贡献降序。
     * 覆盖 Profile.fieldValues 的全部 18 个字段。
     */
    val ORDER: List<String> = listOf(
        "ageRange", "smoking", "alcohol", "exercise", "sleepShort",
        "chronic", "goals", "children", "pregnant", "hasElderly",
        "betelNut", "sugaryDrinks", "occupation", "housing", "gender",
        "secondhandSmoke", "financialStress", "planningAbroad",
    )

    /** 多选字段：选项 chip 需要「确定」确认；其余字段点了即答 */
    val MULTI_FIELDS: Set<String> = setOf("chronic", "goals")

    /**
     * 今天该问哪个字段；null = 今天不问。
     *
     * - [askedToday] 为 true（今天已答过或已「暂不回答」）→ 不问，一天最多一条；
     * - 已答字段不再问；
     * - 「暂不回答」过的字段排到最后：所有没问过的字段问完一轮后才轮到它们。
     */
    fun nextField(answered: Set<String>, deferred: Set<String>, askedToday: Boolean): String? {
        if (askedToday) return null
        val remaining = ORDER.filter { it !in answered }
        return remaining.firstOrNull { it !in deferred } ?: remaining.firstOrNull()
    }

    /** 档案是否已填完：已答集合覆盖全部提问字段（完整向导保存时会把全部字段标为已答） */
    fun isComplete(answered: Set<String>): Boolean = answered.containsAll(ORDER)

    /**
     * 重建引擎/播种用的有效档案：
     * 从未保存过档案 → 空档案（只有 {} 普惠规则命中）；已答覆盖全部字段 → 完整档案；
     * 否则只认已答字段，未答字段的取值是默认值、不得参与规则匹配。
     */
    fun effectiveProfile(profile: Profile?, answered: Set<String>): Profile = when {
        profile == null -> Profile.EMPTY
        isComplete(answered) -> profile
        else -> profile.copy(knownFields = answered)
    }
}
