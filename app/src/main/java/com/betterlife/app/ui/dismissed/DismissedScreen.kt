// 已屏蔽的建议:长按推荐条目或详情页「不再推荐」屏蔽的条目,可在此恢复
package com.betterlife.app.ui.dismissed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.common.SafeListItem
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.DismissedViewModel

@Composable
fun DismissedScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    vm: DismissedViewModel = viewModel(factory = DismissedViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    DismissedContent(
        state = state,
        onBack = onBack,
        onRestore = vm::restore,
        onOpenEntry = onOpenEntry,
    )
}

/** 无状态内容：截图测试直接喂假状态渲染它，不需要 ViewModel / Room。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DismissedContent(
    state: DismissedViewModel.UiState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dismissed_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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

        if (state.entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.NotInterested,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = stringResource(R.string.dismissed_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.space3),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = Spacing.space2),
        ) {
            items(state.entries, key = { it.id }) { entry ->
                SafeListItem(
                    overlineContent = { Text(entry.id) },
                    supportingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                            RatioBadge(entry.ratio)
                            GradeBadge(entry.grade)
                            if (entry.dispute) DisputeBadge()
                        }
                    },
                    trailingContent = {
                        TextButton(onClick = { onRestore(entry.id) }) {
                            Text(stringResource(R.string.dismissed_action_restore))
                        }
                    },
                    // 恢复后行淡出并让位,而不是瞬间消失(§6.2)
                    modifier = Modifier.animateItem().clickable { onOpenEntry(entry.id) },
                ) {
                    Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
