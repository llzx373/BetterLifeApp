package com.betterlife.app.tasks

import kotlinx.coroutines.flow.Flow

/**
 * 聊天页「答案来源一键加入计划」（N3a）对任务层的全部依赖。TaskManager 实现它；
 * 抽成接口是为了单测能喂 fake（照 TimerTaskGateway / ProfileRepo 的先例）。
 */
interface ChatPlanGateway {
    /** 已加入任一计划（一次性待办/每日习惯/每周习惯）的条目 id，「已加入」态徽标用 */
    fun plannedEntryIdsFlow(): Flow<Set<String>>

    /** 加入一次性待办（幂等：已有未完成待办时不重复建行） */
    suspend fun addToTodo(entryId: String)

    /** 设为每日习惯：写 STATE_DAILY，今天已有每日安排则补一行，次日由 ensureTodayTasks 幂等落行 */
    suspend fun addDailyHabit(entryId: String)
}
