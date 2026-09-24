// 条目库首页:33 章目录 + 顶部实时搜索(结果直达条目详情)
package com.betterlife.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    onOpenSection: (Int) -> Unit,
    onOpenEntry: (String) -> Unit,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.space4)) {
            Spacer(Modifier.height(Spacing.space3))
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.space3))
            OutlinedTextField(
                value = state.query,
                onValueChange = { vm.search(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.search("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
            )
            Spacer(Modifier.height(Spacing.space2))

            if (state.query.isNotBlank()) {
                // 搜索模式:显示匹配条目,点击直接进详情
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.space1)) {
                    if (state.searchResults.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.library_no_match),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.space4),
                            )
                        }
                    }
                    items(state.searchResults.size, key = { state.searchResults[it].entry.id }) { i ->
                        val entry = state.searchResults[i].entry
                        ListItem(
                            headlineContent = {
                                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                                    RatioBadge(entry.ratio)
                                    GradeBadge(entry.grade)
                                    if (entry.dispute) DisputeBadge()
                                }
                            },
                            overlineContent = { Text(entry.id) },
                            modifier = Modifier.clickable { onOpenEntry(entry.id) },
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                // 目录模式:章号 + 章名 + 条数
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.space1)) {
                    items(state.sections.size, key = { state.sections[it].n }) { i ->
                        val section = state.sections[i]
                        ListItem(
                            headlineContent = { Text(section.title) },
                            overlineContent = { Text(stringResource(R.string.library_chapter_overline, section.n)) },
                            trailingContent = {
                                Text(
                                    stringResource(R.string.library_entry_count, section.entries),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.clickable { onOpenSection(section.n) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
