// 待办页:每日习惯(DAILY)与一次性待办(ONCE)两个分区,支持打卡与删除
package com.betterlife.app.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.viewmodel.TodoViewModel

@Composable
fun TodoScreen(
    onOpenEntry: (String) -> Unit,
    vm: TodoViewModel = viewModel(factory = TodoViewModel.Factory),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val daily = items.filter { it.task.type == "DAILY" }
    val once = items.filter { it.task.type != "DAILY" }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("待办", style = MaterialTheme.typography.headlineSmall) }

            item { Text("每日习惯", style = MaterialTheme.typography.titleMedium) }
            if (daily.isEmpty()) {
                item {
                    Text(
                        "今天的都做完了",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(daily.size, key = { "d-${daily[it].task.taskId}" }) { i ->
                    TodoRow(
                        item = daily[i],
                        showDelete = false,
                        onToggle = { vm.toggle(daily[i].task) },
                        onDelete = {},
                        onClick = { daily[i].entry?.let { e -> onOpenEntry(e.id) } },
                    )
                }
            }

            item { Text("一次性待办", style = MaterialTheme.typography.titleMedium) }
            if (once.isEmpty()) {
                item {
                    Text(
                        "没有一次性待办。看到想做的事,点条目上的「加入待办」。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(once.size, key = { "o-${once[it].task.taskId}" }) { i ->
                    TodoRow(
                        item = once[i],
                        showDelete = true,
                        onToggle = { vm.toggle(once[i].task) },
                        onDelete = { vm.delete(once[i].task) },
                        onClick = { once[i].entry?.let { e -> onOpenEntry(e.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoRow(
    item: TodoViewModel.TodoItem,
    showDelete: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val task: TaskEntity = item.task
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (task.done) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
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
            if (showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
