// C5③：把周期报告折成一段中文摘要喂给模型。数字一律照抄原始统计，
// 语气约束（回顾式、不催促、不制造焦虑）同时写在这里和 SystemPrompts.STATS_INTERPRET。
// 本文件不在 ui 包内，中文字面量不受 UiNoChineseLiteralTest 约束。
package com.betterlife.app.stats

/**
 * @param periodLabel UI 层传入的区间名（本周 / 上周 / 本月 / 上月），来自 string 资源
 * @param bestStreak 全历史最好连签天数
 */
fun buildPeriodInterpretPrompt(
    report: PeriodReport,
    periodLabel: String,
    bestStreak: Int,
): String = buildString {
    appendLine("请回顾我${periodLabel}的打卡情况。下面是应用统计出的原始数据，请只基于这些数据说话，数字照抄，不要编造数据里没有的内容。")
    appendLine()
    appendLine("【${periodLabel}打卡数据】")
    appendLine("- 总完成次数：${report.totalDone} 次")
    if (report.plannedDaily > 0) {
        val pct = (report.completionRate!! * 100).toInt()
        appendLine("- 每日任务：完成 ${report.doneDaily}/${report.plannedDaily}，完成率 $pct%")
    } else {
        appendLine("- 每日任务：这段时间还没有每日任务记录")
    }
    if (report.doneByLens.isNotEmpty()) {
        val dist = report.doneByLens.entries
            .sortedByDescending { it.value }
            .joinToString("、") { (lens, count) -> "${lens.ifBlank { "其他" }} $count 次" }
        appendLine("- 各口径分布：$dist")
    }
    if (bestStreak > 0) appendLine("- 最长连续打卡：$bestStreak 天")
    if (report.notes.isNotEmpty()) {
        appendLine("- 打卡时的随手记：")
        report.notes.take(10).forEach { n ->
            appendLine("  · ${n.date}《${n.entryTitle}》：${n.note}")
        }
    }
    appendLine()
    append("请用 3 到 5 句话温和地回顾这段时间：只回顾、不催促、不评价好坏、不制造焦虑，不要给出「你落后了」之类的比较。")
}
