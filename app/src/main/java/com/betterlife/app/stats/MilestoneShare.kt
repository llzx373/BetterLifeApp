// 里程碑日签分享(N6):连签 7/30/100 天是用户想晒的时刻,日签图是自然传播。
// 克制红线:不弹窗诱导、不红点、不加「快来下载」水印/二维码——入口只有统计页
// 里程碑行的「分享」按钮,用户想晒才晒。
//
// 选摘与判定是纯函数,可脱机单测;绘制走 ShareCard 的离屏渲染管线(ui/stats/MilestoneShareCard)。
package com.betterlife.app.stats

import java.time.LocalDate

/** 日签分享卡的数据:连签天数 + 日期 + 一条书摘(标题/说人话,可能都没有则不渲染书摘段) */
data class MilestoneShareData(
    val streakDays: Int,
    val date: LocalDate,
    val quoteTitle: String?,
    val quoteHuman: String?,
)

/** 日签只覆盖连签里程碑:卡片内容是连签天数,累计次数/完美周不出日签 */
fun isStreakMilestone(key: String): Boolean =
    key == AchievementKeys.STREAK_7 ||
        key == AchievementKeys.STREAK_30 ||
        key == AchievementKeys.STREAK_100

/**
 * 日签书摘挑选:
 * - 候选优先取已 DONE 的条目(用户真做过的事,晒出来才有底气),没 DONE 过用种子池兜底;
 * - 同一天同一候选集稳定选中同一条(按日期落桶),不同天自然轮换;
 * - 两边都空(极端:库里没有可用条目)返回 null,卡片不渲染书摘段。
 *
 * [doneEntryIds] 调用方需先与条目库求交集(自定义任务 id 不在库里)。
 */
fun pickMilestoneQuote(
    doneEntryIds: Set<String>,
    seedEntryIds: List<String>,
    date: LocalDate,
): String? {
    val pool = if (doneEntryIds.isNotEmpty()) doneEntryIds.sorted() else seedEntryIds.sorted()
    if (pool.isEmpty()) return null
    return pool[Math.floorMod(date.toEpochDay(), pool.size.toLong()).toInt()]
}
