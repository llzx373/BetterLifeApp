// 预览用的假数据。
//
// 只服务预览：构造形状正确的对象，不碰数据库、资产与网络。
// 文案在这里是中文字面量 —— 截图源集不参与构建产物，也不在
// UiNoChineseLiteralTest 的扫描范围内。
package com.betterlife.app.ui

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.recommend.ScoredEntry

internal fun fakeEntry(
    id: String,
    title: String,
    sec: Int = 1,
    n: Int = 1,
    money: String = EntryKeys.COST_LESS,
    time: String = EntryKeys.COST_LESS,
    will: String = EntryKeys.WILL_SOME,
    level: String = EntryKeys.GAIN_BIG,
    lens: String = EntryKeys.LENS_MORTALITY,
    ratio: String = EntryKeys.RATIO_VERY_HIGH,
    grade: String = "A",
    cost: String = "",
    human: String = "",
    gain: String = "",
    src: String = "",
    note: String = "",
) = EntryDto(
    id = id,
    sec = sec,
    n = n,
    title = title,
    cost = cost,
    human = human,
    gain = gain,
    grade = grade,
    src = src,
    note = note,
    money = money,
    time = time,
    will = will,
    level = level,
    lens = lens,
    ratio = ratio,
)

internal fun fakeTask(
    taskId: Long,
    entryId: String,
    type: String = TaskEntity.TYPE_DAILY,
    done: Boolean = false,
    date: String? = "2026-09-24",
) = TaskEntity(
    taskId = taskId,
    entryId = entryId,
    type = type,
    date = date,
    done = done,
    createdAt = 0L,
)

internal fun fakeScored(entry: EntryDto, score: Int = 100) = ScoredEntry(
    entry = entry,
    score = score,
    reasons = emptyList(),
)
