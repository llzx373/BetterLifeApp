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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.data.EntryDto
import com.betterlife.app.recommend.ScoredEntry
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.common.lensGroupTitle
import com.betterlife.app.viewmodel.LibraryViewModel
import com.betterlife.app.viewmodel.TodayViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "AI 问答")
            }
        },
    ) { padding ->
        if (todayState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { GreetingHeader() }

            item {
                Text("今日任务", style = MaterialTheme.typography.titleMedium)
            }
            if (todayState.items.isEmpty()) {
                item {
                    EmptyCard(
                        text = "还没有今日任务。先完善档案,或去条目库逛逛。",
                        actionText = "完善档案",
                        onAction = onEditProfile,
                    )
                }
            } else {
                items(todayState.items.size) { i ->
                    val item = todayState.items[i]
                    DailyTaskCard(
                        item = item,
                        onToggle = { todayVm.toggleTask(item.task) },
                        onClick = { item.entry?.let { onOpenEntry(it.id) } },
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("为你推荐", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenLibrary) { Text("全部条目") }
                }
            }
            if (libraryState.recommended.isEmpty()) {
                item {
                    EmptyCard(
                        text = "填一份档案,推荐会更准;也可以先随便看看。",
                        actionText = "去填档案",
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

            item { Spacer(Modifier.height(72.dp)) } // 给 FAB 留位
        }
    }
}

@Composable
private fun GreetingHeader() {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }
    val date = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())
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
            modifier = Modifier.padding(12.dp),
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
                        text = "已连续 ${item.streak} 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onToggle) {
                // 打勾动画:完成时绿色对勾弹入,未完成显示空圈
                AnimatedVisibility(
                    visible = done,
                    enter = scaleIn() + fadeIn(),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "已完成,点击取消",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                if (!done) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "打卡",
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
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RatioBadge(entry.ratio)
                    GradeBadge(entry.grade)
                    if (entry.dispute) DisputeBadge()
                }
                Spacer(Modifier.height(6.dp))
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
            }
            IconButton(onClick = onAddTodo) {
                Icon(Icons.Filled.Add, contentDescription = "加入待办")
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
            modifier = Modifier.padding(16.dp),
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
