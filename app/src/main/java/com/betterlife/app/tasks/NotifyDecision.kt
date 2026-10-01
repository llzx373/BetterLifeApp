// N2b：HC 自动核销后的通知决策。纯 Kotlin，不依赖 Android，可单测。
//
// 防打扰铁律（与每日汇总提醒互斥）：
// - 自动核销后若无未完成项，当天只发一条报喜，不再发「你还有 N 件事没做」式的汇总；
// - 若还有未完成项，报喜证据合并进每日汇总文案，不单独报喜；
// - 同日同事件不重复发（当日已发过报喜则由 DataStore 标记抑制，见 SettingsStore）。
package com.betterlife.app.tasks

/** 核销后的通知动作 */
enum class AutoNotifyType {
    /** 只发报喜（全部完成、今天还没报喜过） */
    PRAISE,

    /** 发每日汇总（本轮无核销走原文案；有核销且有未完成时把证据并进文案） */
    SUMMARY,

    /** 什么都不发（同日重发抑制） */
    NONE,
}

/**
 * 决定本轮自动核销之后发什么通知。
 *
 * @param completions 本轮核销的达标证据文案列表（如「今日步数 9234 ≥ 7000」），空 = 没核销
 * @param undoneCount 核销后今日仍未完成的任务数
 * @param praiseEnabled 设置页「自动打卡报喜」开关
 * @param praiseSentToday 今天是否已发过报喜（同日同事件不重复发）
 */
fun decideAutoNotify(
    completions: List<String>,
    undoneCount: Int,
    praiseEnabled: Boolean,
    praiseSentToday: Boolean,
): AutoNotifyType = when {
    // 本轮没核销到任何东西：维持每日汇总的原行为
    completions.isEmpty() -> AutoNotifyType.SUMMARY
    // 还有未完成项：不单独报喜，证据合并进汇总（由调用方拼文案）
    undoneCount > 0 -> AutoNotifyType.SUMMARY
    // 全部完成但开关关了：退回原有「全部完成」汇总
    !praiseEnabled -> AutoNotifyType.SUMMARY
    // 全部完成但今天已经报喜过：同日同事件不重复发
    praiseSentToday -> AutoNotifyType.NONE
    else -> AutoNotifyType.PRAISE
}
