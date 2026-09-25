// 条目库首页:33 章目录(按主导口径着色 + 条数占比条) + 顶部搜索(结果直达详情)
//
// 搜索框用 M3 搜索框的视觉语言(大圆角 + surfaceContainerHigh 容器 + 无下划线)实现。
// 没有直接用 SearchBar:alpha28 把它重构为基于 SearchBarState 的新 API 且去掉了 content 槽,
// 形态与「输入时就地出结果」不符,等它稳定后再换(见 docs/DESIGN_SYSTEM.md P2 执行记录)。
//
// ≥600dp 时切成 list-detail 双栏(左目录 / 右章内条目)。窄屏仍走原来的单栏 + 路由,
// 主形态零改动 —— 大屏适配不该以手机体验为代价。
package com.betterlife.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.ai.RetrievedEntry
import com.betterlife.app.data.SectionDto
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.theme.LocalLensColors
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.lensIcon
import com.betterlife.app.viewmodel.EntrySort
import com.betterlife.app.viewmodel.LibraryViewModel

private val ShareBarWidth = 40.dp
private val ShareBarHeight = 4.dp

/** 超过这个宽度才有放两栏的余地(600dp 是 M3 的 medium 断点) */
internal const val TWO_PANE_MIN_WIDTH = 600

@Composable
fun LibraryScreen(
    onOpenSection: (Int) -> Unit,
    onOpenEntry: (String) -> Unit,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    if (LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH) {
        LibraryTwoPane(
            state = state,
            onQueryChange = vm::search,
            onSelectSection = vm::selectSection,
            onSelectSort = vm::setSort,
            onOpenEntry = onOpenEntry,
        )
    } else {
        Scaffold { padding ->
            LibraryCatalogContent(
                state = state,
                onQueryChange = vm::search,
                onOpenSection = onOpenSection,
                onOpenEntry = onOpenEntry,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

/**
 * 大屏双栏:左栏目录、右栏选中章的条目。
 *
 * 两栏都常驻,所以点章不跳页 —— 只更新 `LibraryViewModel.selectedSection`。
 * 条目详情仍走整屏路由(第三栏见 docs/DESIGN_SYSTEM.md 的说明)。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun LibraryTwoPane(
    state: LibraryViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onSelectSection: (Int) -> Unit,
    onSelectSort: (EntrySort) -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val section = state.sections.firstOrNull { it.n == state.selectedSection }

    // 详情栏必须被列进 pane 值,否则宽屏上也只渲染左栏
    LaunchedEffect(state.selectedSection) {
        if (state.selectedSection != null) navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        listPane = {
            AnimatedPane {
                LibraryCatalogContent(
                    state = state,
                    onQueryChange = onQueryChange,
                    onOpenSection = onSelectSection,
                    onOpenEntry = onOpenEntry,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                if (section == null) {
                    EmptyDetailPane()
                } else {
                    SectionListContent(
                        state = state,
                        section = section,
                        onSelectSort = onSelectSort,
                        onOpenEntry = onOpenEntry,
                    )
                }
            }
        },
    )
}

/** 目录侧内容:标题 + 搜索 + 章节目录(或搜索结果)。单栏时它是整屏,双栏时它是左栏。 */
@Composable
internal fun LibraryCatalogContent(
    state: LibraryViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onOpenSection: (Int) -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalEntries = remember(state.sections) {
        state.sections.sumOf { it.entries }.coerceAtLeast(1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(Spacing.space3))
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Spacing.space4),
        )
        Spacer(Modifier.height(Spacing.space3))
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = Spacing.space4),
        )
        Spacer(Modifier.height(Spacing.space2))

        if (state.query.isNotBlank()) {
            SearchResults(results = state.searchResults, onOpenEntry = onOpenEntry)
        } else {
            Catalog(
                sections = state.sections,
                sectionLens = state.sectionLens,
                totalEntries = totalEntries,
                onOpenSection = onOpenSection,
            )
        }
    }
}

@Composable
private fun EmptyDetailPane() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.library_pick_chapter),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
    )
}

@Composable
private fun SearchResults(results: List<RetrievedEntry>, onOpenEntry: (String) -> Unit) {
    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.library_no_match),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space4),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.space4)) {
        items(results, key = { it.entry.id }) { scored ->
            val entry = scored.entry
            ListItem(
                overlineContent = { Text(entry.id) },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                        RatioBadge(entry.ratio)
                        GradeBadge(entry.grade)
                        if (entry.dispute) DisputeBadge()
                    }
                },
                modifier = Modifier.clickable { onOpenEntry(entry.id) },
            ) {
                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 章目录:左边是这章的主导口径(图标 + 口径色),右边是它在全书里的分量 */
@Composable
private fun Catalog(
    sections: List<SectionDto>,
    sectionLens: Map<Int, String>,
    totalEntries: Int,
    onOpenSection: (Int) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.space4)) {
        items(sections, key = { it.n }) { section ->
            val lens = sectionLens[section.n].orEmpty()
            ListItem(
                leadingContent = { LensMark(lens) },
                overlineContent = {
                    Text(
                        stringResource(R.string.library_chapter_overline, section.n),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.library_entry_count, section.entries),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.space1))
                        ShareBar(
                            fraction = section.entries.toFloat() / totalEntries,
                            color = lensTint(lens),
                        )
                    }
                },
                modifier = Modifier.clickable { onOpenSection(section.n) },
            ) {
                Text(section.title)
            }
        }
    }
}

@Composable
private fun LensMark(lens: String) {
    val tint = lensTint(lens)
    val icon = lensIcon(lens)
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Spacing.space6))
    } else {
        Box(Modifier.size(Spacing.space2).clip(CircleShape).background(tint))
    }
}

/** 条数占比条:极简,只表达「这章在全书里的分量」 */
@Composable
private fun ShareBar(fraction: Float, color: Color) {
    Box(
        Modifier
            .size(width = ShareBarWidth, height = ShareBarHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun lensTint(lens: String): Color =
    LocalLensColors.current.forLens(lens) ?: MaterialTheme.colorScheme.outline
