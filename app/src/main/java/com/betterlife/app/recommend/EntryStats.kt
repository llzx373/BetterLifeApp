package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.db.EntryStateEntity

/** 一组条目的状态统计：待看 / 已完成 / 已忽略 */
data class EntryStats(
    val pending: Int,
    val done: Int,
    val dismissed: Int,
)

/**
 * 统计一组条目里 DONE / DISMISSED 的数量，其余算待看。
 * 状态之间正交（一条目可同时 DONE + DISMISSED），每条只落一个桶：DONE 优先于
 * DISMISSED（「做过」是比「不再推荐」更强的事实），保证 pending 不会变负。
 * 已加入计划（TODO/DAILY/每周习惯）但未完成的仍算待看——统计只看「结果」状态。
 */
fun computeEntryStats(
    entries: List<EntryDto>,
    statesByEntry: Map<String, Set<String>>,
): EntryStats {
    var done = 0
    var dismissed = 0
    for (entry in entries) {
        val states = statesByEntry[entry.id].orEmpty()
        if (EntryStateEntity.STATE_DONE in states) done++
        else if (EntryStateEntity.STATE_DISMISSED in states) dismissed++
    }
    return EntryStats(
        pending = entries.size - done - dismissed,
        done = done,
        dismissed = dismissed,
    )
}
