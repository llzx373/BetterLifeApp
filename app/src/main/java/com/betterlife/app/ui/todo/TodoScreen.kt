// 待办页:每日习惯(圆形勾选 + 分区进度)与一次性待办(方框勾选 + 可撤销删除)两个分区
package com.betterlife.app.ui.todo

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.ui.theme.LocalLensColors
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.TodoViewModel
import kotlinx.coroutines.launch

private val ProgressRingSize = 20.dp
private val ProgressRingStroke = 3.dp

@Composable
fun TodoScreen(
    onOpenEntry: (String) -> Unit,
    vm: TodoViewModel = viewModel(factory = TodoViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            item {
                Text(
                    stringResource(R.string.todo_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = Spacing.space2),
                )
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
                        onToggle = { vm.toggle(item.task) },
                        onClick = { item.entry?.let { e -> onOpenEntry(e.id) } },
                    )
                }
            }

            item {
                SectionHeader(
                    titleRes = R.string.todo_once_title,
                    modifier = Modifier.padding(top = Spacing.space4),
                )
            }
            if (state.once.isEmpty()) {
                item { EmptyHint(R.string.todo_once_empty) }
            } else {
                items(state.once, key = { "o-${it.task.taskId}" }) { item ->
                    OnceRow(
                        item = item,
                        onToggle = { vm.toggle(item.task) },
                        onClick = { item.entry?.let { e -> onOpenEntry(e.id) } },
                        onDelete = {
                            vm.delete(item.task)
                            scope.launch {
                                val message = context.getString(
                                    R.string.todo_deleted,
                                    item.entry?.title ?: item.task.entryId,
                                )
                                val result = snackbar.showSnackbar(
                                    message = message,
                                    actionLabel = undoLabel,
                                    withDismissAction = true,
                                )
                                if (result == SnackbarResult.ActionPerformed) vm.restore(item)
                            }
                        },
                    )
                }
            }
        }
    }
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

/** 每日习惯:圆形勾选 + 口径色,与一次性待办的方框区分 */
@Composable
private fun HabitRow(
    item: TodoViewModel.TodoItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val task = item.task
    val lensColors = LocalLensColors.current
    val tint = item.entry?.lens?.let { lensColors.forLens(it) } ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (task.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = stringResource(
                    if (task.done) R.string.todo_habit_done_desc else R.string.todo_habit_todo_desc,
                ),
                tint = if (task.done) tint else MaterialTheme.colorScheme.outline,
            )
        }
        TaskText(item = item, modifier = Modifier.weight(1f))
    }
}

/** 一次性待办:方框勾选 + 可撤销的删除 */
@Composable
private fun OnceRow(
    item: TodoViewModel.TodoItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val task = item.task
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                uncheckedColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        TaskText(item = item, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskText(item: TodoViewModel.TodoItem, modifier: Modifier = Modifier) {
    val task = item.task
    Column(modifier = modifier.padding(vertical = Spacing.space2)) {
        Text(
            text = item.entry?.title ?: task.entryId,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
        )
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
