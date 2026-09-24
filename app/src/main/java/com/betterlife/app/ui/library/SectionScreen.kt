// 章内条目列表:顶部排序切换(性价比 / 证据等级 / 原书顺序) + 条目行,点击进详情
package com.betterlife.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.data.EntryDto
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.common.TodoBadge
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.EntrySort
import com.betterlife.app.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(
    sectionN: Int,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sectionN) { vm.selectSection(sectionN) }

    val section = state.sections.firstOrNull { it.n == sectionN }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        section?.let { stringResource(R.string.section_title, it.n, it.title) }
                            ?: stringResource(R.string.section_title_short, sectionN),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SortRow(selected = state.sort, onSelect = vm::setSort)

            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.space4)) {
                section?.intro?.takeIf { it.isNotBlank() }?.let { intro ->
                    item {
                        Text(
                            intro,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space2),
                        )
                    }
                }
                items(state.sectionEntries, key = { it.id }) { entry ->
                    EntryRow(entry = entry, onClick = { onOpenEntry(entry.id) })
                }
            }
        }
    }
}

/** 排序切换:选中态同时靠形状与填充区分,不只靠颜色 */
@Composable
private fun SortRow(
    selected: EntrySort,
    onSelect: (EntrySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = EntrySort.entries
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space4, vertical = Spacing.space2),
    ) {
        options.forEachIndexed { index, sort ->
            SegmentedButton(
                selected = selected == sort,
                onClick = { onSelect(sort) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(sortLabel(sort)), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun sortLabel(sort: EntrySort): Int = when (sort) {
    EntrySort.RATIO -> R.string.library_sort_ratio
    EntrySort.GRADE -> R.string.library_sort_grade
    EntrySort.ORDER -> R.string.library_sort_order
}

@Composable
private fun EntryRow(entry: EntryDto, onClick: () -> Unit) {
    ListItem(
        overlineContent = { Text(entry.id) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                RatioBadge(entry.ratio)
                GradeBadge(entry.grade)
                if (entry.dispute) DisputeBadge()
                if (entry.todo) TodoBadge()
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
