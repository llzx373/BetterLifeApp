// 回顾式成就（B7）：里程碑判定是纯函数，只产出稳定的 key 与数字。
// 文案在 UI 层按 key 映射到 string 资源；这里不出现任何展示文案。
//
// 设计约束（§1 规则 4）：成就是回顾而非激励 —— 没有徽章墙、等级、排行榜，
// 也没有「你落后了」式的压力文案；达到了就安静地记录，没达到不提示。
package com.betterlife.app.stats

/** 里程碑的稳定 key：持久化（已庆祝集合）与 UI 文案映射都以它为准，不要改值 */
object AchievementKeys {
    const val STREAK_7 = "streak_7"
    const val STREAK_30 = "streak_30"
    const val STREAK_100 = "streak_100"
    const val TOTAL_10 = "total_10"
    const val TOTAL_50 = "total_50"
    const val TOTAL_100 = "total_100"
    const val TOTAL_500 = "total_500"
    const val TOTAL_1000 = "total_1000"
    const val PERFECT_WEEK = "perfect_week"
}

/** 一个已达成的里程碑；value 是达成时的实际数字（当前最长连签 / 累计次数 / 完美周数） */
data class Achievement(
    val key: String,
    val value: Int,
)

/** 成就判定的输入：从 [StatsResult] 提取的标量，避免成就层回依赖统计实现 */
data class AchievementInput(
    /** 历史最好连签（当前与历史最长取大） */
    val bestStreak: Int,
    /** 累计完成次数（全部 done 行） */
    val totalCompletions: Int,
    /** 已结束的 100% 完成周数 */
    val perfectWeeks: Int,
)

private val streakMilestones = listOf(
    7 to AchievementKeys.STREAK_7,
    30 to AchievementKeys.STREAK_30,
    100 to AchievementKeys.STREAK_100,
)

private val totalMilestones = listOf(
    10 to AchievementKeys.TOTAL_10,
    50 to AchievementKeys.TOTAL_50,
    100 to AchievementKeys.TOTAL_100,
    500 to AchievementKeys.TOTAL_500,
    1000 to AchievementKeys.TOTAL_1000,
)

/** 判定已达成的里程碑：阈值含边界（≥），未达成的不出现；顺序为连签 → 累计 → 完美周 */
fun evaluateAchievements(input: AchievementInput): List<Achievement> = buildList {
    streakMilestones.forEach { (threshold, key) ->
        if (input.bestStreak >= threshold) add(Achievement(key, input.bestStreak))
    }
    totalMilestones.forEach { (threshold, key) ->
        if (input.totalCompletions >= threshold) add(Achievement(key, input.totalCompletions))
    }
    if (input.perfectWeeks >= 1) add(Achievement(AchievementKeys.PERFECT_WEEK, input.perfectWeeks))
}
