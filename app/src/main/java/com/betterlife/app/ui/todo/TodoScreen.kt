// 待办页:每日习惯(圆形勾选 + 分区进度)、每周习惯(一周 N 次)与一次性待办(方框勾选 + 可撤销删除)三个分区
package com.betterlife.app.ui.todo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.ui.common.DoneBadge
import com.betterlife.app.ui.common.ReminderBellButton
import com.betterlife.app.ui.theme.LocalLensColors
import com.betterlife.app.ui.theme.LocalMotionLevel
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.motionEffectsSpec
import com.betterlife.app.ui.theme.motionSpatialSpec
import com.betterlife.app.viewmodel.TodoViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val ProgressRingSize = 20.dp
private val ProgressRingStroke = 3.dp

@Composable
fun TodoScreen(
    onOpenEntry: (String) -> Unit,
    onStartTimer: (Long) -> Unit,
    vm: TodoViewModel = viewModel(factory = TodoViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        TodoContent(
            state = state,
            contentPadding = padding,
            onToggle = vm::toggle,
            onOpenEntry = onOpenEntry,
            onDelete = { item ->
                vm.delete(item.task)
                scope.launch {
                    val message = resources.getString(
                        R.string.todo_deleted,
                        item.displayTitle,
                    )
                    val result = snackbar.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.restore(item)
                }
            },
            onSetReminder = { task, minutes -> vm.setTaskReminder(task.taskId, minutes) },
            onSetDueDate = { task, date -> vm.setOnceDueDate(task.taskId, date) },
            onStartTimer = { task -> onStartTimer(task.taskId) },
            onConvertToDaily = { item ->
                vm.convertToDaily(item)
                // 转换后条目换到上方分区,给一句轻反馈,不然看起来像「消失」
                scope.launch {
                    snackbar.showSnackbar(
                        resources.getString(R.string.todo_converted_daily, item.displayTitle)
                    )
                }
            },
            onConvertToWeekly = { item, times ->
                vm.convertToWeekly(item, times)
                scope.launch {
                    snackbar.showSnackbar(
                        resources.getString(R.string.todo_converted_weekly, item.displayTitle)
                    )
                }
            },
            onRemoveDailyHabit = { item ->
                vm.removeDailyHabit(item.task.entryId)
                scope.launch {
                    val message = resources.getString(
                        R.string.todo_daily_removed,
                        item.displayTitle,
                    )
                    val result = snackbar.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.restoreDailyHabit(item.task.entryId, item.customTitle)
                    }
                }
            },
            onToggleWeekly = vm::toggleWeekly,
            onDismissGraduation = vm::dismissGraduation,
            onRemoveWeekly = { entry ->
                vm.removeWeekly(entry.item.habit.entryId)
                scope.launch {
                    val message = resources.getString(
                        R.string.todo_weekly_removed,
                        entry.displayTitle,
                    )
                    val result = snackbar.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.restoreWeekly(entry)
                }
            },
            onAddCustom = vm::addCustom,
        )
    }
}

/** 无状态内容：截图测试直接喂假状态渲染它，不需要 ViewModel / Room / DataStore。 */
@Composable
internal fun TodoContent(
    state: TodoViewModel.UiState,
    contentPadding: PaddingValues,
    onToggle: (TaskEntity) -> Unit,
    onOpenEntry: (String) -> Unit,
    onDelete: (TodoViewModel.TodoItem) -> Unit,
    onSetReminder: (TaskEntity, Int?) -> Unit,
    onSetDueDate: (TaskEntity, LocalDate?) -> Unit,
    onStartTimer: (TaskEntity) -> Unit,
    onConvertToDaily: (TodoViewModel.TodoItem) -> Unit,
    onConvertToWeekly: (TodoViewModel.TodoItem, Int) -> Unit,
    onRemoveDailyHabit: (TodoViewModel.TodoItem) -> Unit,
    onToggleWeekly: (String) -> Unit,
    onRemoveWeekly: (TodoViewModel.WeeklyEntry) -> Unit,
    onDismissGraduation: (String) -> Unit,
    onAddCustom: (String, TodoViewModel.CustomTaskKind, Int, LocalDate?) -> Unit,
) {
    // 正在设置提醒时间的任务；非 null 时弹 TimePicker
    var reminderTarget by remember { mutableStateOf<TaskEntity?>(null) }
    // 正在修改到期日的一次性待办；非 null 时弹 DatePicker
    var dueDateTarget by remember { mutableStateOf<TaskEntity?>(null) }
    // 正在选择转换方式的一次性待办；非 null 时弹「转为每日 / 转为每周」
    var convertTarget by remember { mutableStateOf<TodoViewModel.TodoItem?>(null) }
    // 正在挑选每周次数的一次性待办；非 null 时弹次数选择
    var weeklyTimesTarget by remember { mutableStateOf<TodoViewModel.TodoItem?>(null) }
    // 是否显示「新增自定义任务」对话框
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.todo_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.todo_add_task_desc),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            SectionHeader(
                titleRes = R.string.todo_daily_title,
                trailing = {
                    if (state.daily.isNotEmpty()) {
                        HabitProgress(
                            done = state.dailyDoneCount,
                            total = state.daily.size,
                            allDone = state.dailyAllDone,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
        if (state.daily.isEmpty()) {
            item { EmptyHint(R.string.todo_daily_empty_none) }
        } else {
            items(state.daily, key = { "d-${it.task.taskId}" }) { item ->
                HabitRow(
                    item = item,
                    isUserDaily = item.task.entryId in state.userDailyIds,
                    onToggle = { onToggle(item.task) },
                    onClick = { item.entry?.let { e -> onOpenEntry(e.id) } },
                    onOpenReminder = { reminderTarget = item.task },
                    onStartTimer = { onStartTimer(item.task) },
                    onRemoveDailyHabit = { onRemoveDailyHabit(item) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item {
            SectionHeader(
                titleRes = R.string.todo_weekly_title,
                modifier = Modifier.padding(top = Spacing.space4),
            )
        }
        if (state.weekly.isEmpty()) {
            item { EmptyHint(R.string.todo_weekly_empty) }
        } else {
            items(state.weekly, key = { "w-${it.item.habit.entryId}" }) { item ->
                WeeklyRow(
                    item = item,
                    onToggle = { onToggleWeekly(item.item.habit.entryId) },
                    onClick = { item.entry?.let { e -> onOpenEntry(e.id) } },
                    onRemove = { onRemoveWeekly(item) },
                    modifier = Modifier.animateItem(),
                )
            }
            // 「已养成」提示:连续达标 N 周才出现,安静的一次性横幅,不弹窗不催促
            items(state.graduationPrompts, key = { "g-${it.entryId}" }) { prompt ->
                GraduationBanner(
                    title = state.weekly
                        .firstOrNull { it.item.habit.entryId == prompt.entryId }
                        ?.displayTitle ?: prompt.entryId,
                    weeks = prompt.weeks,
                    onDismiss = { onDismissGraduation(prompt.entryId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item {
            SectionHeader(
                titleRes = R.string.todo_once_title,
                modifier = Modifier.padding(top = Spacing.space4),
                trailing = {
                    val doneCount = state.once.count { it.task.done }
                    if (doneCount > 0) {
                        Text(
                            text = stringResource(R.string.todo_once_done_count, doneCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        if (state.once.isEmpty()) {
            item { EmptyHint(R.string.todo_once_empty) }
        } else {
            items(state.once, key = { "o-${it.task.taskId}" }) { item ->
                OnceRow(
                    item = item,
                    onToggle = { onToggle(item.task) },
                    onClick = { item.entry?.let { e -> onOpenEntry(e.id) } },
                    onDelete = { onDelete(item) },
                    onOpenReminder = { reminderTarget = item.task },
                    onEditDueDate = { dueDateTarget = item.task },
                    onStartTimer = { onStartTimer(item.task) },
                    onConvert = { convertTarget = item },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    reminderTarget?.let { target ->
        ReminderTimeDialog(
            task = target,
            onConfirm = { minutes ->
                reminderTarget = null
                onSetReminder(target, minutes)
            },
            onDismiss = { reminderTarget = null },
        )
    }

    dueDateTarget?.let { target ->
        DueDateDialog(
            initialDate = target.parsedDueDate(),
            showClear = target.dueDate != null,
            onConfirm = { date ->
                dueDateTarget = null
                onSetDueDate(target, date)
            },
            onDismiss = { dueDateTarget = null },
        )
    }

    convertTarget?.let { target ->
        ConvertDialog(
            item = target,
            onConvertToDaily = {
                convertTarget = null
                onConvertToDaily(target)
            },
            onConvertToWeekly = {
                convertTarget = null
                weeklyTimesTarget = target
            },
            onDismiss = { convertTarget = null },
        )
    }

    weeklyTimesTarget?.let { target ->
        WeeklyTimesDialog(
            onConfirm = { times ->
                weeklyTimesTarget = null
                onConvertToWeekly(target, times)
            },
            onDismiss = { weeklyTimesTarget = null },
        )
    }

    if (showAddDialog) {
        AddTaskDialog(
            onConfirm = { title, kind, times, dueDate ->
                showAddDialog = false
                onAddCustom(title, kind, times, dueDate)
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

/** 新增自定义任务：标题 + 类型（一次性/每天/每周），选每周时出现次数滑杆，选一次性时可加到期日 */
@Composable
private fun AddTaskDialog(
    onConfirm: (String, TodoViewModel.CustomTaskKind, Int, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(TodoViewModel.CustomTaskKind.ONCE) }
    var times by rememberSaveable { mutableIntStateOf(3) }
    // 到期日存 ISO 字符串:rememberSaveable 走 Bundle,比直接存 LocalDate 稳
    var dueDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var showDuePicker by remember { mutableStateOf(false) }
    val dueDate = dueDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_add_dialog_title)) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, kind, times, dueDate) },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.todo_add_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.padding(top = Spacing.space2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                ) {
                    FilterChip(
                        selected = kind == TodoViewModel.CustomTaskKind.ONCE,
                        onClick = { kind = TodoViewModel.CustomTaskKind.ONCE },
                        label = { Text(stringResource(R.string.todo_add_kind_once)) },
                    )
                    FilterChip(
                        selected = kind == TodoViewModel.CustomTaskKind.DAILY,
                        onClick = { kind = TodoViewModel.CustomTaskKind.DAILY },
                        label = { Text(stringResource(R.string.todo_add_kind_daily)) },
                    )
                    FilterChip(
                        selected = kind == TodoViewModel.CustomTaskKind.WEEKLY,
                        onClick = { kind = TodoViewModel.CustomTaskKind.WEEKLY },
                        label = { Text(stringResource(R.string.todo_add_kind_weekly)) },
                    )
                }
                if (kind == TodoViewModel.CustomTaskKind.ONCE) {
                    TextButton(
                        onClick = { showDuePicker = true },
                        modifier = Modifier.padding(top = Spacing.space2),
                    ) {
                        Text(
                            dueDate?.let {
                                stringResource(R.string.todo_add_due_set, it.monthValue, it.dayOfMonth)
                            } ?: stringResource(R.string.todo_add_due_none),
                        )
                    }
                }
                if (kind == TodoViewModel.CustomTaskKind.WEEKLY) {
                    Text(
                        text = stringResource(R.string.todo_weekly_times_option, times),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.space2),
                    )
                    Slider(
                        value = times.toFloat(),
                        onValueChange = { times = it.toInt().coerceIn(1, 7) },
                        valueRange = 1f..7f,
                        steps = 5,
                    )
                }
            }
        },
    )

    if (showDuePicker) {
        DueDateDialog(
            initialDate = dueDate,
            showClear = dueDate != null,
            onConfirm = { date ->
                showDuePicker = false
                dueDateIso = date?.toString()
            },
            onDismiss = { showDuePicker = false },
        )
    }
}

/** 单任务提醒时间弹窗:沿用设置页的 TimePicker 用法;已有提醒时多一个「清除」出口 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    task: TaskEntity,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 没设过就从当前时间起挑,设过就回显已设的时间
    val initial = task.remindAtMinutes
    val now = LocalTime.now()
    val timeState = rememberTimePickerState(
        initialHour = initial?.div(60) ?: now.hour,
        initialMinute = initial?.mod(60) ?: now.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_reminder_dialog_title)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour * 60 + timeState.minute) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            Row {
                if (task.remindAtMinutes != null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(stringResource(R.string.task_reminder_clear))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
        text = { TimePicker(state = timeState) },
    )
}

/**
 * 一次性待办的到期日选择：M3 DatePicker（material3 1.5 起已稳定，无需 ExperimentalMaterial3Api）。
 * 已有到期日时多一个「清除到期日」出口；确认时没选日期等价于不设/保持 null。
 */
@Composable
private fun DueDateDialog(
    initialDate: LocalDate?,
    showClear: Boolean,
    onConfirm: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(dateState.selectedDateMillis?.let(::utcMillisToLocalDate))
            }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            Row {
                if (showClear) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(stringResource(R.string.todo_due_date_clear))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) {
        DatePicker(
            state = dateState,
            title = { Text(stringResource(R.string.todo_due_date_dialog_title)) },
        )
    }
}

/** DatePicker 的 millis 是 UTC 零点的 epoch 毫秒,转回 LocalDate 同样按 UTC,避免时区差一天 */
private fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** tasks 表里的 yyyy-MM-dd 到期日；解析失败按没有处理 */
private fun TaskEntity.parsedDueDate(): LocalDate? =
    dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** 一次性待办的转换入口：转为每日习惯，或转为每周习惯（再选一周几次） */
@Composable
private fun ConvertDialog(
    item: TodoViewModel.TodoItem,
    onConvertToDaily: () -> Unit,
    onConvertToWeekly: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.displayTitle) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = {
            Column {
                TextButton(onClick = onConvertToDaily) {
                    Text(stringResource(R.string.todo_convert_daily))
                }
                TextButton(onClick = onConvertToWeekly) {
                    Text(stringResource(R.string.todo_convert_weekly))
                }
            }
        },
    )
}

/** 每周习惯的次数选择：一周 1-7 次 */
@Composable
private fun WeeklyTimesDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_weekly_times_dialog_title)) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = {
            Column {
                (1..7).forEach { times ->
                    TextButton(onClick = { onConfirm(times) }) {
                        Text(stringResource(R.string.todo_weekly_times_option, times))
                    }
                }
            }
        },
    )
}

@Composable
private fun SectionHeader(
    titleRes: Int,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 分区进度:今天完成了几条每日习惯;全部完成时说一句话而不是报数字 */
@Composable
private fun HabitProgress(done: Int, total: Int, allDone: Boolean, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            progress = { done.toFloat() / total },
            modifier = Modifier.size(ProgressRingSize),
            strokeWidth = ProgressRingStroke,
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.width(Spacing.space2))
        Text(
            text = if (allDone) stringResource(R.string.todo_daily_all_done)
            else stringResource(R.string.todo_daily_progress, done, total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 每日习惯:圆形勾选 + 口径色,与一次性待办的方框区分;用户自选的可「取消每日」 */
@Composable
private fun HabitRow(
    item: TodoViewModel.TodoItem,
    isUserDaily: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onOpenReminder: () -> Unit,
    onStartTimer: () -> Unit,
    onRemoveDailyHabit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = item.task
    val lensColors = LocalLensColors.current
    val tint = item.entry?.lens?.let { lensColors.forLens(it) } ?: MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            haptics.performHapticFeedback(
                if (task.done) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
            )
            onToggle()
        }) {
            HabitCheckIcon(
                done = task.done,
                tint = tint,
                contentDescription = stringResource(
                    if (task.done) R.string.todo_habit_done_desc else R.string.todo_habit_todo_desc,
                ),
            )
        }
        TaskText(item = item, modifier = Modifier.weight(1f))
        // 计时入口只出现在未完成态：给已完成的任务计时会再触发一次打卡
        if (!task.done) TimerEntryButton(onStartTimer)
        ReminderBellButton(minutes = task.remindAtMinutes, onClick = onOpenReminder)
        if (isUserDaily) {
            // EventBusy = 停掉这个反复出现的安排;Repeat 留给一次性待办的「转为每日/每周」,
            // 此前两处同图标相反含义(一个取消重复、一个开启重复)
            IconButton(onClick = onRemoveDailyHabit) {
                Icon(
                    Icons.Filled.EventBusy,
                    contentDescription = stringResource(R.string.todo_remove_daily),
                    tint = tint,
                )
            }
        }
    }
}

/**
 * 习惯勾选图标:非关闭档做一次缩放淡入的切换,与今日页打卡同族但只用一拍 ——
 * 三拍形变是今日页打卡的专属预算。关闭档直接画静态图标,一帧中间态都不能有。
 */
@Composable
private fun HabitCheckIcon(done: Boolean, tint: Color, contentDescription: String) {
    if (LocalMotionLevel.current == MotionLevel.OFF) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = contentDescription,
            tint = if (done) tint else MaterialTheme.colorScheme.outline,
        )
        return
    }
    // transitionSpec 不是 Composable 上下文,spec 必须先取出来
    val spatial = motionSpatialSpec<Float>()
    val effects = motionEffectsSpec<Float>()
    AnimatedContent(
        targetState = done,
        transitionSpec = {
            (scaleIn(initialScale = 0.9f, animationSpec = spatial) +
                fadeIn(animationSpec = effects)) togetherWith fadeOut(animationSpec = effects)
        },
        label = "habitCheck",
    ) { checked ->
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = contentDescription,
            tint = if (checked) tint else MaterialTheme.colorScheme.outline,
        )
    }
}

/** 每周习惯:圆形勾选记「今天这一次」,文本与进度环展示本周 x/N 次;达标后行变安静 */
@Composable
private fun WeeklyRow(
    item: TodoViewModel.WeeklyEntry,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekly = item.item
    val lensColors = LocalLensColors.current
    val tint = item.entry?.lens?.let { lensColors.forLens(it) } ?: MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            haptics.performHapticFeedback(
                if (weekly.checkedToday) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
            )
            onToggle()
        }) {
            HabitCheckIcon(
                done = weekly.checkedToday,
                tint = tint,
                contentDescription = stringResource(
                    if (weekly.checkedToday) R.string.todo_habit_done_desc else R.string.todo_habit_todo_desc,
                ),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.space2)) {
            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                // 完成态要比未完成态更安静：本周达标后划线弱化
                textDecoration = if (weekly.reached) TextDecoration.LineThrough else null,
            )
            Text(
                text = if (weekly.reached) stringResource(R.string.todo_weekly_reached)
                else stringResource(
                    R.string.todo_weekly_progress,
                    weekly.doneCount,
                    weekly.habit.timesPerWeek,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CircularProgressIndicator(
            progress = { (weekly.doneCount.toFloat() / weekly.habit.timesPerWeek).coerceAtMost(1f) },
            modifier = Modifier.size(ProgressRingSize),
            strokeWidth = ProgressRingStroke,
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一次性待办:方框勾选 + 可撤销的删除 + 转为每日/每周习惯 + 可改到期日 */
@Composable
private fun OnceRow(
    item: TodoViewModel.TodoItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onOpenReminder: () -> Unit,
    onEditDueDate: () -> Unit,
    onStartTimer: () -> Unit,
    onConvert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = item.task
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.done,
            onCheckedChange = {
                haptics.performHapticFeedback(
                    if (task.done) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                )
                onToggle()
            },
            colors = CheckboxDefaults.colors(
                uncheckedColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        TaskText(item = item, modifier = Modifier.weight(1f))
        if (!task.done) TimerEntryButton(onStartTimer)
        ReminderBellButton(minutes = task.remindAtMinutes, onClick = onOpenReminder)
        IconButton(onClick = onEditDueDate) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = stringResource(R.string.todo_due_date_edit_desc),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onConvert) {
            Icon(
                Icons.Filled.Repeat,
                contentDescription = stringResource(R.string.todo_convert_daily),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 「已养成」的一次性安静提示:不庆祝不施压,点「知道了」就不再出现 */
@Composable
private fun GraduationBanner(
    title: String,
    weeks: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Spacing.space3),
        ) {
            Text(
                text = stringResource(R.string.todo_graduation_prompt, title, weeks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.todo_graduation_dismiss))
            }
        }
    }
}

@Composable
private fun TimerEntryButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Filled.Timer,
            contentDescription = stringResource(R.string.task_timer_desc),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskText(item: TodoViewModel.TodoItem, modifier: Modifier = Modifier) {
    val task = item.task
    Column(modifier = modifier.padding(vertical = Spacing.space2)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                // fill=false:给「已完成」徽标留出位置,否则多行文本会把它挤成零宽
                modifier = Modifier.weight(1f, fill = false),
            )
            // 一次性待办完成后给一个明确的「已完成」标记,不只有删除线
            if (task.type == TaskEntity.TYPE_ONCE && task.done) DoneBadge()
        }
        // 一次性待办的到期日:安静地跟在标题下面;过期也只是弱化的说明,不用告警色施压
        if (task.type == TaskEntity.TYPE_ONCE) {
            task.parsedDueDate()?.let { due ->
                val overdue = !task.done && due.isBefore(LocalDate.now())
                Text(
                    text = if (overdue) {
                        stringResource(R.string.todo_due_overdue_label, due.monthValue, due.dayOfMonth)
                    } else {
                        stringResource(R.string.todo_due_date_label, due.monthValue, due.dayOfMonth)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item.entry?.human?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHint(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.space2),
    )
}
