// 数据统计页（B6/B7/C5③）：回顾与成就、连续天数、近 8 周完成率趋势、周期报告 + AI 解读。
// 基调是「回顾」而不是「激励」（§1 规则 4）：没有徽章墙与排行榜，空态不施压，
// 新达成的里程碑只做一次性低调高亮。
package com.betterlife.app.ui.stats

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.stats.Achievement
import com.betterlife.app.stats.AchievementKeys
import com.betterlife.app.stats.CheckinNote
import com.betterlife.app.stats.PeriodReport
import com.betterlife.app.stats.StatsPeriod
import com.betterlife.app.stats.WeeklyCompletion
import com.betterlife.app.ui.common.MotionEntrance
import com.betterlife.app.ui.common.lensGroupTitle
import com.betterlife.app.ui.theme.LocalLensColors
import com.betterlife.app.ui.theme.LocalMotionLevel
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.lensIcon
import com.betterlife.app.viewmodel.StatsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    StatsContent(
        state = state,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onSelectPeriod = vm::selectPeriod,
        onInterpret = { label -> vm.interpretPeriod(label) },
    )
}

/** 无状态内容：截图测试直接喂假状态渲染它，不需要 ViewModel / Room */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsContent(
    state: StatsViewModel.UiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onInterpret: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (!state.hasAnyActivity) {
            // 空态：安静的引导，不催促、不施压
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.space3),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            MotionEntrance(visibleState = staggeredCardVisible(0), slideFromBottom = true) {
                AchievementsCard(state.achievements, state.newAchievementKeys)
            }
            MotionEntrance(visibleState = staggeredCardVisible(1), slideFromBottom = true) {
                StreakCard(state)
            }
            MotionEntrance(visibleState = staggeredCardVisible(2), slideFromBottom = true) {
                TrendCard(state.weeklyTrend)
            }
            MotionEntrance(visibleState = staggeredCardVisible(3), slideFromBottom = true) {
                PeriodReportCard(state, onSelectPeriod, onInterpret, onOpenSettings)
            }
        }
    }
}

/** 卡片入场错峰间隔 */
private const val CARD_STAGGER_MILLIS = 60L

/**
 * 卡片入场可见态:MutableTransitionState 重载才能首帧就播(`visible = true` 首组合不播);
 * 关闭档初始即 true —— 截图引擎抓的是首帧静态帧,靠 LaunchedEffect 翻转会拍到空卡
 */
@Composable
private fun staggeredCardVisible(index: Int): MutableTransitionState<Boolean> {
    // LaunchedEffect 里不是 Composable 上下文,档位先取出来
    val level = LocalMotionLevel.current
    val visible = remember { MutableTransitionState(level == MotionLevel.OFF) }
    LaunchedEffect(Unit) {
        if (level == MotionLevel.OFF) return@LaunchedEffect
        delay(index * CARD_STAGGER_MILLIS)
        visible.targetState = true
    }
    return visible
}

@Composable
private fun StatsCard(titleRes: Int, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

// ---- 回顾 / 成就 ----

private fun achievementTextRes(key: String): Pair<Int, Int>? = when (key) {
    AchievementKeys.STREAK_7 -> R.string.ach_streak_7_title to R.string.ach_streak_7_desc
    AchievementKeys.STREAK_30 -> R.string.ach_streak_30_title to R.string.ach_streak_30_desc
    AchievementKeys.STREAK_100 -> R.string.ach_streak_100_title to R.string.ach_streak_100_desc
    AchievementKeys.TOTAL_10 -> R.string.ach_total_10_title to R.string.ach_total_10_desc
    AchievementKeys.TOTAL_50 -> R.string.ach_total_50_title to R.string.ach_total_50_desc
    AchievementKeys.TOTAL_100 -> R.string.ach_total_100_title to R.string.ach_total_100_desc
    AchievementKeys.TOTAL_500 -> R.string.ach_total_500_title to R.string.ach_total_500_desc
    AchievementKeys.TOTAL_1000 -> R.string.ach_total_1000_title to R.string.ach_total_1000_desc
    AchievementKeys.PERFECT_WEEK -> R.string.ach_perfect_week_title to R.string.ach_perfect_week_desc
    else -> null
}

private fun achievementIcon(key: String): ImageVector = when {
    key.startsWith("streak_") -> Icons.Filled.LocalFireDepartment
    key == AchievementKeys.PERFECT_WEEK -> Icons.Filled.DateRange
    else -> Icons.Filled.CheckCircle
}

@Composable
private fun AchievementsCard(achievements: List<Achievement>, newKeys: Set<String>) {
    StatsCard(R.string.stats_section_achievements) {
        if (achievements.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_achievements_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            achievements.forEach { ach ->
                val res = achievementTextRes(ach.key) ?: return@forEach
                val isNew = ach.key in newKeys
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = achievementIcon(ach.key),
                        contentDescription = null,
                        // 回顾式：已达成用弱化的色调，不用高亮色庆祝
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Spacing.space6),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(res.first), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(res.second),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isNew) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                stringResource(R.string.stats_new_badge),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- 连续天数 ----

@Composable
private fun StreakCard(state: StatsViewModel.UiState) {
    StatsCard(R.string.stats_section_streak) {
        if (state.streaks.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_streak_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.stats_streak_best, state.bestStreak),
                style = MaterialTheme.typography.headlineSmall,
            )
            state.streaks.forEach { streak ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        streak.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.stats_streak_current, streak.current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---- 完成率趋势（手写 Canvas 柱状图，无图表库） ----

@Composable
private fun TrendCard(trend: List<WeeklyCompletion>) {
    StatsCard(R.string.stats_section_trend) {
        if (trend.isEmpty()) return@StatsCard
        val noData = stringResource(R.string.stats_trend_no_data)
        // TalkBack 摘要：逐周报完成率，图形不可读时信息不丢
        val desc = stringResource(R.string.stats_trend_desc) + ": " +
            trend.joinToString(", ") { w ->
                val label = "${w.weekStart.monthValue}/${w.weekStart.dayOfMonth}"
                w.rate?.let { "$label ${(it * 100).roundToInt()}%" } ?: "$label $noData"
            }

        val barColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.outlineVariant
        val textColor = MaterialTheme.colorScheme.onSurfaceVariant
        val textPaint = remember(textColor) {
            android.graphics.Paint().apply {
                color = textColor.toArgb()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }

        Column(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = desc }) {
            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                val n = trend.size
                val slot = size.width / n
                val barWidth = slot * 0.45f
                val topPad = 18.dp.toPx() // 柱顶留给百分比文字
                textPaint.textSize = 10.sp.toPx()
                trend.forEachIndexed { i, w ->
                    val cx = slot * i + slot / 2
                    val rate = w.rate
                    if (rate == null) {
                        // 那周还没有记录：底部一截短横，占位但明确表示无数据
                        val y = size.height - 3.dp.toPx()
                        drawLine(
                            color = trackColor,
                            start = Offset(cx - barWidth / 2, y),
                            end = Offset(cx + barWidth / 2, y),
                            strokeWidth = 2.dp.toPx(),
                        )
                    } else {
                        val h = (size.height - topPad) * rate
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(cx - barWidth / 2, size.height - h),
                            size = Size(barWidth, h.coerceAtLeast(2.dp.toPx())),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                        // 柱顶标数值：不只靠颜色/高度传达信息
                        val pct = "${(rate * 100).roundToInt()}%"
                        drawContext.canvas.nativeCanvas.drawText(
                            pct, cx, size.height - h - 5.dp.toPx(), textPaint,
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                trend.forEach { w ->
                    Text(
                        text = "${w.weekStart.monthValue}/${w.weekStart.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---- 周期报告 + AI 解读 ----

@Composable
private fun periodLabelRes(period: StatsPeriod): Int = when (period) {
    StatsPeriod.THIS_WEEK -> R.string.stats_period_this_week
    StatsPeriod.LAST_WEEK -> R.string.stats_period_last_week
    StatsPeriod.THIS_MONTH -> R.string.stats_period_this_month
    StatsPeriod.LAST_MONTH -> R.string.stats_period_last_month
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodReportCard(
    state: StatsViewModel.UiState,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onInterpret: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val report = state.report ?: return
    val periodLabel = stringResource(periodLabelRes(state.selectedPeriod))
    StatsCard(R.string.stats_section_period) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
            StatsPeriod.entries.forEach { period ->
                FilterChip(
                    selected = state.selectedPeriod == period,
                    onClick = { onSelectPeriod(period) },
                    label = { Text(stringResource(periodLabelRes(period))) },
                )
            }
        }

        // 汇总行：总次数 + 每日完成率
        val summary = buildString {
            append(stringResource(R.string.stats_period_total_done, report.totalDone))
            append(" · ")
            report.completionRate?.let {
                append(stringResource(R.string.stats_period_rate, (it * 100).roundToInt()))
            } ?: append(stringResource(R.string.stats_period_no_daily))
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium)

        // 口径分布：图标 + 组名 + 次数（不只靠颜色区分口径）
        if (report.doneByLens.isNotEmpty()) {
            report.doneByLens.entries
                .sortedByDescending { it.value }
                .forEach { (lens, count) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        val tint = LocalLensColors.current.forLens(lens)
                            ?: MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(
                            imageVector = lensIcon(lens) ?: Icons.Filled.Category,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(Spacing.space4),
                        )
                        Text(
                            lensGroupTitle(lens),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text("$count", style = MaterialTheme.typography.bodyMedium)
                    }
                }
        }

        // 随手记
        Text(
            stringResource(R.string.stats_period_notes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (report.notes.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_period_no_notes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            report.notes.forEach { note -> NoteRow(note) }
        }

        InsightArea(state, periodLabel, onInterpret, onOpenSettings)
    }
}

@Composable
private fun NoteRow(note: CheckinNote) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.space1)) {
        Text(
            text = "${note.date} · ${note.entryTitle}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(note.note, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InsightArea(
    state: StatsViewModel.UiState,
    periodLabel: String,
    onInterpret: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val insight = state.insight
    when {
        // 没配 key：和聊天页同款提示，引导去设置而不是放个死按钮
        !state.aiConfigured -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.stats_ai_no_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.chat_go_settings))
            }
        }

        insight.failed -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.stats_ai_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onInterpret(periodLabel) }) {
                Text(stringResource(R.string.action_retry))
            }
        }

        !insight.requested -> OutlinedButton(
            onClick = { onInterpret(periodLabel) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.stats_ai_interpret)) }

        else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.space1)) {
            if (insight.streaming && insight.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_ai_interpreting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (insight.text.isNotEmpty()) {
                Text(insight.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
