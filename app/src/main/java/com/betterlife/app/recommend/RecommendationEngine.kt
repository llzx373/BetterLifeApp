package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RuleDto
import com.betterlife.app.data.RulesFile

/** 一条被打分的推荐条目 */
data class ScoredEntry(
    val entry: EntryDto,
    val score: Int,
    val reasons: List<String>,
)

/**
 * 规则推荐引擎，纯 Kotlin、无 Android 依赖，可单测。
 *
 * 打分规则：
 * - 每条 entry 累加所有命中规则的 weight（boostEntryIds 直接命中；boostSections 对该节每条命中）；
 * - 被任何命中规则的 excludeEntryIds 命中的条目直接剔除；
 * - todo = true 的条目不进入推荐；
 * - 调用方传入的 excludedIds（本地 DONE / DISMISSED 状态）同样剔除。
 *
 * 排序：按 lens（口径）分组，组间顺序固定（死亡率、金钱、时间、自由，其余口径排在最后），
 * 组内按 score 降序 → ratio(极高>高>一般) → grade(A>B>C) → cs 升序，不跨口径混排。
 */
class RecommendationEngine {

    companion object {
        /** 口径展示顺序 */
        val LENS_ORDER = listOf("死亡率", "金钱", "时间", "自由")

        private val RATIO_ORDER = mapOf("极高" to 0, "高" to 1, "一般" to 2)
        private val GRADE_ORDER = mapOf("A" to 0, "B" to 1, "C" to 2)

        /** 组内排序比较器，DailyTaskPlanner 也复用 */
        val ENTRY_COMPARATOR: Comparator<EntryDto> =
            compareBy({ RATIO_ORDER[it.ratio] ?: 3 }, { GRADE_ORDER[it.grade] ?: 3 }, { it.cs }, { it.id })

        fun matchedRules(profile: Profile, rules: RulesFile): List<RuleDto> =
            rules.rules.filter { profile.matches(it.`when`) }
    }

    fun recommend(
        profile: Profile,
        entries: List<EntryDto>,
        rules: RulesFile,
        excludedIds: Set<String>,
        topN: Int = 5,
    ): LinkedHashMap<String, List<ScoredEntry>> {
        val matched = matchedRules(profile, rules)

        val scored = ArrayList<ScoredEntry>(entries.size)
        for (entry in entries) {
            if (entry.todo) continue
            if (entry.id in excludedIds) continue

            var excludedByRule = false
            var score = 0
            val reasons = ArrayList<String>()
            for (rule in matched) {
                if (entry.id in rule.excludeEntryIds) {
                    excludedByRule = true
                    break
                }
                val boosted = entry.id in rule.boostEntryIds || entry.sec in rule.boostSections
                if (boosted) {
                    score += rule.weight
                    if (rule.reason.isNotBlank()) reasons += rule.reason
                }
            }
            if (excludedByRule) continue
            scored += ScoredEntry(entry, score, reasons)
        }

        val comparator: Comparator<ScoredEntry> =
            compareByDescending<ScoredEntry> { it.score }
                .thenComparing({ it.entry }, ENTRY_COMPARATOR)

        val grouped = scored.groupBy { it.entry.lens }
        val result = LinkedHashMap<String, List<ScoredEntry>>()
        val orderedLenses = LENS_ORDER.filter { it in grouped } +
            grouped.keys.filter { it !in LENS_ORDER }.sorted()
        for (lens in orderedLenses) {
            result[lens] = grouped.getValue(lens).sortedWith(comparator).take(topN)
        }
        return result
    }
}
