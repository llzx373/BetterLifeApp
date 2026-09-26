// 传感器路径的「今日步数」换算：TYPE_STEP_COUNTER 给的是开机累计值，
// 要拿今日步数就得记住「今天零点时计数器是多少」。
//
// 这条换算有两类边界，都收在这一个纯函数里，由单测锁住：
//  - 跨天：日期变了，以昨天最后一个读数作为今天的零点基线（夜里走的步数不丢）
//  - 重启：计数器清零（新读数 < 上次读数），基线归零，开机累计值近似当作今日步数
package com.betterlife.app.data.health

/** 持久化的基线状态：哪天记的、零点基线、最近一次开机累计读数 */
data class StepsBaseline(
    val date: String,
    val baseline: Long,
    val lastCounter: Long,
)

/**
 * 来了一个新计数器读数，算出新的基线状态与今日步数。
 *
 * [today] 为设备本地日期（yyyy-MM-dd）。返回 Pair(新基线, 今日步数)。
 */
internal fun computeTodaySteps(prev: StepsBaseline?, counter: Long, today: String): Pair<StepsBaseline, Long> {
    val baseline = when {
        // 首次使用：没有任何历史，从当前读数起算，今日步数从 0 开始
        prev == null -> counter
        // 重启清零：计数器比上次还小，基线归零（优先于跨天判断，否则减出负数）
        counter < prev.lastCounter -> 0L
        // 同一天：基线不动，步数 = 累计 - 基线
        prev.date == today -> prev.baseline
        // 跨天：以昨天最后读数为今天的零点基线
        else -> prev.lastCounter
    }
    return StepsBaseline(today, baseline, counter) to (counter - baseline).coerceAtLeast(0L)
}
