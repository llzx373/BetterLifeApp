// N5：每日一条内容推送的选条与触发时刻。纯 Kotlin，不依赖 Android，可单测。
//
// 选条语义与推荐引擎一致：非 removed/todo/dispute、未 DONE 未 DISMISSED（excludedIds）、
// 未被命中规则 exclude；按档案命中规则累加 weight 打分，分高优先，并列时走引擎同款
// 稳定排序（ratio → grade → cs → id）。近 30 天已推过的排除（DataStore 滚动窗口，
// 见 SettingsStore 的 dailyContentPushedLines）。无可推时返回 null——当天不发，不硬凑。
//
// 推送正文用条目的 title + human（「说人话」字段），human 空白的条目不入候选：
// 没有可给人看的摘要，这条通知就谈不上「看完即走」。
package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RulesFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 每日一条触发时刻：08:07（避开整点/半点） */
const val DAILY_CONTENT_HOUR = 8
const val DAILY_CONTENT_MINUTE = 7

/** 已推去重窗口：同一条目 30 天内不重复推 */
const val DAILY_CONTENT_WINDOW_DAYS = 30L

/**
 * 下一次触发时刻（epoch 毫秒）：最近一个 08:07，已过点则顺延到明天。
 * 与 nextWeeklyReportMillis / TaskReminderTiming.nextTriggerMillis 同风格。
 */
fun nextDailyContentMillis(nowMillis: Long, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    var target = now.withHour(DAILY_CONTENT_HOUR).withMinute(DAILY_CONTENT_MINUTE)
        .withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return target.toInstant().toEpochMilli()
}

/**
 * 已推记录行格式："entryId,yyyy-MM-dd"（entryId 是 sha1 hex，不含逗号，按最后一个逗号切）。
 * 坏行返回 null，由调用方忽略——DataStore 里的局部状态坏了不应让推送整体失败。
 */
internal fun parsePushedLine(line: String): Pair<String, LocalDate>? {
    val idx = line.lastIndexOf(',')
    if (idx <= 0) return null
    val date = runCatching { LocalDate.parse(line.substring(idx + 1)) }.getOrNull() ?: return null
    return line.substring(0, idx) to date
}

/** 滚动窗口裁剪：只保留窗口内的有效记录行（坏行与过期行一并清掉，写回即完成滚动） */
fun prunePushedLines(
    lines: List<String>,
    today: LocalDate,
    windowDays: Long = DAILY_CONTENT_WINDOW_DAYS,
): List<String> = lines.filter { line ->
    parsePushedLine(line)?.let { (_, date) ->
        val days = ChronoUnit.DAYS.between(date, today)
        days in 0 until windowDays
    } == true
}

/** 近 [windowDays] 天已推过的条目 id 集合，选条时排除 */
fun pushedIdsInWindow(
    lines: List<String>,
    today: LocalDate,
    windowDays: Long = DAILY_CONTENT_WINDOW_DAYS,
): Set<String> = prunePushedLines(lines, today, windowDays)
    .mapTo(HashSet()) { it.substringBeforeLast(',') }

/**
 * 挑今天推的一条；无可推返回 null（当天不发）。
 *
 * @param excludedIds 本地 DONE / DISMISSED 状态的条目 id（EntryStateDao.excludedIds）
 * @param pushedIds 近 30 天已推过的条目 id（[pushedIdsInWindow] 的输出）
 */
fun pickDailyContent(
    profile: Profile,
    entries: List<EntryDto>,
    rules: RulesFile,
    excludedIds: Set<String>,
    pushedIds: Set<String>,
): EntryDto? {
    val matched = RecommendationEngine.matchedRules(profile, rules)
    val excludedByRule = matched.flatMapTo(HashSet()) { it.excludeEntryIds }

    var best: EntryDto? = null
    var bestScore = Int.MIN_VALUE
    for (entry in entries) {
        if (entry.removed || entry.todo || entry.dispute) continue
        if (entry.human.isBlank()) continue
        if (entry.id in excludedIds || entry.id in pushedIds || entry.id in excludedByRule) continue

        var score = 0
        for (rule in matched) {
            if (rule.weight <= 0) continue
            if (entry.id in rule.boostEntryIds || entry.secKey in rule.boostSections) {
                score += rule.weight
            }
        }
        // 分高优先；并列时走引擎同款稳定排序，保证同一天同一数据下结果确定可复现
        if (best == null || score > bestScore ||
            (score == bestScore && RecommendationEngine.ENTRY_COMPARATOR.compare(entry, best) < 0)
        ) {
            best = entry
            bestScore = score
        }
    }
    return best
}
