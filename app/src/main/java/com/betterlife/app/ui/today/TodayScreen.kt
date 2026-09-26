// 今日页:问候 + 连续天数胶囊 + 今日任务(SplitButton 打卡)+ 按口径分组的推荐分段列表 + AI 入口 FAB
//
// 三级视觉层级(见 docs/DESIGN_SYSTEM.md §5.1):问候区是纯文字,今日任务占唯一的 Card,
// 推荐是分段列表 —— 601 条里的 5 条不该长得和「今天必须做的事」一样。
package com.betterlife.app.ui.today

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.recommend.ScoredEntry
import com.betterlife.app.ui.common.CostMeter
import com.betterlife.app.ui.common.DisputeBadge
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
) {
    val todayState by todayVm.uiState.collectAsStateWithLifecycle()
    val libraryState by libraryVm.uiState.collectAsStateWithLifecycle()

    // 「不再推荐」的结果反馈走这个宿主:撤销即把 DISMISSED 状态清掉
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dismissedMessage = stringResource(R.string.today_dismissed)
    val undoLabel = stringResource(R.string.action_undo)

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

            TodayViewModel.UiState.Empty -> NoProfileState(
                onEditProfile = onEditProfile,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            TodayViewModel.UiState.Error -> ErrorState(
                onRetry = todayVm::retry,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is TodayViewModel.UiState.Ready -> TodayContent(
                state = state,
                libraryState = libraryState,
                now = LocalDateTime.now(),
                onToggle = todayVm::toggleTask,
                onSwap = todayVm::swapTask,
                onDrop = todayVm::dropTask,
                onOpenEntry = onOpenEntry,
                onAddTodo = libraryVm::addToTodo,
                onDismissEntry = { entryId ->
                    libraryVm.dismissEntry(entryId)
                    scope.launch {
                        val result = snackbar.showSnackbar(dismissedMessage, actionLabel = undoLabel)
                        if (result == SnackbarResult.ActionPerformed) libraryVm.restoreEntry(entryId)
                    }
                },
                onStartTimer = onStartTimer,
                onOpenLibrary = onOpenLibrary,
                onEditProfile = onEditProfile,
                contentPadding = padding,
            )
        }
    }
}

/** 无状态内容：截图测试直接喂假状态渲染它，不需要 ViewModel / Room / DataStore。 */
@Composable
internal fun TodayContent(
    state: TodayViewModel.UiState.Ready,
    libraryState: LibraryViewModel.UiState,
    now: LocalDateTime,
    onToggle: (TaskEntity) -> Unit,
    onSwap: (TaskEntity) -> Unit,
    onDrop: (TaskEntity) -> Unit,
    onOpenEntry: (String) -> Unit,
    onAddTodo: (String) -> Unit,
    onDismissEntry: (String) -> Unit,
    onStartTimer: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onEditProfile: () -> Unit,
    contentPadding: PaddingValues,
) {
    // 打卡后让卡片先停在原位 300ms:形变和位移动画同时发生会互相打架
    var settlingId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(settlingId) {
        val id = settlingId ?: return@LaunchedEffect
        delay(REORDER_DELAY_MS)
        if (settlingId == id) settlingId = null
    }
    val tasks = remember(state.items, settlingId) {
        state.items.sortedWith(
            compareBy({ it.task.done && it.task.taskId != settlingId }, { it.task.taskId }),
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

        when {
            tasks.isEmpty() -> item(key = "tasks-empty") {
                EmptyCard(
                    text = stringResource(R.string.today_tasks_empty),
                    actionText = stringResource(R.string.today_action_fill_profile),
                    onAction = onEditProfile,
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
                    onToggle = {
                        if (!item.task.done) settlingId = item.task.taskId
                        onToggle(item.task)
                    },
                    onSwap = { onSwap(item.task) },
                    onDrop = { onDrop(item.task) },
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
                    modifier = Modifier.weight(1f),
                )
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
                        )
                    }
                }
            }
        }

        item(key = "bottom-spacer") { Spacer(Modifier.height(Spacing.space12)) }
    }
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
    onToggle: () -> Unit,
    onSwap: () -> Unit,
    onDrop: () -> Unit,
    onStartTimer: () -> Unit,
    onOpenEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = item.task.done
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
                text = item.entry?.title ?: item.task.entryId,
                style = MaterialTheme.typography.titleSmall,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onOpenEntry),
            )
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
            item.task.remindAtMinutes?.let {
                Spacer(Modifier.height(Spacing.space2))
                ReminderTimeLabel(it)
            }
            Spacer(Modifier.height(Spacing.space3))
            TaskAction(done = done, onToggle = onToggle, onSwap = onSwap, onDrop = onDrop, onStartTimer = onStartTimer)
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
    onToggle: () -> Unit,
    onSwap: () -> Unit,
    onDrop: () -> Unit,
    onStartTimer: () -> Unit,
) {
    // 关闭档不套 AnimatedContent:静态帧否则可能抓到按钮切换的中间态
    if (LocalMotionLevel.current == MotionLevel.OFF) {
        TaskActionContent(done, onToggle, onSwap, onDrop, onStartTimer)
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
        TaskActionContent(isDone, onToggle, onSwap, onDrop, onStartTimer)
    }
}

@Composable
private fun TaskActionContent(
    done: Boolean,
    onToggle: () -> Unit,
    onSwap: () -> Unit,
    onDrop: () -> Unit,
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
                onToggle()
            }) {
                CheckPopIcon()
                Spacer(Modifier.width(Spacing.space1))
                Text(stringResource(R.string.today_task_undo))
            }
        } else {
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onToggle()
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
                                text = { Text(stringResource(R.string.today_action_swap)) },
                                onClick = {
                                    menuOpen = false
                                    onSwap()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.today_action_drop)) },
                                onClick = {
                                    menuOpen = false
                                    onDrop()
                                },
                            )
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
 * 关闭与减弱档直接停在终态,不动。
 */
@Composable
private fun CheckPopIcon() {
    val level = LocalMotionLevel.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(level) {
        if (level != MotionLevel.STANDARD) {
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

/** 口径分组标题:标题之外再带图标 + 口径色,不依赖颜色也能区分 */
@Composable
private fun LensGroupHeader(lens: String, modifier: Modifier = Modifier) {
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

/** 没档案:整页只做一件事 —— 说清填档案的收益,给一个入口 */
@Composable
private fun NoProfileState(onEditProfile: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.today_empty_profile),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.space4))
        Button(onClick = onEditProfile) { Text(stringResource(R.string.today_action_go_profile)) }
    }
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
