// 今日页:问候 + 连续天数胶囊 + 今日任务(SplitButton 打卡)+ 按口径分组的推荐分段列表 + AI 入口 FAB
//
// 三级视觉层级(见 docs/DESIGN_SYSTEM.md §5.1):问候区是纯文字,今日任务占唯一的 Card,
// 推荐是分段列表 —— 601 条里的 5 条不该长得和「今天必须做的事」一样。
package com.betterlife.app.ui.today

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.data.AgeRange
import com.betterlife.app.data.Alcohol
import com.betterlife.app.data.Children
import com.betterlife.app.data.Chronic
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Exercise
import com.betterlife.app.data.Gender
import com.betterlife.app.data.Goal
import com.betterlife.app.data.Housing
import com.betterlife.app.data.Occupation
import com.betterlife.app.data.Smoking
import com.betterlife.app.data.SugaryDrinks
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.data.health.HealthConnectRepository
import com.betterlife.app.data.health.StepsSource
import com.betterlife.app.data.health.StepsState
import com.betterlife.app.recommend.EntryStats
import com.betterlife.app.recommend.ProfileQuestions
import com.betterlife.app.recommend.ScoredEntry
import com.betterlife.app.tasks.TaskManager
import com.betterlife.app.ui.common.CostMeter
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.EntryStatsLine
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.lensGroupTitle
import com.betterlife.app.ui.common.MotionEntrance
import com.betterlife.app.ui.common.ReminderTimeLabel
import com.betterlife.app.ui.common.SafeListItem
import com.betterlife.app.ui.theme.LocalLensColors
import com.betterlife.app.ui.theme.LocalMotionLevel
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.lensIcon
import com.betterlife.app.ui.theme.motionEffectsSpec
import com.betterlife.app.ui.theme.motionSpatialSpec
import com.betterlife.app.viewmodel.LibraryViewModel
import com.betterlife.app.viewmodel.StepsViewModel
import com.betterlife.app.viewmodel.TodayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 打卡后卡片停在原位的时间:等勾选的形变做完再下沉 */
private const val REORDER_DELAY_MS = 300L

/** 连续天数到这个值,胶囊换成强调色填充 */
private const val STREAK_STRONG_DAYS = 7

@Composable
fun TodayScreen(
    onOpenEntry: (String) -> Unit,
    onOpenChat: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenLibrary: () -> Unit,
    onStartTimer: (Long) -> Unit,
    todayVm: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
    libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    stepsVm: StepsViewModel = viewModel(factory = StepsViewModel.Factory),
) {
    val todayState by todayVm.uiState.collectAsStateWithLifecycle()
    val libraryState by libraryVm.uiState.collectAsStateWithLifecycle()
    val stepsState by stepsVm.uiState.collectAsStateWithLifecycle()
    val recommendUpdated by todayVm.recommendUpdated.collectAsStateWithLifecycle()

    // 「不再推荐」/「我做过了」/「补卡」的结果反馈走这个宿主:撤销类动作即回滚对应状态
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dismissedMessage = stringResource(R.string.today_dismissed)
    val doneBeforeMessage = stringResource(R.string.today_done_before)
    val backfillSuccessMessage = stringResource(R.string.today_backfill_success)
    val backfillHadMessage = stringResource(R.string.today_backfill_had)
    val undoLabel = stringResource(R.string.action_undo)

    // 步数数据源按门面给的 source 发起对应的授权流程;授权结果回来都统一 refresh 重判。
    // HC 一次申请自动核销要用的全部读权限(步数+运动+睡眠),缺的类型对应规则安静地不命中
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { stepsVm.refresh() }
    val sensorPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { stepsVm.refresh() }
    val onAuthorizeSteps = {
        when ((stepsState as? StepsState.Unauthorized)?.source ?: StepsSource.SENSOR) {
            StepsSource.HEALTH_CONNECT ->
                healthPermissionLauncher.launch(HealthConnectRepository.AUTO_COMPLETE_PERMISSIONS)
            StepsSource.SENSOR ->
                sensorPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    // Health Connect 路径没有实时流，回前台时重读一次快照；顺手跑一次 B1 自动核销（无权限时 no-op）
    LifecycleResumeEffect(stepsVm, todayVm) {
        stepsVm.refresh()
        todayVm.autoCompleteByHealth()
        onPauseOrDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenChat) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.title_chat))
            }
        },
    ) { padding ->
        when (val state = todayState) {
            // 骨架屏按 Ready 布局画占位:用户第一眼就知道内容会落在哪,
            // 居中转圈只会让人觉得「什么都没加载出来」
            TodayViewModel.UiState.Loading -> {
                val skeletonVisible = remember { MutableTransitionState(false).apply { targetState = true } }
                MotionEntrance(visibleState = skeletonVisible) {
                    TodaySkeleton(Modifier.fillMaxSize().padding(padding))
                }
            }

            TodayViewModel.UiState.Error -> ErrorState(
                onRetry = todayVm::retry,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is TodayViewModel.UiState.Ready -> {
                // 骨架屏有入场,Ready 裸切过来就是硬切;只淡入,不带位移/缩放
                val readyVisible = remember { MutableTransitionState(false).apply { targetState = true } }
                MotionEntrance(visibleState = readyVisible) {
                    TodayContent(
                        state = state,
                        libraryState = libraryState,
                        stepsState = stepsState,
                        now = LocalDateTime.now(),
                        recommendUpdated = recommendUpdated,
                        onCheckIn = todayVm::checkIn,
                        onUndo = todayVm::undoCheckIn,
                        onDrop = todayVm::dropTask,
                        onTakeLeave = { task -> todayVm.takeLeaveToday(task.entryId) },
                        onCancelLeave = { task -> todayVm.cancelLeaveToday(task.entryId) },
                        onBackfill = { task ->
                            todayVm.backfillYesterday(task.entryId) { filled ->
                                scope.launch {
                                    snackbar.showSnackbar(if (filled) backfillSuccessMessage else backfillHadMessage)
                                }
                            }
                        },
                        onAnswerQuestion = todayVm::answerQuestion,
                        onSkipQuestion = todayVm::skipQuestion,
                        onOpenEntry = onOpenEntry,
                        onAddTodo = libraryVm::addToTodo,
                        onReshuffle = libraryVm::reshuffleRecommendations,
                        onDismissEntry = { entryId ->
                            libraryVm.dismissEntry(entryId)
                            scope.launch {
                                val result = snackbar.showSnackbar(dismissedMessage, actionLabel = undoLabel)
                                if (result == SnackbarResult.ActionPerformed) libraryVm.restoreEntry(entryId)
                            }
                        },
                        onMarkDoneBefore = { entryId ->
                            libraryVm.markDoneBefore(entryId)
                            scope.launch {
                                val result = snackbar.showSnackbar(doneBeforeMessage, actionLabel = undoLabel)
                                if (result == SnackbarResult.ActionPerformed) libraryVm.unmarkDoneBefore(entryId)
                            }
                        },
                        onAuthorizeSteps = onAuthorizeSteps,
                        onStartTimer = onStartTimer,
                        onOpenLibrary = onOpenLibrary,
                        onEditProfile = onEditProfile,
                        contentPadding = padding,
                    )
                }
            }
        }
    }
}

/** 无状态内容：截图测试直接喂假状态渲染它，不需要 ViewModel / Room / DataStore。 */
@Composable
internal fun TodayContent(
    state: TodayViewModel.UiState.Ready,
    libraryState: LibraryViewModel.UiState,
    stepsState: StepsState,
    now: LocalDateTime,
    onCheckIn: (TaskEntity, String?) -> Unit,
    onUndo: (TaskEntity) -> Unit,
    onDrop: (TaskEntity) -> Unit,
    onTakeLeave: (TaskEntity) -> Unit,
    onCancelLeave: (TaskEntity) -> Unit,
    onBackfill: (TaskEntity) -> Unit,
    onOpenEntry: (String) -> Unit,
    onAddTodo: (String) -> Unit,
    onDismissEntry: (String) -> Unit,
    onMarkDoneBefore: (String) -> Unit,
    onReshuffle: () -> Unit,
    onAuthorizeSteps: () -> Unit,
    onStartTimer: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onEditProfile: () -> Unit,
    contentPadding: PaddingValues,
    /** 答完每日一问的轻反馈：推荐区标题短暂显示「推荐已更新」（N1） */
    recommendUpdated: Boolean = false,
    onAnswerQuestion: (String, Set<String>) -> Unit = { _, _ -> },
    onSkipQuestion: (String) -> Unit = {},
) {
    // 打卡/撤销后让卡片先停在原位 300ms:形变和位移动画同时发生会互相打架。
    // pinnedDone 记下动作瞬间的分组:Room 状态还没回来的间隙里,卡片也不许先动
    var settlingId by remember { mutableStateOf<Long?>(null) }
    var settlingPinnedDone by remember { mutableStateOf(false) }
    LaunchedEffect(settlingId) {
        val id = settlingId ?: return@LaunchedEffect
        delay(REORDER_DELAY_MS)
        if (settlingId == id) settlingId = null
    }
    // 打卡备注 / 请假 / 补卡的确认弹窗:一次只会有一个
    var noteDialogTask by remember { mutableStateOf<TaskEntity?>(null) }
    var leaveDialogTask by remember { mutableStateOf<TaskEntity?>(null) }
    var backfillDialogTask by remember { mutableStateOf<TaskEntity?>(null) }
    // settlingId 期间把任务钉在动作前的分组:打卡的留在未完成组,撤销的留在已完成组,
    // 等 300ms 沉降延迟结束、勾选形变做完,再统一重排
    val tasks = remember(state.items, settlingId, settlingPinnedDone) {
        state.items.sortedWith(
            compareBy(
                { if (it.task.taskId == settlingId) settlingPinnedDone else it.task.done },
                { it.task.taskId },
            ),
        )
    }
    val streak = remember(state.items) { state.items.maxOfOrNull { it.streak } ?: 0 }

    // 全部完成时卡片收起,只留一句话;用户想回顾(或撤销)可以再展开
    var reviewing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.allDone) { if (!state.allDone) reviewing = false }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(vertical = Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(Spacing.space3),
    ) {
        item(key = "greeting") {
            GreetingHeader(
                streak = streak,
                now = now,
                modifier = Modifier.padding(horizontal = Spacing.space4),
            )
        }

        // N1 渐进式档案收集:今天有题可问时出问题卡片;答过/暂缓过、但档案还没填完时,
        // 兜底为常驻的「完善档案」Banner(点了进档案编辑)
        val questionField = state.questionField
        if (questionField != null) {
            item(key = "profile-question") {
                ProfileQuestionCard(
                    field = questionField,
                    onAnswer = { onAnswerQuestion(questionField, it) },
                    onSkip = { onSkipQuestion(questionField) },
                    onFillAll = onEditProfile,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
            }
        } else if (state.profileIncomplete) {
            item(key = "profile-banner") {
                ProfileNudgeBanner(
                    onEditProfile = onEditProfile,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
            }
        }

        // 步数卡片只在有数据或待授权时出现;Loading / Unavailable 不渲染(模拟器上就是没有卡片)
        if (stepsState is StepsState.Available || stepsState is StepsState.Unauthorized) {
            item(key = "steps") {
                StepsCard(
                    state = stepsState,
                    onAuthorize = onAuthorizeSteps,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
            }
        }

        item(key = "tasks-title") {
            val doneCount = tasks.size - state.undoneCount
            val progress = stringResource(R.string.today_tasks_progress, doneCount, tasks.size)
            Text(
                text = stringResource(R.string.today_tasks_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = Spacing.space4)
                    .then(
                        if (tasks.isEmpty()) Modifier
                        else Modifier.semantics { contentDescription = progress },
                    ),
            )
        }

        // 每日任务是纯用户自选:走到这里说明用户把习惯删光了,引导去条目库重新挑
        when {
            tasks.isEmpty() -> item(key = "tasks-empty") {
                EmptyCard(
                    text = stringResource(R.string.today_tasks_empty),
                    actionText = stringResource(R.string.today_action_go_library),
                    onAction = onOpenLibrary,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
            }

            state.allDone && !reviewing -> item(key = "all-done") {
                AllDoneCard(
                    count = tasks.size,
                    onReview = { reviewing = true },
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
            }

            else -> items(tasks, key = { it.task.taskId }) { item ->
                DailyTaskCard(
                    item = item,
                    onRequestCheckIn = { noteDialogTask = item.task },
                    onUndo = {
                        // 撤销同样走沉降延迟:卡片先在已完成组停 300ms,等勾的形变做完再升回去
                        settlingId = item.task.taskId
                        settlingPinnedDone = item.task.done
                        onUndo(item.task)
                    },
                    onDrop = { onDrop(item.task) },
                    onRequestLeave = { leaveDialogTask = item.task },
                    onCancelLeave = { onCancelLeave(item.task) },
                    onRequestBackfill = { backfillDialogTask = item.task },
                    onStartTimer = { onStartTimer(item.task.taskId) },
                    onOpenEntry = { item.entry?.let { onOpenEntry(it.id) } },
                    modifier = Modifier
                        .padding(horizontal = Spacing.space4)
                        .animateItem(),
                )
            }
        }

        item(key = "recommend-title") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.space4, end = Spacing.space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.today_recommend_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                // N1:答完每日一问的轻反馈,短暂显示后由 ViewModel 复位
                if (recommendUpdated) {
                    Spacer(Modifier.width(Spacing.space2))
                    Text(
                        text = stringResource(R.string.today_recommend_updated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onReshuffle) { Text(stringResource(R.string.today_reshuffle)) }
                TextButton(onClick = onOpenLibrary) { Text(stringResource(R.string.today_all_entries)) }
            }
        }

        if (libraryState.recommended.isEmpty()) {
            item(key = "recommend-empty") {
                EmptyCard(
                    text = stringResource(R.string.today_recommend_empty),
                    actionText = stringResource(R.string.today_action_go_profile),
                    onAction = onEditProfile,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
            }
        } else {
            libraryState.recommended.forEach { (lens, entries) ->
                item(key = "lens-$lens") {
                    LensGroupHeader(
                        lens = lens,
                        stats = libraryState.lensStats[lens],
                        modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space1),
                    )
                }
                itemsIndexed(entries, key = { _, scored -> "rec-${scored.entry.id}" }) { index, scored ->
                    Column {
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = Spacing.space4),
                            )
                        }
                        RecommendedRow(
                            scored = scored,
                            onClick = { onOpenEntry(scored.entry.id) },
                            onAddTodo = { onAddTodo(scored.entry.id) },
                            onDismiss = { onDismissEntry(scored.entry.id) },
                            onMarkDoneBefore = { onMarkDoneBefore(scored.entry.id) },
                        )
                    }
                }
            }
        }

        item(key = "bottom-spacer") { Spacer(Modifier.height(Spacing.space12)) }
    }

    // 打卡备注(C2):可留空,跳过 = 不带备注直接打卡;点外部取消 = 不打卡
    noteDialogTask?.let { task ->
        CheckInNoteDialog(
            onConfirm = { note ->
                settlingId = task.taskId
                settlingPinnedDone = task.done
                onCheckIn(task, note)
                noteDialogTask = null
            },
            onDismiss = { noteDialogTask = null },
        )
    }

    // 请假(B3):确认后当天不打卡也不断签,语义保持安静
    leaveDialogTask?.let { task ->
        ConfirmDialog(
            title = stringResource(R.string.today_action_leave),
            message = stringResource(R.string.today_leave_confirm),
            onConfirm = {
                onTakeLeave(task)
                leaveDialogTask = null
            },
            onDismiss = { leaveDialogTask = null },
        )
    }

    // 补昨天的卡(B3):只认昨天这一天,结果反馈由调用方 snackbar 给出
    backfillDialogTask?.let { task ->
        ConfirmDialog(
            title = stringResource(R.string.today_action_backfill),
            message = stringResource(R.string.today_backfill_confirm),
            onConfirm = {
                onBackfill(task)
                backfillDialogTask = null
            },
            onDismiss = { backfillDialogTask = null },
        )
    }
}

/** C2 打卡备注弹窗:单行、可留空;打卡与跳过都会完成打卡,区别只在带不带备注 */
@Composable
private fun CheckInNoteDialog(onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.today_checkin_note_title)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.today_checkin_note_placeholder)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.ifBlank { null }) }) {
                Text(stringResource(R.string.today_task_check))
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(null) }) {
                Text(stringResource(R.string.today_checkin_note_skip))
            }
        },
    )
}

/** 确定/取消 的轻确认弹窗(请假、补卡共用),按钮文案走全局 action_confirm/action_cancel */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 问候语只由小时决定。提成纯函数 + 由外部传入时刻,是为了让截图预览能固定住时间 ——
 * 直接在组件里读时钟的话,基线每天（甚至每小时）都会自己失效。 */
internal fun greetingResForHour(hour: Int): Int = when (hour) {
    in 5..10 -> R.string.today_greeting_morning
    in 11..13 -> R.string.today_greeting_noon
    in 14..17 -> R.string.today_greeting_afternoon
    else -> R.string.today_greeting_evening
}

/** 问候日期固定中文格式，不随系统 locale 变化（避免英文设备上中英混排） */
internal fun formatFullDateZh(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.CHINA))

@Composable
private fun GreetingHeader(streak: Int, now: LocalDateTime, modifier: Modifier = Modifier) {
    val greeting = stringResource(greetingResForHour(now.hour))
    val date = remember(now) { formatFullDateZh(now.toLocalDate()) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineSmall)
            Text(
                date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (streak > 0) StreakPill(streak)
    }
}

/** 连续天数胶囊:数字用等宽数字,否则天数跳动时整行会横移 */
@Composable
private fun StreakPill(days: Int) {
    val strong = days >= STREAK_STRONG_DAYS
    Surface(
        color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (strong) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = stringResource(R.string.today_streak_pill, days),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space2),
        )
    }
}

@Composable
private fun DailyTaskCard(
    item: TodayViewModel.TaskItem,
    onRequestCheckIn: () -> Unit,
    onUndo: () -> Unit,
    onDrop: () -> Unit,
    onRequestLeave: () -> Unit,
    onCancelLeave: () -> Unit,
    onRequestBackfill: () -> Unit,
    onStartTimer: () -> Unit,
    onOpenEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = item.task.done
    // 三拍庆祝一张卡只播一次:卡片划出屏再划回、或打开页面时已完成,勾都停在终态;
    // 撤销后重置,下次打卡还能再播
    var celebrated by rememberSaveable(item.task.taskId) { mutableStateOf(false) }
    LaunchedEffect(done) { if (!done) celebrated = false }
    // 请假只对未完成态有意义:已完成(或完成后请假)的卡维持正常完成态
    val onLeave = item.onLeaveToday && !done
    // 平板/桌面指针悬停时抬一档容器层级,给个「指到了」的反馈;触控下 hoverable 无开销
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            // 完成态退到背景:更低的容器层级 + 更弱的文字
            containerColor = when {
                hovered -> MaterialTheme.colorScheme.surfaceContainerHighest
                done -> MaterialTheme.colorScheme.surfaceContainerLowest
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = modifier.fillMaxWidth().hoverable(interactionSource),
    ) {
        Column(Modifier.padding(Spacing.space4)) {
            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onOpenEntry),
            )
            // B1:Health Connect 自动核销的卡标出来源,小而弱,不抢完成态的安静
            if (done && item.doneBy == TaskManager.DONE_BY_AUTO_HC) {
                Spacer(Modifier.height(Spacing.space1))
                Text(
                    text = stringResource(R.string.today_auto_done_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.entry?.human?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.space1))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // C2:完成备注只显示手动打卡的随手记;自动核销的备注是达标证据,已由来源标签表达
            if (done && item.doneBy != TaskManager.DONE_BY_AUTO_HC) {
                item.task.note?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(Spacing.space1))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.task.remindAtMinutes?.let {
                Spacer(Modifier.height(Spacing.space2))
                ReminderTimeLabel(it)
            }
            Spacer(Modifier.height(Spacing.space3))
            if (onLeave) {
                LeaveAction(onCancelLeave = onCancelLeave)
            } else {
                TaskAction(
                    done = done,
                    isDailyHabit = item.isDailyHabit,
                    playPop = done && !celebrated,
                    onPopPlayed = { celebrated = true },
                    onCheckIn = onRequestCheckIn,
                    onUndo = onUndo,
                    onDrop = onDrop,
                    onRequestLeave = onRequestLeave,
                    onRequestBackfill = onRequestBackfill,
                    onStartTimer = onStartTimer,
                )
            }
        }
    }
}

/** B3 请假态:一行安静的「已请假」+ 取消入口,替代打卡按钮;不庆祝也不警告 */
@Composable
private fun LeaveAction(onCancelLeave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.today_leave_state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancelLeave) {
            Text(stringResource(R.string.today_leave_cancel))
        }
    }
}

/** 打卡勾的三拍:0.9 → 1.15 → 1.0。节奏按 §6.2 的「入场 250ms」 */
private const val CHECK_POP_FROM = 0.9f
private const val CHECK_POP_PEAK = 1.15f
private const val CHECK_POP_MILLIS = 250

/** 打卡动作:未完成是「打卡 + 更多」分裂按钮,已完成收成一个可撤销的按钮 */
@Composable
private fun TaskAction(
    done: Boolean,
    isDailyHabit: Boolean,
    playPop: Boolean,
    onPopPlayed: () -> Unit,
    onCheckIn: () -> Unit,
    onUndo: () -> Unit,
    onDrop: () -> Unit,
    onRequestLeave: () -> Unit,
    onRequestBackfill: () -> Unit,
    onStartTimer: () -> Unit,
) {
    // 关闭档不套 AnimatedContent:静态帧否则可能抓到按钮切换的中间态
    if (LocalMotionLevel.current == MotionLevel.OFF) {
        TaskActionContent(
            done, isDailyHabit, playPop, onPopPlayed, onCheckIn, onUndo, onDrop,
            onRequestLeave, onRequestBackfill, onStartTimer,
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
                fadeIn(animationSpec = effects)) togetherWith
                (scaleOut(targetScale = 0.9f, animationSpec = spatial) +
                    fadeOut(animationSpec = effects))
        },
        label = "taskAction",
    ) { isDone ->
        TaskActionContent(
            isDone, isDailyHabit, playPop, onPopPlayed, onCheckIn, onUndo, onDrop,
            onRequestLeave, onRequestBackfill, onStartTimer,
        )
    }
}

@Composable
private fun TaskActionContent(
    done: Boolean,
    isDailyHabit: Boolean,
    playPop: Boolean,
    onPopPlayed: () -> Unit,
    onCheckIn: () -> Unit,
    onUndo: () -> Unit,
    onDrop: () -> Unit,
    onRequestLeave: () -> Unit,
    onRequestBackfill: () -> Unit,
    onStartTimer: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            FilledTonalButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
                onUndo()
            }) {
                CheckPopIcon(playPop = playPop, onPopPlayed = onPopPlayed)
                Spacer(Modifier.width(Spacing.space1))
                Text(stringResource(R.string.today_task_undo))
            }
        } else {
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onCheckIn()
                    }) {
                        Text(stringResource(R.string.today_task_check))
                    }
                },
                trailingButton = {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        SplitButtonDefaults.TrailingButton(
                            checked = menuOpen,
                            onCheckedChange = { menuOpen = it },
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.today_task_menu),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.today_action_drop)) },
                                onClick = {
                                    menuOpen = false
                                    onDrop()
                                },
                            )
                            // 请假/补卡只对用户自选的每日习惯开放:任务行生成后习惯被取消时
                            // isDailyHabit 为 false(连签已断,没有要保的)
                            if (isDailyHabit) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.today_action_leave)) },
                                    onClick = {
                                        menuOpen = false
                                        onRequestLeave()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.today_action_backfill)) },
                                    onClick = {
                                        menuOpen = false
                                        onRequestBackfill()
                                    },
                                )
                            }
                        }
                    }
                },
            )
            // 计时入口只出现在未完成态：给已完成的任务计时会再触发一次打卡
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onStartTimer) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = stringResource(R.string.task_timer_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 打卡勾:一次三拍形变。这是全 App 最高频的动作,值得一次明确的反馈。
 *
 * 触觉只是**加强**,形变才是主反馈 —— 不能把触觉当唯一反馈(§6.3)。
 * 关闭与减弱档、以及已庆祝过的卡(playPop=false)直接停在终态,不动。
 */
@Composable
private fun CheckPopIcon(playPop: Boolean, onPopPlayed: () -> Unit) {
    val level = LocalMotionLevel.current
    // 初始值按播放意图取,避免首帧先画终态再 snapTo 回起点的闪跳
    val scale = remember {
        Animatable(if (level == MotionLevel.STANDARD && playPop) CHECK_POP_FROM else 1f)
    }
    LaunchedEffect(level, playPop) {
        if (level != MotionLevel.STANDARD || !playPop) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        scale.snapTo(CHECK_POP_FROM)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = CHECK_POP_MILLIS
                CHECK_POP_PEAK at CHECK_POP_MILLIS / 3
                1f at CHECK_POP_MILLIS
            },
        )
        onPopPlayed()
    }
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        modifier = Modifier
            .size(Spacing.space4)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

/** 全部完成:一句话 + 一次形变,不弹窗、不放彩带 */
@Composable
private fun AllDoneCard(count: Int, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    MotionEntrance(visibleState = visible, modifier = modifier, scaleFrom = 0.96f) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.space3))
                Text(
                    text = stringResource(R.string.today_all_done, count),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReview) { Text(stringResource(R.string.today_all_done_review)) }
            }
        }
    }
}

/** 口径分组标题:标题之外再带图标 + 口径色,不依赖颜色也能区分;[stats] 是该口径全书的 待看/完成/忽略 统计 */
@Composable
private fun LensGroupHeader(lens: String, modifier: Modifier = Modifier, stats: EntryStats? = null) {
    val tint = LocalLensColors.current.forLens(lens) ?: MaterialTheme.colorScheme.onSurfaceVariant
    val icon = lensIcon(lens)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Spacing.space4))
        } else {
            Box(Modifier.size(Spacing.space2).clip(CircleShape).background(tint))
        }
        Text(lensGroupTitle(lens), style = MaterialTheme.typography.titleSmall, color = tint)
        if (stats != null) {
            Spacer(Modifier.weight(1f))
            EntryStatsLine(stats)
        }
    }
}

/** 推荐条目:分段列表行,不是卡片。点按进详情,长按出「不再推荐这条」菜单。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecommendedRow(
    scored: ScoredEntry,
    onClick: () -> Unit,
    onAddTodo: () -> Unit,
    onDismiss: () -> Unit,
    onMarkDoneBefore: () -> Unit,
) {
    val entry: EntryDto = scored.entry
    // 与 DailyTaskCard 同一套 hover 先例:指针悬停抬到 surfaceContainerHighest
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val overline: (@Composable () -> Unit)? =
        if (entry.grade.isNotBlank() || entry.dispute) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                    GradeBadge(entry.grade)
                    if (entry.dispute) DisputeBadge()
                }
            }
        } else {
            null
        }

    Box {
        SafeListItem(
            modifier = Modifier
                .hoverable(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                ),
            colors = ListItemDefaults.colors(
                containerColor = if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
            ),
            overlineContent = overline,
            supportingContent = {
                Column {
                    if (entry.human.isNotBlank()) {
                        Text(
                            text = entry.human,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.space2))
                    }
                    CostMeter(entry)
                }
            },
            trailingContent = {
                IconButton(onClick = onAddTodo) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.today_add_todo_desc))
                }
            },
        ) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.today_done_before_menu)) },
                onClick = {
                    menuOpen = false
                    onMarkDoneBefore()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.today_dismiss_menu)) },
                onClick = {
                    menuOpen = false
                    onDismiss()
                },
            )
        }
    }
}

/** 骨架屏:按 TodayContent 的真实结构摆占位块 —— 问候两行、一张任务卡、三条推荐行 */
@Composable
internal fun TodaySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(Spacing.space3),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(Spacing.space6))
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(Spacing.space4))
        }
        SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(Spacing.space4))
        // 任务卡:内边距 + 标题 + 两行正文 + 按钮,实测约 128dp
        SkeletonBlock(
            Modifier.fillMaxWidth().height(Spacing.space12 + Spacing.space12 + Spacing.space8),
            shape = MaterialTheme.shapes.large,
        )
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(Spacing.space4))
        repeat(3) {
            // ListItem 带 overline + supporting 的标准行高
            SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.space12 + Spacing.space6))
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) {
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
private fun EmptyCard(
    text: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

/** N1:档案未填完时的常驻兜底入口,点了进档案编辑 */
@Composable
private fun ProfileNudgeBanner(onEditProfile: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.today_profile_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEditProfile) { Text(stringResource(R.string.today_action_go_profile)) }
        }
    }
}

/**
 * N1 每日一问卡片:一天只问一个未填的档案字段。
 * 单选/布尔字段点了即答;多选字段（chronic/goals）用「确定」确认,空选 = 都没有。
 * 「暂不回答」当天不再出现,次日换下一题。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileQuestionCard(
    field: String,
    onAnswer: (Set<String>) -> Unit,
    onSkip: () -> Unit,
    onFillAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = questionTitleRes(field) ?: return
    val options = questionOptions(field)
    val multi = field in ProfileQuestions.MULTI_FIELDS
    var selected by remember(field) { mutableStateOf(setOf<String>()) }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                options.forEach { (key, label) ->
                    ToggleButton(
                        checked = key in selected,
                        onCheckedChange = {
                            if (multi) {
                                selected = if (key in selected) selected - key else selected + key
                            } else {
                                onAnswer(setOf(key))
                            }
                        },
                    ) { Text(label) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (multi) {
                    TextButton(onClick = { onAnswer(selected) }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSkip) { Text(stringResource(R.string.today_question_skip)) }
                TextButton(onClick = onFillAll) { Text(stringResource(R.string.today_question_fill_all)) }
            }
        }
    }
}

/** 每日一问的问题文案;字段不在提问序列里（不该发生）返回 null,卡片不渲染 */
private fun questionTitleRes(field: String): Int? = when (field) {
    "ageRange" -> R.string.question_age_range
    "smoking" -> R.string.question_smoking
    "alcohol" -> R.string.question_alcohol
    "exercise" -> R.string.question_exercise
    "sleepShort" -> R.string.question_sleep_short
    "chronic" -> R.string.question_chronic
    "goals" -> R.string.question_goals
    "children" -> R.string.question_children
    "pregnant" -> R.string.question_pregnant
    "hasElderly" -> R.string.question_has_elderly
    "betelNut" -> R.string.question_betel_nut
    "sugaryDrinks" -> R.string.question_sugary
    "occupation" -> R.string.question_occupation
    "housing" -> R.string.question_housing
    "gender" -> R.string.question_gender
    "secondhandSmoke" -> R.string.question_secondhand
    "financialStress" -> R.string.question_financial_stress
    "planningAbroad" -> R.string.question_planning_abroad
    else -> null
}

/** 每日一问的选项:取值 key（与规则 when 的取值一致）到展示文案;布尔字段统一 是/否 */
@Composable
private fun questionOptions(field: String): List<Pair<String, String>> = when (field) {
    "ageRange" -> AgeRange.entries.map { it.key to it.key }
    "smoking" -> listOf(
        Smoking.YES.key to stringResource(R.string.option_smoking_yes),
        Smoking.QUIT.key to stringResource(R.string.option_smoking_quit),
        Smoking.NO.key to stringResource(R.string.option_smoking_no),
    )
    "alcohol" -> listOf(
        Alcohol.OFTEN.key to stringResource(R.string.option_alcohol_often),
        Alcohol.SOMETIMES.key to stringResource(R.string.option_sometimes),
        Alcohol.NO.key to stringResource(R.string.option_no_drink),
    )
    "exercise" -> listOf(
        Exercise.NONE.key to stringResource(R.string.option_exercise_none),
        Exercise.LOW.key to stringResource(R.string.option_exercise_low),
        Exercise.OK.key to stringResource(R.string.option_exercise_ok),
    )
    "sugaryDrinks" -> listOf(
        SugaryDrinks.DAILY.key to stringResource(R.string.option_drinks_daily),
        SugaryDrinks.SOMETIMES.key to stringResource(R.string.option_sometimes),
        SugaryDrinks.NO.key to stringResource(R.string.option_no_drink),
    )
    "chronic" -> listOf(
        Chronic.HYPERTENSION.key to stringResource(R.string.option_chronic_hypertension),
        Chronic.DIABETES.key to stringResource(R.string.option_chronic_diabetes),
        Chronic.KIDNEY.key to stringResource(R.string.option_chronic_kidney),
        Chronic.HEART.key to stringResource(R.string.option_chronic_heart),
        Chronic.OTHER.key to stringResource(R.string.option_other),
    )
    "goals" -> listOf(
        Goal.HEALTH.key to stringResource(R.string.goal_health),
        Goal.MONEY.key to stringResource(R.string.goal_money),
        Goal.TIME.key to stringResource(R.string.goal_time),
        Goal.CAREER.key to stringResource(R.string.goal_career),
        Goal.FAMILY.key to stringResource(R.string.goal_family),
        Goal.RELAX.key to stringResource(R.string.goal_relax),
    )
    "children" -> listOf(
        Children.NONE.key to stringResource(R.string.option_children_none),
        Children.BABY.key to stringResource(R.string.option_children_baby),
        Children.SCHOOL.key to stringResource(R.string.option_children_school),
    )
    "occupation" -> listOf(
        Occupation.PROGRAMMER.key to stringResource(R.string.option_programmer),
        Occupation.STUDENT.key to stringResource(R.string.option_student),
        Occupation.OTHER.key to stringResource(R.string.option_other),
    )
    "housing" -> listOf(
        Housing.RENT.key to stringResource(R.string.option_rent),
        Housing.OWN.key to stringResource(R.string.option_own),
        Housing.FAMILY.key to stringResource(R.string.option_live_family),
    )
    "gender" -> listOf(
        Gender.MALE.key to stringResource(R.string.option_male),
        Gender.FEMALE.key to stringResource(R.string.option_female),
        Gender.OTHER.key to stringResource(R.string.option_other),
    )
    else -> listOf(
        "true" to stringResource(R.string.option_yes),
        "false" to stringResource(R.string.option_no),
    )
}

@Composable
private fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.common_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.space3))
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
