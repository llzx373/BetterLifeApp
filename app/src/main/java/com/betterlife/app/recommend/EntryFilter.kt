package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto

/**
 * 状态筛选的取值。状态不在 EntryDto 上（在 entry_states 表与计划成员里），
 * 所以这一维不能并入 [EntryDto.matchesFilter]，由调用方给 id 集合判定（见 [matchesStatus]）。
 * 待看的口径与 EntryStats 一致：没完成 = 待看（已加入计划但未完成的仍是待看）。
 */
enum class EntryStatusFilter { ALL, PENDING, DONE, PLANNED }

/**
 * 条目筛选：性价比 / 证据等级 / 口径三个维度各自可多选，外加单选的状态维度。
 * 维度之间取交集（AND），维度内取并集（OR）；某维度为空集表示该维度不约束。
 * 纯逻辑、可单测；UI 只负责渲染与回调。
 */
data class EntryFilter(
    val ratios: Set<String> = emptySet(),
    val grades: Set<String> = emptySet(),
    val lenses: Set<String> = emptySet(),
    val status: EntryStatusFilter = EntryStatusFilter.ALL,
) {
    val isEmpty: Boolean get() = ratios.isEmpty() && grades.isEmpty() && lenses.isEmpty() &&
        status == EntryStatusFilter.ALL

    /** 已选条件总数，筛选行上的激活角标用 */
    val activeCount: Int get() = ratios.size + grades.size + lenses.size +
        if (status == EntryStatusFilter.ALL) 0 else 1

    fun toggleRatio(value: String): EntryFilter = copy(ratios = ratios.toggle(value))
    fun toggleGrade(value: String): EntryFilter = copy(grades = grades.toggle(value))
    fun toggleLens(value: String): EntryFilter = copy(lenses = lenses.toggle(value))
    fun withStatus(value: EntryStatusFilter): EntryFilter = copy(status = value)

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value
}

/** 单条是否满足筛选：三维度 AND，维度内 OR */
fun EntryDto.matchesFilter(f: EntryFilter): Boolean =
    (f.ratios.isEmpty() || ratio in f.ratios) &&
        (f.grades.isEmpty() || grade in f.grades) &&
        (f.lenses.isEmpty() || lens in f.lenses)

/** 单条是否命中状态维度；[doneIds] / [plannedIds] 由调用方从 entry_states 与计划成员给 */
fun EntryDto.matchesStatus(
    f: EntryStatusFilter,
    doneIds: Set<String>,
    plannedIds: Set<String>,
): Boolean = when (f) {
    EntryStatusFilter.ALL -> true
    EntryStatusFilter.PENDING -> id !in doneIds
    EntryStatusFilter.DONE -> id in doneIds
    EntryStatusFilter.PLANNED -> id in plannedIds
}

fun List<EntryDto>.applyFilter(f: EntryFilter): List<EntryDto> =
    if (f.ratios.isEmpty() && f.grades.isEmpty() && f.lenses.isEmpty()) this
    else filter { it.matchesFilter(f) }

fun List<EntryDto>.applyStatusFilter(
    f: EntryStatusFilter,
    doneIds: Set<String>,
    plannedIds: Set<String>,
): List<EntryDto> =
    if (f == EntryStatusFilter.ALL) this else filter { it.matchesStatus(f, doneIds, plannedIds) }
