package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RulesFile
import java.time.LocalDate

/**
 * 每日任务规划器，纯 Kotlin、可单测。
 *
 * 从 rules.dailyEntryIds 中挑选今日任务来源，条件：
 * - 未被任何命中规则 exclude，也不在 excludedIds（本地 DONE/DISMISSED）中；
 * - todo = false；
 * - 与该 Profile 至少命中一条 weight > 0 规则的 boost
 *   （when = {} 的普惠规则天然命中所有人，无需特判）。
 *
 * 排序稳定（ratio → grade → cs → id），再按日期 hash 做确定性轮换：
 * 同一天、同一批候选结果必然相同；不同日期起点偏移不同，每天最多 maxDaily 条。
 */
class DailyTaskPlanner(private val maxDaily: Int = 3) {

    fun plan(
        date: LocalDate,
        profile: Profile,
        entries: List<EntryDto>,
        rules: RulesFile,
        excludedIds: Set<String>,
    ): List<EntryDto> {
        val byId = entries.associateBy { it.id }
        val matched = RecommendationEngine.matchedRules(profile, rules)

        val excludedByRule = matched.flatMapTo(HashSet()) { it.excludeEntryIds }
        val boosted = HashSet<String>()
        for (rule in matched) {
            if (rule.weight <= 0) continue
            boosted += rule.boostEntryIds
            for (sec in rule.boostSections) {
                entries.asSequence().filter { it.sec == sec }.forEach { boosted += it.id }
            }
        }

        val candidates = rules.dailyEntryIds
            .mapNotNull { byId[it] }
            .filter { !it.todo }
            .filter { it.id !in excludedIds && it.id !in excludedByRule }
            .filter { it.id in boosted }
            .sortedWith(RecommendationEngine.ENTRY_COMPARATOR)

        if (candidates.isEmpty()) return emptyList()

        // 确定性轮换：日期字符串 hash 做起点偏移
        val offset = Math.floorMod(date.toString().hashCode(), candidates.size)
        val rotated = candidates.drop(offset) + candidates.take(offset)
        return rotated.take(maxDaily)
    }
}
