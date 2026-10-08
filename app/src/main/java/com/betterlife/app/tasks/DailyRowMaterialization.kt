package com.betterlife.app.tasks

/**
 * 设为/转为每日习惯时，是否立即为今天补一行 DAILY 任务。提成纯函数是为了脱离
 * Android 环境单测（TaskManager.ensureTodayDailyRow 的事务里用它做判断）。
 *
 * - 今天已有 DAILY 行（[todayRowCount] > 0）：今天已规划过，直接补行；
 * - 今天一行都没有但该条目是唯一的每日习惯（[otherDailyHabitCount] == 0）：
 *   也必须补行，否则它要等下次 ensureTodayTasks（重启/次日）才出现；
 * - 今天一行都没有且还有其他每日习惯：今天尚未规划，留给 ensureTodayTasks 统一落行，
 *   抢先插一行会让它误判「今天已规划」（countDailyByDate > 0 早退）而漏掉其他习惯。
 */
internal fun shouldMaterializeDailyRow(todayRowCount: Int, otherDailyHabitCount: Int): Boolean =
    todayRowCount > 0 || otherDailyHabitCount == 0
