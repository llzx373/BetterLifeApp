package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RulesFile

/**
 * 每日习惯的首次播种挑选器，纯 Kotlin、可单测。
 *
 * 每日任务是纯用户自选机制（见 TaskManager.ensureTodayTasks）：只在用户还没有任何
 * STATE_DAILY 时，从 rules.seedEntryIds 种子池里按档案挑出 [count] 条示例写进去，
 * 之后增删全由用户决定，种子池不再参与每天的安排。
 *
 * 候选条件（与推荐引擎同一套语义）：
 * - 未被任何命中规则 exclude，也不在 excludedIds（本地 DONE/DISMISSED）中；
 * - todo = false 且未下架（removed）；
 * - 与该 Profile 至少命中一条 weight > 0 规则的 boost
 *   （when = {} 的普惠规则天然命中所有人，无需特判）。
 *
 * 排序稳定（ratio → grade → cs → id），取前 [count] 条；候选不足时有多少挑多少。
 */
class DailySeedPicker {

    fun pickSeeds(
        profile: Profile,
        entries: List<EntryDto>,
        rules: RulesFile,
        excludedIds: Set<String>,
        count: Int = SEED_COUNT,
    ): List<EntryDto> {
        val byId = entries.associateBy { it.id }
        val matched = RecommendationEngine.matchedRules(profile, rules)

        val excludedByRule = matched.flatMapTo(HashSet()) { it.excludeEntryIds }
        val boosted = HashSet<String>()
        for (rule in matched) {
            if (rule.weight <= 0) continue
            boosted += rule.boostEntryIds
            if (rule.boostSections.isNotEmpty()) {
                entries.asSequence().filter { it.secKey in rule.boostSections }.forEach { boosted += it.id }
            }
        }

        return rules.seedEntryIds
            .mapNotNull { byId[it] }
            .filter { !it.todo && !it.removed }
            .filter { it.id !in excludedIds && it.id !in excludedByRule }
            .filter { it.id in boosted }
            .sortedWith(RecommendationEngine.ENTRY_COMPARATOR)
            .take(count)
    }

    companion object {
        /** 首次播种的示例习惯条数 */
        const val SEED_COUNT = 3
    }
}
