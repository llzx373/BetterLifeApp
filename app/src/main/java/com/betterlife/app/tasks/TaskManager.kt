package com.betterlife.app.tasks

import androidx.room.withTransaction
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.data.Profile
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.AppDatabase
import com.betterlife.app.data.db.CustomEntryDao
import com.betterlife.app.data.db.CustomEntryEntity
import com.betterlife.app.data.db.EntryStateDao
import com.betterlife.app.data.db.EntryStateEntity
import com.betterlife.app.data.db.StreakLeaveDao
import com.betterlife.app.data.db.StreakLeaveEntity
import com.betterlife.app.data.db.TaskDao
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.data.db.WeeklyHabitDao
import com.betterlife.app.data.db.WeeklyHabitEntity
import com.betterlife.app.data.health.HealthConnectMetrics
import com.betterlife.app.data.health.HealthConnectStatus
import com.betterlife.app.data.health.HealthRule
import com.betterlife.app.data.health.HealthRulesRepository
import com.betterlife.app.data.health.evaluateAutoCompletion
import com.betterlife.app.data.health.healthEvidenceText
import com.betterlife.app.recommend.DailySeedPicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * 任务协调层：把用户自选的每日习惯（STATE_DAILY）落成当天的任务行，并处理打卡、待办与连续天数。
 *
 * 同时持有 [ReminderScheduler]：凡是动到任务生命周期（新建/打卡/删除/恢复/改提醒时间）
 * 的地方都在这里同步重排或取消对应的单任务提醒 work，ViewModel 不需要各自编排。
 */
class TaskManager(
    private val database: AppDatabase,
    private val taskDao: TaskDao,
    private val entryStateDao: EntryStateDao,
    private val weeklyHabitDao: WeeklyHabitDao,
    private val streakLeaveDao: StreakLeaveDao,
    private val customEntryDao: CustomEntryDao,
    private val seedPicker: DailySeedPicker,
    private val entryRepository: EntryRepository,
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler,
    // B1 自动核销依赖：默认 null，缺了任何一个都安静地不启用自动核销
    private val healthConnect: HealthConnectMetrics? = null,
    private val healthRulesRepository: HealthRulesRepository? = null,
) : TimerTaskGateway, ChatPlanGateway {

    companion object {
        private const val CUSTOM_ID_PREFIX = "custom:"

        /** Health Connect 自动核销写入的 doneBy 标记，与手动打卡（"manual"）区分 */
        const val DONE_BY_AUTO_HC = "auto:hc"

        /** 自定义任务 id 统一 "custom:<UUID>" 前缀，与条目库 id（如 "02-01"）不会冲突 */
        fun isCustomEntryId(entryId: String): Boolean = entryId.startsWith(CUSTOM_ID_PREFIX)
    }

    /**
     * 「今天」是可刷新的状态而不是一次性求值：进程跨夜存活时，由
     * [refreshToday]（MainActivity.onResume）推进，下游 Flow 随之切到新日期的查询，
     * 否则 UI 会永远停在 Flow 创建那天的任务上。
     */
    private val today = MutableStateFlow(LocalDate.now())

    fun refreshToday() {
        today.value = LocalDate.now()
    }

    fun todayFlow(): StateFlow<LocalDate> = today.asStateFlow()

    /**
     * 当天没有任何 DAILY 任务时，把用户自选的每日习惯（STATE_DAILY）落成今天的任务行；
     * 幂等，可反复调用。
     *
     * 每日任务是纯用户自选机制：用户还一条习惯都没有且从未播种过时（新用户，或老用户
     * 升级到自选机制后的第一天），先由 [DailySeedPicker] 按档案从种子池挑几条示例
     * 写入 STATE_DAILY（可删可改），之后增删全由用户决定，系统不再补位。
     */
    suspend fun ensureTodayTasks(profile: Profile, date: LocalDate = LocalDate.now()) {
        val dateStr = date.toString()
        val data = entryRepository.entriesData()
        // 「当天行是否已存在 + 首次播种 + 插入」包进一个事务，靠 Room 单库事务串行化
        // 消除双插窗口（TodayViewModel、小组件、DailyReminderWorker 都可能同时触发）。
        val toSchedule = database.withTransaction {
            if (taskDao.countDailyByDate(dateStr) > 0) return@withTransaction emptyList()
            val excluded = entryStateDao.excludedIds().toSet()
            val customIds = customEntryDao.all().mapTo(HashSet()) { it.entryId }
            // 自选每日习惯只按 DISMISSED 过滤，不按 DONE：打卡完成会写 DONE（见 completeTask），
            // 若把 DONE 也算排除，用户自选的习惯打一次卡就会从每日任务里消失。
            val dismissed = entryStateDao.entryIdsByState(EntryStateEntity.STATE_DISMISSED).toSet()
            var dailyIds = entryStateDao.entryIdsByState(EntryStateEntity.STATE_DAILY)
            // 首次播种：一条自选习惯都没有且没播种过时才挑示例；标记无论挑到几条都写，
            // 用户主动删光习惯后不会再被塞回来。播种结果直接并入今天的任务，不等明天。
            if (dailyIds.isEmpty() && !settingsStore.isDailyHabitsSeeded()) {
                val seeds = seedPicker.pickSeeds(profile, data.entries, data.rules, excluded)
                val seedNow = System.currentTimeMillis()
                seeds.forEach {
                    entryStateDao.upsert(EntryStateEntity(it.id, EntryStateEntity.STATE_DAILY, seedNow))
                }
                settingsStore.setDailyHabitsSeeded()
                dailyIds = seeds.map { it.id }
            }
            val chosen = dailyIds.filter { (it in data.byId || it in customIds) && it !in dismissed }
            if (chosen.isEmpty()) return@withTransaction emptyList()
            val now = System.currentTimeMillis()
            // 继承同条目最近一次的提醒设置（清除也是设置，null 不继承）：每天的任务是
            // 新建的行，不继承的话「每天 8:30 提醒我」只生效一天。
            val tasks = chosen.map {
                TaskEntity(
                    entryId = it,
                    type = TaskEntity.TYPE_DAILY,
                    date = dateStr,
                    remindAtMinutes = inheritedReminderMinutes(taskDao.dailyReminderHistory(it)),
                    createdAt = now,
                )
            }
            val ids = taskDao.insertAll(tasks)
            tasks.zip(ids).mapNotNull { (task, id) -> task.remindAtMinutes?.let { id to it } }
        }
        toSchedule.forEach { (id, minutes) -> reminderScheduler.scheduleTaskReminder(id, minutes) }
    }

    /** 计时器自动打卡走接口（无备注）；带备注的打卡用下面的双参重载 */
    override suspend fun completeTask(taskId: Long) = completeTask(taskId, null)

    /**
     * 完成打卡，[note] 是随手记的完成备注，[doneBy] 记录打卡来源（手动 "manual" /
     * Health Connect 自动核销 [DONE_BY_AUTO_HC]）。
     * 任何打卡完成都会顺手给条目写 STATE_DONE（推荐引擎据此排除「做过」的内容），
     * 撤销打卡不清它——撤销只影响当天任务行，「做过」这个事实不变。
     * 一次性待办完成时清掉 STATE_TODO：计划已兑现，TODO 只表示仍在生效的计划。
     */
    suspend fun completeTask(taskId: Long, note: String? = null, doneBy: String = "manual") {
        val task = taskDao.getTask(taskId)
        taskDao.markDone(taskId, System.currentTimeMillis(), note, doneBy)
        task?.entryId?.let { entryId ->
            addEntryState(entryId, EntryStateEntity.STATE_DONE)
            if (task.type == TaskEntity.TYPE_ONCE) {
                removeEntryState(entryId, EntryStateEntity.STATE_TODO)
            }
        }
        // 已完成的任务的提醒 work 触发时也会自查 done 跳过，提前取消省一次唤醒
        reminderScheduler.cancelTaskReminder(taskId)
    }

    /**
     * 一轮 HC 自动核销的结果：核销条数 + 每条的达标证据文案（N2b 报喜通知直接用证据）。
     * 核销顺序与当日任务顺序一致。
     */
    data class AutoCompleteResult(
        val count: Int,
        val evidences: List<String>,
    ) {
        /** 报喜/合并汇总文案里的证据部分，多条用「；」连接 */
        val evidenceText: String get() = evidences.joinToString("；")

        companion object {
            val NONE = AutoCompleteResult(0, emptyList())
        }
    }

    /**
     * B1：用 Health Connect 数据自动核销今天的 DAILY 任务，返回核销结果（条数 + 证据）。
     *
     * 保守原则（误核销比不核销更糟糕）：
     * - HC 不可用、没装、缺权限、读取异常 → 安静地返回 [AutoCompleteResult.NONE]；
     * - 只核销 health_rules.json 里显式映射的条目，且该条目今天有未完成的 DAILY 行；
     * - 某类数据没权限/读不到时按 null 处理，该类型的规则本轮永不命中。
     * 备注写入达标证据（如「今日步数 9234 ≥ 7000」），doneBy = [DONE_BY_AUTO_HC]。
     */
    suspend fun autoCompleteByHealth(today: LocalDate = LocalDate.now()): AutoCompleteResult {
        val hc = healthConnect ?: return AutoCompleteResult.NONE
        val rulesRepo = healthRulesRepository ?: return AutoCompleteResult.NONE
        if (runCatching { hc.status() }.getOrNull() != HealthConnectStatus.AVAILABLE) {
            return AutoCompleteResult.NONE
        }
        val rules = runCatching { rulesRepo.rules().rules }.getOrNull().orEmpty()
        if (rules.isEmpty()) return AutoCompleteResult.NONE

        val undone = taskDao.dailyTasksFlow(today.toString()).first().filter { !it.done }
        val candidates = undone.filter { task -> rules.any { it.entryId == task.entryId } }
        if (candidates.isEmpty()) return AutoCompleteResult.NONE

        // 只读规则里用得上的数据类型；没权限的类型保持 null（对应规则不命中）
        val needed = rules.mapTo(HashSet()) { it.type }
        val steps = readMetricIfGranted(needed, HealthRule.TYPE_STEPS, { hc.hasStepsPermission() }) {
            hc.readTodaySteps()
        }
        val exerciseMin =
            readMetricIfGranted(needed, HealthRule.TYPE_EXERCISE, { hc.hasExercisePermission() }) {
                hc.readTodayExerciseMinutes()
            }
        val sleepHours =
            readMetricIfGranted(needed, HealthRule.TYPE_SLEEP, { hc.hasSleepPermission() }) {
                hc.readLastNightSleepHours()
            }

        val matched = evaluateAutoCompletion(
            rules, steps, exerciseMin, sleepHours, candidates.mapTo(HashSet()) { it.entryId },
        )
        if (matched.isEmpty()) return AutoCompleteResult.NONE

        val evidences = candidates.filter { it.entryId in matched }.map { task ->
            val rule = rules.first { it.entryId == task.entryId }
            val evidence = healthEvidenceText(rule, steps, exerciseMin, sleepHours)
            completeTask(task.taskId, "Health Connect 自动核销：$evidence", DONE_BY_AUTO_HC)
            evidence
        }
        return AutoCompleteResult(evidences.size, evidences)
    }

    /** 权限检查与读取都可能抛（HC 服务异常等），任何一步失败都退化为 null = 该类型不命中 */
    private suspend fun <T> readMetricIfGranted(
        needed: Set<String>,
        type: String,
        hasPermission: suspend () -> Boolean,
        read: suspend () -> T,
    ): T? {
        if (type !in needed) return null
        return runCatching { if (hasPermission()) read() else null }.getOrNull()
    }

    override suspend fun taskTitle(taskId: Long): String? {
        val task = taskDao.getTask(taskId) ?: return null
        return entryRepository.entriesData().byId[task.entryId]?.title
            ?: customEntryDao.title(task.entryId)
    }

    /**
     * 撤销打卡：复位任务行；完成备注保留在行里（数据不丢），DONE 状态也不动。
     * 一次性待办回到待办态，补回完成时清掉的 STATE_TODO。
     * 完成时（completeTask）会取消单任务提醒，撤销时若触发时刻仍在未来则补排回来。
     */
    suspend fun uncompleteTask(taskId: Long) {
        taskDao.markUndone(taskId)
        val task = taskDao.getTask(taskId) ?: return
        if (task.type == TaskEntity.TYPE_ONCE) {
            addEntryState(task.entryId, EntryStateEntity.STATE_TODO)
        }
        val minutes = task.remindAtMinutes
        val dueDate = task.onceDueDate()
        if (minutes != null &&
            shouldRescheduleReminder(minutes, System.currentTimeMillis(), ZoneId.systemDefault(), dueDate)
        ) {
            reminderScheduler.scheduleTaskReminder(taskId, minutes, dueDate)
        }
    }

    /**
     * 把条目加入一次性待办，并把条目标记为 TODO 状态；[dueDate] 为可选截止日。
     * 幂等：同条目已有未完成的 ONCE 行时不重复插入，只确保 TODO 状态在。
     */
    suspend fun addOneOffTodo(entryId: String, dueDate: LocalDate? = null) {
        if (taskDao.activeOnceByEntry(entryId) == null) {
            taskDao.insert(
                TaskEntity(
                    entryId = entryId,
                    type = TaskEntity.TYPE_ONCE,
                    date = null,
                    dueDate = dueDate?.toString(),
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        entryStateDao.upsert(
            EntryStateEntity(entryId, EntryStateEntity.STATE_TODO, System.currentTimeMillis())
        )
    }

    /** 聊天页「加入待办」（N3a）：无截止日的复用上面的幂等逻辑 */
    override suspend fun addToTodo(entryId: String) = addOneOffTodo(entryId)

    /**
     * 把条目设为每日习惯：写 STATE_DAILY，今天已有 DAILY 安排则直接补一行；
     * 今天还没生成则留给 ensureTodayTasks 一并规划（幂等，次日自动落行）。
     */
    override suspend fun addDailyHabit(entryId: String) {
        addEntryState(entryId, EntryStateEntity.STATE_DAILY)
        ensureTodayDailyRow(entryId)
    }

    /**
     * 设置/清除一次性待办的截止日（null = 清除）。
     * 已设独立提醒时间的任务按新截止日重排提醒 work；没设提醒时间的无需动 work。
     */
    suspend fun setOnceDueDate(taskId: Long, dueDate: LocalDate?) {
        taskDao.setDueDate(taskId, dueDate?.toString())
        val task = taskDao.getTask(taskId) ?: return
        val minutes = task.remindAtMinutes ?: return
        if (task.done) return
        reminderScheduler.scheduleTaskReminder(taskId, minutes, dueDate)
    }

    /**
     * 删除任务行。删掉未完成的一次性待办时清掉 STATE_TODO：TODO 只表示仍在生效的
     * 计划，残留会让这条内容永远被推荐排除。每日行的「今天不做」不动 STATE_DAILY。
     */
    suspend fun deleteTask(taskId: Long) {
        val task = taskDao.getTask(taskId)
        taskDao.delete(taskId)
        if (task != null && task.type == TaskEntity.TYPE_ONCE && !task.done) {
            removeEntryState(task.entryId, EntryStateEntity.STATE_TODO)
        }
        reminderScheduler.cancelTaskReminder(taskId)
    }

    /** 撤销删除:按原 taskId 把任务写回,顺序与状态都保持不变;一次性待办补回 TODO 状态 */
    suspend fun restoreTask(task: TaskEntity) {
        taskDao.insert(task)
        if (task.type == TaskEntity.TYPE_ONCE && !task.done) {
            addEntryState(task.entryId, EntryStateEntity.STATE_TODO)
        }
        task.remindAtMinutes?.let {
            reminderScheduler.scheduleTaskReminder(task.taskId, it, task.onceDueDate())
        }
    }

    /** 设置/清除单任务提醒时间（一天内分钟数，null = 跟随全局汇总），并同步重排 work */
    suspend fun setTaskReminder(taskId: Long, minutes: Int?) {
        taskDao.setReminder(taskId, minutes)
        if (minutes == null) {
            reminderScheduler.cancelTaskReminder(taskId)
            return
        }
        // 一次性待办带了截止日就按截止日触发，否则维持「今天/明天到点」的默认语义
        reminderScheduler.scheduleTaskReminder(
            taskId, minutes, taskDao.getTask(taskId)?.onceDueDate()
        )
    }

    /** ONCE 任务的截止日（其他类型恒为 null，提醒调度只对它区分日期维度） */
    private fun TaskEntity.onceDueDate(): LocalDate? =
        if (type == TaskEntity.TYPE_ONCE) dueDate?.let(LocalDate::parse) else null

    suspend fun getTask(taskId: Long): TaskEntity? = taskDao.getTask(taskId)

    suspend fun markNotified(taskId: Long) = taskDao.markNotified(taskId)

    /**
     * 打上某种条目状态。
     *
     * 状态之间是正交的：加收藏不会顶掉「加入待办」，标记不再推荐也不会清掉收藏
     * （主键是 (entryId, state)，见 [EntryStateEntity]）。
     */
    suspend fun addEntryState(entryId: String, state: String) {
        entryStateDao.upsert(EntryStateEntity(entryId, state, System.currentTimeMillis()))
    }

    /** 清除某种条目状态，不影响该条目的其他状态 */
    suspend fun removeEntryState(entryId: String, state: String) {
        entryStateDao.delete(entryId, state)
    }

    /** 收藏开关 */
    suspend fun setFavorite(entryId: String, favorite: Boolean) {
        if (favorite) addEntryState(entryId, EntryStateEntity.STATE_FAVORITE)
        else removeEntryState(entryId, EntryStateEntity.STATE_FAVORITE)
    }

    /** 已收藏的条目 id */
    fun favoriteIdsFlow(): Flow<List<String>> =
        entryStateDao.entryIdsByStateFlow(EntryStateEntity.STATE_FAVORITE)

    /** 「不再推荐」开关:DISMISSED 会被推荐引擎排除,不影响收藏/待办等其他状态 */
    suspend fun setDismissed(entryId: String, dismissed: Boolean) {
        if (dismissed) addEntryState(entryId, EntryStateEntity.STATE_DISMISSED)
        else removeEntryState(entryId, EntryStateEntity.STATE_DISMISSED)
    }

    /** 已屏蔽（不再推荐）的条目 id */
    fun dismissedIdsFlow(): Flow<List<String>> =
        entryStateDao.entryIdsByStateFlow(EntryStateEntity.STATE_DISMISSED)

    /** 已完成（DONE）的条目 id，「已完成列表」页用 */
    fun doneIdsFlow(): Flow<List<String>> =
        entryStateDao.entryIdsByStateFlow(EntryStateEntity.STATE_DONE)

    /** 「我做过了」：写 DONE 状态（推荐引擎据此排除），不产生任务行；正向反馈，无压力 */
    suspend fun markDoneBefore(entryId: String) =
        addEntryState(entryId, EntryStateEntity.STATE_DONE)

    /** 撤销「我做过了」：只清 DONE 状态，不影响收藏/待办等其他状态 */
    suspend fun unmarkDoneBefore(entryId: String) =
        removeEntryState(entryId, EntryStateEntity.STATE_DONE)

    /** 用户自选的每日习惯条目 id */
    fun userDailyIdsFlow(): Flow<List<String>> =
        entryStateDao.entryIdsByStateFlow(EntryStateEntity.STATE_DAILY)

    /**
     * 已加入任一计划（一次性待办 TODO / 每日习惯 DAILY / 每周习惯模板）的条目 id。
     * 推荐引擎据此把「已在计划里」的条目排除出推荐列表。
     */
    override fun plannedEntryIdsFlow(): Flow<Set<String>> =
        combine(
            entryStateDao.entryIdsByStateFlow(EntryStateEntity.STATE_TODO),
            entryStateDao.entryIdsByStateFlow(EntryStateEntity.STATE_DAILY),
            weeklyHabitDao.allFlow(),
        ) { todo, daily, weekly ->
            (todo + daily).toHashSet().apply { weekly.forEach { add(it.entryId) } }
        }

    /**
     * 把一次性待办转为每日习惯：删 ONCE 行、清 TODO、写 STATE_DAILY。
     * 今天的 DAILY 任务已生成过就直接补一行；还没生成则留给 ensureTodayTasks 一并规划。
     */
    suspend fun convertToDaily(entryId: String, taskId: Long) {
        taskDao.delete(taskId)
        entryStateDao.delete(entryId, EntryStateEntity.STATE_TODO)
        addEntryState(entryId, EntryStateEntity.STATE_DAILY)
        ensureTodayDailyRow(entryId)
    }

    /** 今天已有 DAILY 安排时，为 [entryId] 补一行（提醒时间继承历史设置）；今天还没生成则跳过 */
    private suspend fun ensureTodayDailyRow(entryId: String) {
        val dateStr = today.value.toString()
        val result = database.withTransaction {
            if (taskDao.countDailyByDate(dateStr) == 0) return@withTransaction null
            if (taskDao.dailyTaskByEntry(entryId, dateStr) != null) return@withTransaction null
            val minutes = inheritedReminderMinutes(taskDao.dailyReminderHistory(entryId))
            val id = taskDao.insert(
                TaskEntity(
                    entryId = entryId,
                    type = TaskEntity.TYPE_DAILY,
                    date = dateStr,
                    remindAtMinutes = minutes,
                    createdAt = System.currentTimeMillis(),
                )
            )
            minutes?.let { id to it }
        } ?: return
        reminderScheduler.scheduleTaskReminder(result.first, result.second)
    }

    /** 取消每日习惯：清 STATE_DAILY；今天还没完成的安排一并移除（已完成的保留作记录） */
    suspend fun removeDailyHabit(entryId: String) {
        removeEntryState(entryId, EntryStateEntity.STATE_DAILY)
        val task = taskDao.dailyTaskByEntry(entryId, today.value.toString())
        if (task != null && !task.done) deleteTask(task.taskId)
        if (isCustomEntryId(entryId)) customEntryDao.delete(entryId)
    }

    fun todayTasksFlow(date: LocalDate): Flow<List<TaskEntity>> =
        taskDao.dailyTasksFlow(date.toString())

    /** 跟随「今天」的每日任务流：日期被 [refreshToday] 推进后自动切到新日期的查询 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun todayTasksFlow(): Flow<List<TaskEntity>> =
        today.flatMapLatest { taskDao.dailyTasksFlow(it.toString()) }

    fun todoListFlow(): Flow<List<TaskEntity>> = taskDao.onceTasksFlow()

    /**
     * 某条目 DAILY 任务的连续完成天数，规则见 [computeStreak]：
     * 今天没完成（也没请假）时从昨天往前数；请假日搭桥，不断签也不计数。
     */
    suspend fun streak(entryId: String, today: LocalDate = LocalDate.now()): Int =
        computeStreak(
            doneDates = taskDao.doneDates(entryId).toHashSet(),
            leaveDates = streakLeaveDao.dates(entryId).toHashSet(),
            today = today,
        )

    /** 该条目的请假日期流（yyyy-MM-dd 降序），连签展示用 */
    fun streakLeaveDatesFlow(entryId: String): Flow<List<String>> =
        streakLeaveDao.datesFlow(entryId)

    /**
     * 请假一天：当天不打卡也不断签（连签计算里是「桥」）。
     * 只对用户自选的每日习惯（STATE_DAILY）有意义，别的条目直接忽略；
     * 语义保持安静：不发通知、不催打卡。
     */
    suspend fun takeLeave(entryId: String, date: LocalDate) {
        if (entryId !in entryStateDao.entryIdsByState(EntryStateEntity.STATE_DAILY)) return
        streakLeaveDao.upsert(
            StreakLeaveEntity(entryId, date.toString(), System.currentTimeMillis())
        )
    }

    /** 取消某天的请假 */
    suspend fun cancelLeave(entryId: String, date: LocalDate) =
        streakLeaveDao.delete(entryId, date.toString())

    /**
     * 补昨天的卡：找到（或补建）昨天的 DAILY 行并标记完成。
     * 只认「昨天」这一天——补卡入口不接受任意日期，避免变成随意改历史。
     * 安静操作：不发通知、不排提醒。
     */
    suspend fun backfillYesterday(entryId: String, today: LocalDate = LocalDate.now()) {
        val yesterday = today.minusDays(1).toString()
        val existing = taskDao.dailyTaskByEntry(entryId, yesterday)
        if (existing != null && existing.done) return
        val taskId = existing?.taskId ?: taskDao.insert(
            TaskEntity(
                entryId = entryId,
                type = TaskEntity.TYPE_DAILY,
                date = yesterday,
                createdAt = System.currentTimeMillis(),
            )
        )
        taskDao.markDone(taskId, System.currentTimeMillis())
        // 与 completeTask 一致：任何打卡完成都写 DONE（推荐排除用）
        addEntryState(entryId, EntryStateEntity.STATE_DONE)
    }

    suspend fun todayUndoneCount(date: LocalDate = LocalDate.now()): Int =
        taskDao.countUndoneDaily(date.toString())

    /** 昨天是否已有该条目的 DAILY 完成记录（补卡入口的可用性判断与结果反馈用） */
    suspend fun doneYesterday(entryId: String, today: LocalDate = LocalDate.now()): Boolean =
        taskDao.doneDates(entryId).contains(today.minusDays(1).toString())

    /** 用户自建任务的标题表（entryId → title），UI 标题解析用 */
    fun customEntriesFlow(): Flow<List<CustomEntryEntity>> = customEntryDao.allFlow()

    /** 新建自定义条目并返回其 id */
    private suspend fun newCustomEntry(title: String): String {
        val id = CUSTOM_ID_PREFIX + UUID.randomUUID().toString()
        customEntryDao.upsert(CustomEntryEntity(id, title.trim(), System.currentTimeMillis()))
        return id
    }

    /** 自定义一次性待办 */
    suspend fun addCustomOnce(title: String, dueDate: LocalDate? = null) =
        addOneOffTodo(newCustomEntry(title), dueDate)

    /** 自定义每日习惯：写 STATE_DAILY，今天已有安排则补一行 */
    suspend fun addCustomDaily(title: String) = addDailyHabit(newCustomEntry(title))

    /** 自定义每周习惯：一周 [timesPerWeek] 次 */
    suspend fun addCustomWeekly(title: String, timesPerWeek: Int) =
        addWeeklyHabit(newCustomEntry(title), timesPerWeek)

    /** 每周习惯的一项：模板 + 本周已打卡次数 + 今天是否已打卡 */
    data class WeeklyItem(
        val habit: WeeklyHabitEntity,
        val doneCount: Int,
        val checkedToday: Boolean,
    ) {
        val reached: Boolean get() = doneCount >= habit.timesPerWeek
    }

    /**
     * 每周习惯进度流：进度 = 打卡日落在本周区间（周一~周日）内的 WEEKLY 行数，
     * 跨周天然归零；日期被 [refreshToday] 推进后区间随之切换。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun weeklyItemsFlow(): Flow<List<WeeklyItem>> =
        combine(weeklyHabitDao.allFlow(), today) { habits, date -> habits to date }
            .flatMapLatest { (habits, date) ->
                if (habits.isEmpty()) return@flatMapLatest flowOf(emptyList())
                val (start, end) = weekRange(date)
                val todayStr = date.toString()
                taskDao.weeklyCheckinsFlow(start.toString(), end.toString()).map { checkins ->
                    val byEntry = checkins.groupBy { it.entryId }
                    habits.map { habit ->
                        val rows = byEntry[habit.entryId].orEmpty()
                        WeeklyItem(habit, rows.size, rows.any { it.date == todayStr })
                    }
                }
            }

    /** 新建每周习惯：一周目标 [timesPerWeek] 次（收敛到 1-7） */
    suspend fun addWeeklyHabit(entryId: String, timesPerWeek: Int) {
        weeklyHabitDao.upsert(
            WeeklyHabitEntity(entryId, timesPerWeek.coerceIn(1, 7), System.currentTimeMillis())
        )
    }

    /** 全部 WEEKLY 打卡历史：连续达标周数（「已养成」提示）统计用 */
    fun weeklyHistoryFlow(): Flow<List<TaskEntity>> = taskDao.weeklyAllFlow()

    /** 把一次性待办转为每周习惯：删 ONCE 行、清 TODO、建模板 */
    suspend fun convertToWeekly(entryId: String, taskId: Long, timesPerWeek: Int) {
        taskDao.delete(taskId)
        entryStateDao.delete(entryId, EntryStateEntity.STATE_TODO)
        addWeeklyHabit(entryId, timesPerWeek)
    }

    /** 移除每周习惯：删模板并清掉该条目全部打卡记录 */
    suspend fun removeWeeklyHabit(entryId: String) {
        weeklyHabitDao.delete(entryId)
        taskDao.deleteWeeklyCheckins(entryId)
        if (isCustomEntryId(entryId)) customEntryDao.delete(entryId)
    }

    /** 撤销移除：恢复模板；自定义习惯的标题一并恢复（[title] 为 null 表示非自定义） */
    suspend fun restoreWeeklyHabit(habit: WeeklyHabitEntity, title: String? = null) {
        if (title != null && isCustomEntryId(habit.entryId)) {
            customEntryDao.upsert(CustomEntryEntity(habit.entryId, title, System.currentTimeMillis()))
        }
        weeklyHabitDao.upsert(habit)
    }

    /** 今天的打卡开关：已打卡则撤销这一次，否则记一次（[note] 为可选的完成备注） */
    suspend fun toggleWeeklyToday(entryId: String, note: String? = null) {
        val dateStr = today.value.toString()
        val removed = taskDao.deleteWeeklyCheckin(entryId, dateStr)
        // 撤销打卡不写 DONE（DONE 是「做过」的事实，撤销今天这一次不改变它）
        if (removed > 0) return
        val now = System.currentTimeMillis()
        taskDao.insert(
            TaskEntity(
                entryId = entryId,
                type = TaskEntity.TYPE_WEEKLY,
                date = dateStr,
                done = true,
                doneAt = now,
                note = note,
                createdAt = now,
            )
        )
        // 与 completeTask 一致：打卡完成写 DONE（推荐排除用）
        addEntryState(entryId, EntryStateEntity.STATE_DONE)
    }
}
