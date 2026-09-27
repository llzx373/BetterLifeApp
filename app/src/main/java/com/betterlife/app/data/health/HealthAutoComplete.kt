// Health Connect 自动核销的纯规则判定。
//
// 规则来自 assets/health_rules.json（HealthRulesRepository 加载），这里只做纯计算：
// 某类健康数据为 null（没权限 / 读不到）时，该类型的规则一律不匹配——
// 宁可不自动核销，也不能误核销（产品要求）。
package com.betterlife.app.data.health

import kotlinx.serialization.Serializable

/** 对应 assets/health_rules.json 的顶层结构 */
@Serializable
data class HealthRulesFile(
    val version: Int = 1,
    val rules: List<HealthRule> = emptyList(),
)

/**
 * 一条可自动核销的映射：条目 [entryId] 在 [type] 类健康数据达到 [threshold] 时自动打卡。
 * threshold 的单位随类型：steps = 步，exercise = 分钟，sleep = 小时。
 */
@Serializable
data class HealthRule(
    val entryId: String,
    val type: String,
    val threshold: Double,
) {
    companion object {
        const val TYPE_STEPS = "steps"
        const val TYPE_EXERCISE = "exercise"
        const val TYPE_SLEEP = "sleep"
    }
}

/**
 * 评估哪些候选条目满足自动核销条件，返回命中的 entryId 集合。
 *
 * [candidateEntryIds] 是当天未完成 DAILY 任务的条目 id；不在候选里的规则直接忽略，
 * 保证「不在规则里的条目绝不核销、已完成的任务绝不重复核销」由调用方与这里共同守住。
 * 某类数据为 null 时该类型规则永不匹配。
 */
fun evaluateAutoCompletion(
    rules: List<HealthRule>,
    stepsToday: Long?,
    exerciseMinutesToday: Long?,
    sleepHoursLastNight: Double?,
    candidateEntryIds: Set<String>,
): Set<String> = rules
    .filter { it.entryId in candidateEntryIds }
    .filter { rule ->
        when (rule.type) {
            HealthRule.TYPE_STEPS -> stepsToday != null && stepsToday >= rule.threshold
            HealthRule.TYPE_EXERCISE ->
                exerciseMinutesToday != null && exerciseMinutesToday >= rule.threshold
            HealthRule.TYPE_SLEEP ->
                sleepHoursLastNight != null && sleepHoursLastNight >= rule.threshold
            else -> false // 未知类型保守不匹配
        }
    }
    .map { it.entryId }
    .toSet()

/** 完成备注里的证据文案，如「今日步数 9234 ≥ 7000」；数据缺失（不该发生）时退化为规则描述 */
fun healthEvidenceText(
    rule: HealthRule,
    stepsToday: Long?,
    exerciseMinutesToday: Long?,
    sleepHoursLastNight: Double?,
): String = when (rule.type) {
    HealthRule.TYPE_STEPS ->
        "今日步数 ${stepsToday ?: 0} ≥ ${rule.threshold.trimZero()}"
    HealthRule.TYPE_EXERCISE ->
        "今日运动 ${exerciseMinutesToday ?: 0} 分钟 ≥ ${rule.threshold.trimZero()} 分钟"
    HealthRule.TYPE_SLEEP ->
        "昨晚睡眠 ${(sleepHoursLastNight ?: 0.0).trimZero()} 小时 ≥ ${rule.threshold.trimZero()} 小时"
    else -> "达标（${rule.type} ≥ ${rule.threshold.trimZero()}）"
}

/** 7.0 → "7"，7.5 → "7.5"，备注里不出现多余的 .0 */
private fun Double.trimZero(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
