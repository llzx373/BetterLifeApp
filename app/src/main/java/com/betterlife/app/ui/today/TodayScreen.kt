// 今日页:问候 + 今日任务打卡(含连续天数)+ 按口径分组的为你推荐 + AI 问答入口 FAB
package com.betterlife.app.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.data.EntryDto
import com.betterlife.app.recommend.ScoredEntry
import com.betterlife.app.ui.common.CostMeter
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.lensGroupTitle
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.LibraryViewModel
import com.betterlife.app.viewmodel.TodayViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    onOpenEntry: (String) -> Unit,
    onOpenChat: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenLibrary: () -> Unit,
    todayVm: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
    libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val todayState by todayVm.uiState.collectAsStateWithLifecycle()
    val libraryState by libraryVm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenChat) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.title_chat))
            }
        },
    ) { padding ->
        val state = todayState
        if (state is TodayViewModel.UiState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (state is TodayViewModel.UiState.Error) {
            ErrorState(
                onRetry = todayVm::retry,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        val items = (state as TodayViewModel.UiState.Ready).items

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            item { GreetingHeader() }

            item {
                Text(stringResource(R.string.today_tasks_title), style = MaterialTheme.typography.titleMedium)
            }
            if (items.isEmpty()) {
                item {
                    EmptyCard(
                        text = stringResource(R.string.today_tasks_empty),
                        actionText = stringResource(R.string.today_action_fill_profile),
                        onAction = onEditProfile,
                    )
                }
            } else {
                items(items.size) { i ->
                    val item = items[i]
                    DailyTaskCard(
                        item = item,
                        onToggle = { todayVm.toggleTask(item.task) },
                        onClick = { item.entry?.let { onOpenEntry(it.id) } },
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.today_recommend_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenLibrary) { Text(stringResource(R.string.today_all_entries)) }
                }
            }
            if (libraryState.recommended.isEmpty()) {
                item {
                    EmptyCard(
                        text = stringResource(R.string.today_recommend_empty),
                        actionText = stringResource(R.string.today_action_go_profile),
                        onAction = onEditProfile,
                    )
                }
            } else {
                libraryState.recommended.forEach { (lens, entries) ->
                    item(key = "lens-$lens") {
                        Text(
                            text = lensGroupTitle(lens),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(entries.size, key = { "rec-$lens-$it" }) { i ->
                        RecommendCard(
                            scored = entries[i],
                            onClick = { onOpenEntry(entries[i].entry.id) },
                            onAddTodo = { libraryVm.addToTodo(entries[i].entry.id) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(Spacing.space12)) } // 给 FAB 留位
        }
    }
}

@Composable
private fun GreetingHeader() {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..10 -> stringResource(R.string.today_greeting_morning)
        in 11..13 -> stringResource(R.string.today_greeting_noon)
        in 14..17 -> stringResource(R.string.today_greeting_afternoon)
        else -> stringResource(R.string.today_greeting_evening)
    }
    // 日期格式交给系统 locale，不再硬编码中文
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) }
    Column {
        Text(greeting, style = MaterialTheme.typography.headlineSmall)
        Text(
            date,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyTaskCard(
    item: TodayViewModel.TaskItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val done = item.task.done
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.entry?.title ?: item.task.entryId,
                    style = MaterialTheme.typography.titleSmall,
                )
                item.entry?.human?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.streak > 0) {
                    Text(
                        text = stringResource(R.string.today_streak_days, item.streak),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.space2))
            IconButton(onClick = onToggle) {
                // 打勾动画:完成时绿色对勾弹入,未完成显示空圈
                AnimatedVisibility(
                    visible = done,
                    enter = scaleIn() + fadeIn(),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.today_task_done_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                if (!done) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.today_task_todo_desc),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendCard(
    scored: ScoredEntry,
    onClick: () -> Unit,
    onAddTodo: () -> Unit,
) {
    val entry: EntryDto = scored.entry
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                    GradeBadge(entry.grade)
                    if (entry.dispute) DisputeBadge()
                }
                Spacer(Modifier.height(Spacing.space2))
                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                if (entry.human.isNotBlank()) {
                    Text(
                        text = entry.human,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.space2))
                CostMeter(entry)
            }
            IconButton(onClick = onAddTodo) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.today_add_todo_desc))
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String, actionText: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) { Text(actionText) }
        }
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
