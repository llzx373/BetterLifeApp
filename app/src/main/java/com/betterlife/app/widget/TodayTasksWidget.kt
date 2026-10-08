package com.betterlife.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.MainActivity
import com.betterlife.app.R
import com.betterlife.app.recommend.ProfileQuestions
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * B5 桌面小部件：今日任务一览 + 一键打卡。
 *
 * 数据直接读 Room（与应用同进程/同库），标题解析与 TodayViewModel 一致：
 * 条目库 title → custom_entries title → entryId 兜底。每次渲染前幂等地
 * ensureTodayTasks，避免跨夜后小部件停在空列表。
 *
 * 刷新触发：
 * - 小部件上的打卡/撤销（[CheckInAction]）；
 * - 每日提醒 worker（DailyReminderWorker）跑完后 updateAll；
 * - UI 侧手动打卡、删除等改动后由 TodayViewModel 调用 [WidgetUpdater.refresh]。
 */
class TodayTasksWidget : GlanceAppWidget() {

    /** 一行任务的展示模型 */
    private data class TaskRow(val taskId: Long, val title: String, val done: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as BetterLifeApp).container
        val date = LocalDate.now()
        // 新的一天先按档案规划今日任务（幂等）。N1：没档案用空档案也能规划；
        // 还没看过引导（装完未打开）就保持空态，引导打开应用
        if (runCatching { container.settingsStore.current().onboardingDone }.getOrDefault(false)) {
            val profile = container.profileRepository.getProfile()
            val answered = runCatching {
                container.settingsStore.profileQuestionsAnsweredFlow.first()
            }.getOrDefault(emptySet())
            runCatching {
                container.taskManager.ensureTodayTasks(ProfileQuestions.effectiveProfile(profile, answered), date)
            }
        }
        val tasks = runCatching {
            container.taskManager.todayTasksFlow(date).first()
        }.getOrNull().orEmpty()
        val entries = runCatching { container.entryRepository.entriesData() }.getOrNull()
        val customTitles = runCatching {
            container.database.customEntryDao().all().associate { it.entryId to it.title }
        }.getOrNull().orEmpty()
        val rows = tasks.map { task ->
            TaskRow(
                taskId = task.taskId,
                title = entries?.byId?.get(task.entryId)?.title
                    ?: customTitles[task.entryId]
                    ?: task.entryId,
                done = task.done,
            )
        }

        val headerTitle = context.getString(R.string.today_tasks_title)
        val doneCount = rows.count { it.done }
        val progress = context.getString(R.string.todo_daily_progress, doneCount, rows.size)
        val allDoneText = context.getString(R.string.widget_all_done)
        val noTasksText = context.getString(R.string.widget_no_tasks)

        provideContent {
            GlanceTheme {
                WidgetContent(headerTitle, progress, rows, allDoneText, noTasksText)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        headerTitle: String,
        progress: String,
        rows: List<TaskRow>,
        allDoneText: String,
        noTasksText: String,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 标题栏：点击打开应用（落在今日页）
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headerTitle,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (rows.isNotEmpty()) {
                    Text(
                        text = progress,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                    )
                }
            }
            when {
                rows.isEmpty() -> EmptyState(noTasksText)
                rows.all { it.done } -> EmptyState(allDoneText)
                else -> TaskList(rows)
            }
        }
    }

    @Composable
    private fun EmptyState(message: String) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 2,
            )
        }
    }

    @Composable
    private fun TaskList(rows: List<TaskRow>) {
        val context = LocalContext.current
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(rows, itemId = { it.taskId }) { row ->
                val cd = context.getString(
                    if (row.done) R.string.widget_task_undo_desc else R.string.widget_task_checkin_desc,
                    row.title,
                )
                Row(
                    // ≥48dp 触控目标（无障碍要求）
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .semantics { contentDescription = cd }
                        .clickable(
                            actionRunCallback<CheckInAction>(
                                actionParametersOf(CheckInAction.TaskIdKey to row.taskId)
                            )
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (row.done) "✓" else "○",
                        style = TextStyle(
                            color = if (row.done) {
                                GlanceTheme.colors.primary
                            } else {
                                GlanceTheme.colors.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = row.title,
                        style = TextStyle(
                            color = if (row.done) {
                                GlanceTheme.colors.onSurfaceVariant
                            } else {
                                GlanceTheme.colors.onSurface
                            },
                            textDecoration = if (row.done) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 小部件行点击：未完成 → 打卡（doneBy = "widget"）；已完成 → 撤销（与应用内今日页一致） */
class CheckInAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[TaskIdKey] ?: return
        val container = (context.applicationContext as BetterLifeApp).container
        val task = container.taskManager.getTask(taskId) ?: return
        // 跨午夜后小部件可能还渲染着昨日任务行（点击参数还是当时的 taskId）：日期对不上
        // 就不打卡——打了等于静默补昨天的卡；只刷新，让 provideGlance 拉今日数据
        if (task.date != LocalDate.now().toString()) {
            TodayTasksWidget().update(context, glanceId)
            return
        }
        if (task.done) {
            container.taskManager.uncompleteTask(taskId)
        } else {
            container.taskManager.completeTask(taskId, doneBy = DONE_BY_WIDGET)
        }
        TodayTasksWidget().update(context, glanceId)
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("taskId")

        /** 打卡来源标记：与手动 "manual"、自动核销区分开 */
        const val DONE_BY_WIDGET = "widget"
    }
}

/**
 * 任务数据变化后刷新所有「今日任务」小部件实例；尽力而为，失败静默。
 * TaskManager 不持有 Context，由调用方（worker / ViewModel）在改动成功后调用。
 */
object WidgetUpdater {
    suspend fun refresh(context: Context) {
        runCatching { TodayTasksWidget().updateAll(context) }
    }
}
