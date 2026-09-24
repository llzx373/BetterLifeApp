package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryKeys

/**
 * CostMeter 的纯计算部分：把条目的成本/收益文字档位换算成可供绘制的格数。
 *
 * 纯 Kotlin、无 Android/Compose 依赖，可单测。
 * - 成本三项（钱/时间/毅力）各 0..2 格，对应设计文档 §3.3 的「3 格 × 3 项」；
 * - 收益档来自 level（大/中/小），映射为 0..3 格。
 */
object CostMeterModel {

    /** 单项成本最大格数 */
    const val MAX_COST_LEVEL = 2

    /** 收益最大格数 */
    const val MAX_GAIN_LEVEL = 3

    fun moneyLevel(entry: EntryDto): Int = when (entry.money) {
        EntryKeys.COST_MORE -> 2
        EntryKeys.COST_LESS -> 1
        else -> 0
    }

    fun timeLevel(entry: EntryDto): Int = when (entry.time) {
        EntryKeys.COST_MORE -> 2
        EntryKeys.COST_MID -> 1
        else -> 0
    }

    fun willLevel(entry: EntryDto): Int = when (entry.will) {
        EntryKeys.WILL_YES -> 2
        EntryKeys.WILL_SOME -> 1
        else -> 0
    }

    /** 三项成本格数之和（0..6） */
    fun costScore(entry: EntryDto): Int = moneyLevel(entry) + timeLevel(entry) + willLevel(entry)

    /** 收益格数：大=3、中=2、小=1、未标注=0 */
    fun gainLevel(entry: EntryDto): Int = when (entry.level) {
        EntryKeys.GAIN_BIG -> 3
        EntryKeys.GAIN_MID -> 2
        EntryKeys.GAIN_SMALL -> 1
        else -> 0
    }
}
