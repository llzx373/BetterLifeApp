package com.betterlife.app.tasks

/**
 * 番茄钟计时会话：纯逻辑，不依赖 Android，时钟可注入。
 *
 * 计时不靠 tick 计数 —— 开始时记下结束时刻 endAt，剩余时间永远按「endAt - 现在」算，
 * 切后台、息屏、进程被冻结再回来，时间都自动校准，不会少走。
 */
class TimerSession(
    val durationMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var endAtMillis: Long = clock() + durationMillis

    /** 非 null 表示已暂停，值是暂停那一刻冻结的剩余毫秒数 */
    private var pausedRemainingMillis: Long? = null

    val isPaused: Boolean get() = pausedRemainingMillis != null

    val isFinished: Boolean get() = !isPaused && remainingMillis() <= 0L

    fun remainingMillis(): Long =
        pausedRemainingMillis ?: (endAtMillis - clock()).coerceAtLeast(0L)

    /** 0f（刚开始）→ 1f（结束）；暂停时停在当前值 */
    fun progress(): Float =
        if (durationMillis <= 0L) 1f
        else (1f - remainingMillis().toFloat() / durationMillis).coerceIn(0f, 1f)

    fun pause() {
        if (pausedRemainingMillis == null) pausedRemainingMillis = remainingMillis()
    }

    /** 继续 = 以冻结的剩余时间为新时长重定结束时刻，暂停期间经过的时间不计入 */
    fun resume() {
        val paused = pausedRemainingMillis ?: return
        endAtMillis = clock() + paused
        pausedRemainingMillis = null
    }
}

/** 剩余毫秒格式化为 m:ss；秒向上取整，避免刚开始就显示「少了一秒」 */
internal fun formatRemainingMillis(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * 计时器页对任务层的全部依赖。TaskManager 实现它；
 * 抽成接口是为了单测能喂 fake（项目没有 mock 库，照 ProfileRepo 的先例）。
 */
interface TimerTaskGateway {
    /** 任务显示名（条目标题）；任务已删除时为 null，计时页退回通用文案 */
    suspend fun taskTitle(taskId: Long): String?

    /** 完成打卡；TaskManager 的实现内部会同步取消该任务的提醒 work */
    suspend fun completeTask(taskId: Long)
}
