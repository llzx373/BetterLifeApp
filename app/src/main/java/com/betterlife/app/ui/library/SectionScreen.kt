// 章内条目列表:序号 + 标题 + 性价比/证据等级徽标,点击进详情
package com.betterlife.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.betterlife.app.ui.common.TodoBadge
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            section?.intro?.takeIf { it.isNotBlank() }?.let { intro ->
                item {
                    Text(
                        intro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.sectionEntries.size, key = { state.sectionEntries[it].id }) { i ->
                val entry = state.sectionEntries[i]
                ListItem(
                    headlineContent = {
                        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RatioBadge(entry.ratio)
                            GradeBadge(entry.grade)
                            if (entry.dispute) DisputeBadge()
                            if (entry.todo) TodoBadge()
                        }
                    },
                    overlineContent = { Text(entry.id) },
                    modifier = Modifier.clickable { onOpenEntry(entry.id) },
                )
                HorizontalDivider()
            }
        }
    }
}
