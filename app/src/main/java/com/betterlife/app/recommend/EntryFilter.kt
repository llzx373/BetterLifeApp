package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto

/**
 * 条目筛选：性价比 / 证据等级 / 口径三个维度各自可多选。
 * 维度之间取交集（AND），维度内取并集（OR）；某维度为空集表示该维度不约束。
 * 纯逻辑、可单测；UI 只负责渲染与回调。
 */
data class EntryFilter(
    val ratios: Set<String> = emptySet(),
    val grades: Set<String> = emptySet(),
    val lenses: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = ratios.isEmpty() && grades.isEmpty() && lenses.isEmpty()

    /** 已选条件总数，筛选行上的激活角标用 */
    val activeCount: Int get() = ratios.size + grades.size + lenses.size

    fun toggleRatio(value: String): EntryFilter = copy(ratios = ratios.toggle(value))
    fun toggleGrade(value: String): EntryFilter = copy(grades = grades.toggle(value))
    fun toggleLens(value: String): EntryFilter = copy(lenses = lenses.toggle(value))

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value
}

/** 单条是否满足筛选：三维度 AND，维度内 OR */
fun EntryDto.matchesFilter(f: EntryFilter): Boolean =
    (f.ratios.isEmpty() || ratio in f.ratios) &&
        (f.grades.isEmpty() || grade in f.grades) &&
        (f.lenses.isEmpty() || lens in f.lenses)

fun List<EntryDto>.applyFilter(f: EntryFilter): List<EntryDto> =
    if (f.isEmpty) this else filter { it.matchesFilter(f) }
