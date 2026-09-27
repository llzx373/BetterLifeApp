// 章内条目列表:条目行占满中间,排序切换(性价比 / 证据等级 / 原书顺序)+ 筛选行置底,点击进详情
//
// 列表本体抽成 SectionListContent:单栏时它是整屏内容,双栏时它是右栏。
package com.betterlife.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.data.SectionDto
import com.betterlife.app.recommend.EntryFilter
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.common.SafeListItem
import com.betterlife.app.ui.common.TodoBadge
import com.betterlife.app.ui.common.lensGroupTitle
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.lensIcon
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
        SectionListContent(
            state = state,
            section = section,
            onSelectSort = vm::setSort,
            onSetFilter = vm::setFilter,
            onOpenEntry = onOpenEntry,
            modifier = Modifier.padding(padding),
        )
    }
}

/** 章内列表本体:章导语 + 条目行占满中间,排序切换 + 筛选行置底(拇指区)。单栏时它是整屏内容,双栏时它是右栏。 */
@Composable
internal fun SectionListContent(
    state: LibraryViewModel.UiState,
    section: SectionDto?,
    onSelectSort: (EntrySort) -> Unit,
    onSetFilter: (EntryFilter) -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.space4),
        ) {
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
            if (state.sectionEntries.isEmpty() && !state.filter.isEmpty) {
                item {
                    Text(
                        stringResource(R.string.filter_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.space4),
                    )
                }
            }
            items(state.sectionEntries, key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    onClick = { onOpenEntry(entry.id) },
                    // 排序切换时列表项 cross-fade + 位移,而不是硬跳(§6.2)
                    modifier = Modifier.animateItem(),
                )
            }
        }
        SortRow(selected = state.sort, onSelect = onSelectSort)
        FilterRow(filter = state.filter, onSetFilter = onSetFilter)
    }
}

/**
 * 筛选行:性价比 / 证据 / 口径 三组下拉多选,横向滚动保持紧凑。
 * 有激活条件时尾部出现「清空」;组标签上带已选数量。
 */
@Composable
private fun FilterRow(
    filter: EntryFilter,
    onSetFilter: (EntryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.space4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterMenuChip(
            label = stringResource(R.string.filter_ratio),
            activeCount = filter.ratios.size,
        ) {
            RatioOption.entries.forEach { option ->
                CheckMenuItem(
                    checked = option.value in filter.ratios,
                    label = stringResource(option.labelRes),
                    onClick = { onSetFilter(filter.toggleRatio(option.value)) },
                )
            }
        }
        FilterMenuChip(
            label = stringResource(R.string.filter_grade),
            activeCount = filter.grades.size,
        ) {
            listOf("A", "B", "C").forEach { grade ->
                CheckMenuItem(
                    checked = grade in filter.grades,
                    label = stringResource(R.string.grade_badge, grade),
                    onClick = { onSetFilter(filter.toggleGrade(grade)) },
                )
            }
        }
        FilterMenuChip(
            label = stringResource(R.string.filter_lens),
            activeCount = filter.lenses.size,
        ) {
            LENS_OPTIONS.forEach { lens ->
                CheckMenuItem(
                    checked = lens in filter.lenses,
                    label = lensGroupTitle(lens),
                    icon = lensIcon(lens),
                    onClick = { onSetFilter(filter.toggleLens(lens)) },
                )
            }
        }
        if (!filter.isEmpty) {
            TextButton(onClick = { onSetFilter(EntryFilter()) }) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
}

private val LENS_OPTIONS = listOf(
    EntryKeys.LENS_MORTALITY,
    EntryKeys.LENS_MONEY,
    EntryKeys.LENS_TIME,
    EntryKeys.LENS_FREEDOM,
)

private enum class RatioOption(val value: String, val labelRes: Int) {
    VERY_HIGH(EntryKeys.RATIO_VERY_HIGH, R.string.ratio_short_very_high),
    HIGH(EntryKeys.RATIO_HIGH, R.string.ratio_short_high),
    NORMAL(EntryKeys.RATIO_NORMAL, R.string.ratio_short_normal),
}

/** 一组筛选条件的入口:FilterChip + 下拉菜单;选中数量体现在标签上 */
@Composable
private fun FilterMenuChip(
    label: String,
    activeCount: Int,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = activeCount > 0,
            onClick = { expanded = true },
            label = {
                Text(
                    if (activeCount > 0) stringResource(R.string.filter_chip_active, label, activeCount)
                    else label,
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content()
        }
    }
}

/** 多选菜单项:点了不收起菜单,方便连续勾选 */
@Composable
private fun CheckMenuItem(
    checked: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = null)
                if (icon != null) {
                    Icon(icon, contentDescription = null)
                }
                Text(label, modifier = Modifier.padding(start = Spacing.space2))
            }
        },
        onClick = onClick,
    )
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
private fun EntryRow(entry: EntryDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SafeListItem(
        overlineContent = { Text(entry.id) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                RatioBadge(entry.ratio)
                GradeBadge(entry.grade)
                if (entry.dispute) DisputeBadge()
                if (entry.todo) TodoBadge()
            }
        },
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
