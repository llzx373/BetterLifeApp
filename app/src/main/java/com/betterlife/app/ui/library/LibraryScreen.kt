// 条目库首页:33 章目录(按主导口径着色 + 条数占比条) + 底部搜索(结果直达详情)
//
// 搜索框用 M3 搜索框的视觉语言(大圆角 + surfaceContainerHigh 容器 + 无下划线)实现。
// 没有直接用 SearchBar:alpha28 把它重构为基于 SearchBarState 的新 API 且去掉了 content 槽,
// 形态与「输入时就地出结果」不符,等它稳定后再换(见 docs/DESIGN_SYSTEM.md P2 执行记录)。
//
// 操作件(搜索框 / 章节选择器 / 排序 / 筛选)统一置底:拇指区在屏幕下方,
// 顶部只留标题等展示性内容。宽屏三档整体补 statusBarsPadding,否则顶栏内容
// 会被系统状态栏压住、点击被拦截(折叠屏/平板竖屏实测无法选章节)。
//
// 宽度分四档:<600dp 单栏 + 路由;600–839dp 双栏(目录 | 章内条目),详情仍走整屏路由;
// 840–1199dp 双栏(章内条目 | 条目详情),目录收成列表栏底部的章节选择器;
// ≥1200dp 三栏(目录 | 章内条目 | 条目详情),点条目不再跳页。
// 窄屏主形态零改动 —— 大屏适配不该以手机体验为代价。
package com.betterlife.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.R
import com.betterlife.app.ai.RetrievedEntry
import com.betterlife.app.data.SectionDto
import com.betterlife.app.recommend.EntryFilter
import com.betterlife.app.recommend.EntryStats
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.DoneBadge
import com.betterlife.app.ui.common.EntryStatsLine
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.PlannedBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.common.SafeListItem
import com.betterlife.app.ui.common.TodoBadge
import com.betterlife.app.ui.theme.LocalLensColors
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.lensIcon
import com.betterlife.app.viewmodel.EntrySort
import com.betterlife.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ShareBarWidth = 40.dp
private val ShareBarHeight = 4.dp

/** 超过这个宽度才有放两栏的余地(600dp 是 M3 的 medium 断点) */
internal val TWO_PANE_MIN_WIDTH = 600.dp

/** 超过这个宽度详情升级为常驻栏(840dp 是 M3 的 expanded 断点):章内条目 | 条目详情 */
internal val LIST_DETAIL_MIN_WIDTH = 840.dp

/** 超过这个宽度才有把目录也摆出来的余地;手机横屏刚过 840dp 摆三栏会挤成窄竖条 */
internal val THREE_PANE_MIN_WIDTH = 1200.dp

@Composable
fun LibraryScreen(
    initialLens: String? = null,
    onOpenSection: (Int) -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenArticleList: () -> Unit,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // 统计页完成度行带口径跳入:进库即带好该 lens 筛选,进任意章/搜索都生效,
    // 筛选行上的激活角标与「清空」是它的出口
    LaunchedEffect(initialLens) {
        if (initialLens != null) vm.setFilter(EntryFilter(lenses = setOf(initialLens)))
    }

    // 用 WindowInfo.containerSize 而不是 Configuration.screenWidthDp:后者在不同 targetSdk 下
    // inset 行为不同、而且被取整,分屏与折叠屏上会判错(Compose 自带 lint 也会报这条)。
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val width = with(density) { windowInfo.containerSize.width.toDp() }

    // 宽屏三档都是裸 ListDetailPaneScaffold,没有 Scaffold 接 insets,统一补状态栏边距,
    // 否则顶部控件(章节选择器 / 搜索框 / 目录标题)被系统状态栏压住、点击被拦截。
    // 只在 when 分支垫一层:pane 内部不再重复 pad。
    when {
        width >= THREE_PANE_MIN_WIDTH -> Box(Modifier.statusBarsPadding()) {
            LibraryThreePane(
                state = state,
                onQueryChange = vm::search,
                onSearchSubmit = vm::submitSearch,
                onSelectSection = vm::selectSection,
                onSelectSort = vm::setSort,
                onSetFilter = vm::setFilter,
                onSelectEntry = vm::selectEntry,
                onAddOnce = vm::addToTodo,
                onAddDaily = vm::addDaily,
                onAddWeekly = vm::addWeekly,
                onOpenChat = onOpenChat,
                onOpenArticle = onOpenArticle,
                onOpenArticleList = onOpenArticleList,
            )
        }
        width >= LIST_DETAIL_MIN_WIDTH -> Box(Modifier.statusBarsPadding()) {
            LibraryListDetail(
                state = state,
                onQueryChange = vm::search,
                onSearchSubmit = vm::submitSearch,
                onSelectSection = vm::selectSection,
                onSelectSort = vm::setSort,
                onSetFilter = vm::setFilter,
                onSelectEntry = vm::selectEntry,
                onAddOnce = vm::addToTodo,
                onAddDaily = vm::addDaily,
                onAddWeekly = vm::addWeekly,
                onOpenChat = onOpenChat,
                onOpenArticle = onOpenArticle,
                onOpenArticleList = onOpenArticleList,
            )
        }
        width >= TWO_PANE_MIN_WIDTH -> Box(Modifier.statusBarsPadding()) {
            LibraryTwoPane(
                state = state,
                onQueryChange = vm::search,
                onSearchSubmit = vm::submitSearch,
                onSelectSection = vm::selectSection,
                onSelectSort = vm::setSort,
                onSetFilter = vm::setFilter,
                onOpenEntry = onOpenEntry,
                onAddOnce = vm::addToTodo,
                onAddDaily = vm::addDaily,
                onAddWeekly = vm::addWeekly,
                onOpenArticle = onOpenArticle,
                onOpenArticleList = onOpenArticleList,
            )
        }
        else -> Scaffold { padding ->
            LibraryCatalogContent(
                state = state,
                onQueryChange = vm::search,
                onSearchSubmit = vm::submitSearch,
                onOpenSection = onOpenSection,
                onOpenEntry = onOpenEntry,
                onOpenArticleList = onOpenArticleList,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

/**
 * 大屏双栏(600–839dp):左栏目录、右栏选中章的条目。
 *
 * 两栏都常驻,所以点章不跳页 —— 只更新 `LibraryViewModel.selectedSection`。
 * 条目详情仍走整屏路由;≥840dp 时详情升级为常驻栏,见 [LibraryListDetail]。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun LibraryTwoPane(
    state: LibraryViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSelectSection: (Int) -> Unit,
    onSelectSort: (EntrySort) -> Unit,
    onSetFilter: (EntryFilter) -> Unit,
    onOpenEntry: (String) -> Unit,
    onAddOnce: (String) -> Unit,
    onAddDaily: (String) -> Unit,
    onAddWeekly: (String, Int) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenArticleList: () -> Unit,
) {
    // 600–839 落在 medium 宽度档,默认指令只给一栏 —— 目录(搜索/章节)会被详情栏顶掉,
    // 用户就没法选章。显式用 medium 也出两栏的指令,保持「两栏都常驻」的设计。
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>(
        scaffoldDirective = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
            currentWindowAdaptiveInfo(),
        ),
    )
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
                    onSearchSubmit = onSearchSubmit,
                    onOpenSection = onSelectSection,
                    onOpenEntry = onOpenEntry,
                    onOpenArticleList = onOpenArticleList,
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
                        onSetFilter = onSetFilter,
                        onOpenEntry = onOpenEntry,
                        onOpenArticle = onOpenArticle,
                        onAddOnce = onAddOnce,
                        onAddDaily = onAddDaily,
                        onAddWeekly = onAddWeekly,
                    )
                }
            }
        },
    )
}

/**
 * 宽屏双栏(840–1199dp):章内条目列表 | 条目详情。
 *
 * 目录不再占一栏,收成列表栏底部的章节选择器;点条目只更新
 * `LibraryViewModel.selectedEntryId`,不跳路由;未选中条目时右栏用 EmptyEntryPane 占位。
 * ≥1200dp 时目录独立成栏,见 [LibraryThreePane]。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun LibraryListDetail(
    state: LibraryViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSelectSection: (Int) -> Unit,
    onSelectSort: (EntrySort) -> Unit,
    onSetFilter: (EntryFilter) -> Unit,
    onSelectEntry: (String?) -> Unit,
    onAddOnce: (String) -> Unit,
    onAddDaily: (String) -> Unit,
    onAddWeekly: (String, Int) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenArticleList: () -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val section = state.sections.firstOrNull { it.n == state.selectedSection }
    val articleCount = rememberArticleCount()

    // 目录不在这个档位里,没选过章左栏就是空态 —— 默认选中第 1 章
    LaunchedEffect(state.sections, state.selectedSection) {
        if (state.selectedSection == null && state.sections.isNotEmpty()) {
            onSelectSection(state.sections.first().n)
        }
    }
    // 详情栏必须被列进 pane 值,否则宽屏上也只渲染左栏
    LaunchedEffect(state.selectedEntryId) {
        val entryId = state.selectedEntryId
        if (entryId != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entryId)
        } else if (navigator.canNavigateBack()) {
            // 选中被清掉(换章 / 不再推荐)且详情正独占一屏时,退回条目列表
            navigator.navigateBack()
        }
    }

    // 详情独占一屏(scaffold 自适应收窄)时,系统返回先退回条目列表
    BackHandler(navigator.canNavigateBack()) { scope.launch { navigator.navigateBack() } }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        listPane = {
            AnimatedPane {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        if (state.query.isNotBlank()) {
                            SearchResults(
                                results = state.searchResults,
                                totalHits = state.searchTotalHits,
                                doneIds = state.doneIds,
                                plannedIds = state.plannedIds,
                                onOpenEntry = onSelectEntry,
                            )
                        } else {
                            SectionListContent(
                                state = state,
                                section = section,
                                onSelectSort = onSelectSort,
                                onSetFilter = onSetFilter,
                                onOpenEntry = onSelectEntry,
                                onOpenArticle = onOpenArticle,
                                onAddOnce = onAddOnce,
                                onAddDaily = onAddDaily,
                                onAddWeekly = onAddWeekly,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.space2))
                    SectionPicker(
                        sections = state.sections,
                        selected = section,
                        onSelectSection = onSelectSection,
                        articleCount = articleCount,
                        onOpenArticleList = onOpenArticleList,
                    )
                    Spacer(Modifier.height(Spacing.space2))
                    SearchField(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        onSearchSubmit = onSearchSubmit,
                        modifier = Modifier.padding(horizontal = Spacing.space4),
                    )
                    Spacer(Modifier.height(Spacing.space3))
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val entryId = state.selectedEntryId
                if (entryId == null) {
                    EmptyEntryPane(stringResource(R.string.library_pick_entry_left))
                } else {
                    // 嵌在 pane 里,不传 predictiveBackTransition:pane 切换动画由 scaffold 管
                    EntryDetailContent(
                        entryId = entryId,
                        onExplain = onOpenChat,
                        onDismissed = { onSelectEntry(null) },
                        onOpenArticle = onOpenArticle,
                    )
                }
            }
        },
    )
}

/**
 * 超宽屏三栏(≥1200dp):目录 | 章内条目 | 条目详情。
 *
 * 实现是两个嵌套的 ListDetailPaneScaffold:外层分出「目录 | 其余」,内层再把「其余」
 * 分成「条目列表 | 详情」。点条目只更新 `LibraryViewModel.selectedEntryId`,不跳路由;
 * 「AI 解读」仍整屏跳到 ChatRoute。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun LibraryThreePane(
    state: LibraryViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSelectSection: (Int) -> Unit,
    onSelectSort: (EntrySort) -> Unit,
    onSetFilter: (EntryFilter) -> Unit,
    onSelectEntry: (String?) -> Unit,
    onAddOnce: (String) -> Unit,
    onAddDaily: (String) -> Unit,
    onAddWeekly: (String, Int) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenArticleList: () -> Unit,
) {
    val outerNavigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val innerNavigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val section = state.sections.firstOrNull { it.n == state.selectedSection }

    // 详情栏必须被列进 pane 值,否则宽屏上也只渲染左栏
    LaunchedEffect(state.selectedSection) {
        if (state.selectedSection != null) outerNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }
    LaunchedEffect(state.selectedEntryId) {
        val entryId = state.selectedEntryId
        if (entryId != null) {
            innerNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entryId)
        } else if (innerNavigator.canNavigateBack()) {
            // 选中被清掉(换章 / 不再推荐)且详情正独占一屏时,退回条目列表
            innerNavigator.navigateBack()
        }
    }

    // 内层 scaffold 的 BackHandler 组合在外层之后,返回键优先给内层:
    // 详情独占一屏时先退回条目列表,退无可退才轮到外层(详情 → 目录)。
    BackHandler(outerNavigator.canNavigateBack()) { scope.launch { outerNavigator.navigateBack() } }

    ListDetailPaneScaffold(
        directive = outerNavigator.scaffoldDirective,
        scaffoldState = outerNavigator.scaffoldState,
        listPane = {
            AnimatedPane {
                LibraryCatalogContent(
                    state = state,
                    onQueryChange = onQueryChange,
                    onSearchSubmit = onSearchSubmit,
                    onOpenSection = onSelectSection,
                    onOpenEntry = onSelectEntry,
                    onOpenArticleList = onOpenArticleList,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                BackHandler(innerNavigator.canNavigateBack()) {
                    scope.launch { innerNavigator.navigateBack() }
                }
                ListDetailPaneScaffold(
                    directive = innerNavigator.scaffoldDirective,
                    scaffoldState = innerNavigator.scaffoldState,
                    listPane = {
                        AnimatedPane {
                            if (section == null) {
                                EmptyDetailPane()
                            } else {
                                SectionListContent(
                                    state = state,
                                    section = section,
                                    onSelectSort = onSelectSort,
                                    onSetFilter = onSetFilter,
                                    onOpenEntry = onSelectEntry,
                                    onOpenArticle = onOpenArticle,
                                    onAddOnce = onAddOnce,
                                    onAddDaily = onAddDaily,
                                    onAddWeekly = onAddWeekly,
                                )
                            }
                        }
                    },
                    detailPane = {
                        AnimatedPane {
                            val entryId = state.selectedEntryId
                            if (entryId == null) {
                                EmptyEntryPane()
                            } else {
                                // 嵌在 pane 里,不传 predictiveBackTransition:pane 切换动画由 scaffold 管
                                EntryDetailContent(
                                    entryId = entryId,
                                    onExplain = onOpenChat,
                                    onDismissed = { onSelectEntry(null) },
                                    onOpenArticle = onOpenArticle,
                                )
                            }
                        }
                    },
                )
            }
        },
    )
}

/** 目录侧内容:标题在顶部,章节目录(或搜索结果)占满中间,最近搜索 + 搜索框置底。单栏时它是整屏,双栏/三栏时它是左栏。 */
@Composable
internal fun LibraryCatalogContent(
    state: LibraryViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onOpenSection: (Int) -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenArticleList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalEntries = remember(state.sections) {
        state.sections.sumOf { it.entries }.coerceAtLeast(1)
    }
    val articleCount = rememberArticleCount()

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(Spacing.space3))
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Spacing.space4),
        )
        Spacer(Modifier.height(Spacing.space3))

        Box(Modifier.weight(1f)) {
            if (state.query.isNotBlank()) {
                SearchResults(
                    results = state.searchResults,
                    totalHits = state.searchTotalHits,
                    doneIds = state.doneIds,
                    plannedIds = state.plannedIds,
                    onOpenEntry = onOpenEntry,
                )
            } else {
                Catalog(
                    sections = state.sections,
                    sectionLens = state.sectionLens,
                    sectionStats = state.sectionStats,
                    totalEntries = totalEntries,
                    articleCount = articleCount,
                    onOpenSection = onOpenSection,
                    onOpenArticleList = onOpenArticleList,
                )
            }
        }

        if (state.query.isBlank() && state.searchHistory.isNotEmpty()) {
            RecentSearches(
                history = state.searchHistory,
                onPick = { term ->
                    onQueryChange(term)
                    onSearchSubmit()
                },
            )
        }
        Spacer(Modifier.height(Spacing.space2))
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearchSubmit = onSearchSubmit,
            modifier = Modifier.padding(horizontal = Spacing.space4),
        )
        Spacer(Modifier.height(Spacing.space3))
    }
}

/** 长文篇数(目录「长文」入口与章节选择器共用);0 = 不显示入口 */
@Composable
private fun rememberArticleCount(): Int {
    val context = LocalContext.current
    val repo = (context.applicationContext as BetterLifeApp).container.entryRepository
    val count by produceState(0) {
        value = withContext(Dispatchers.IO) { repo.entriesData().articles.size }
    }
    return count
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
private fun EmptyEntryPane(text: String = stringResource(R.string.library_pick_entry)) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        singleLine = true,
        // 输入即搜照旧,IME 的搜索键是「提交」:这条词才会进历史
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
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

/** 章节选择器:840–1199dp 档里目录的替代形态(置底,贴近拇指区) —— 当前章标题按钮 + 全章节下拉(带每章条数);
 * 下拉末尾带「长文」项(articleCount > 0 时),否则这一档够不到长文列表页 */
@Composable
private fun SectionPicker(
    sections: List<SectionDto>,
    selected: SectionDto?,
    onSelectSection: (Int) -> Unit,
    articleCount: Int,
    onOpenArticleList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.padding(horizontal = Spacing.space4)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable { expanded = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space3),
            ) {
                Text(
                    text = selected?.let { stringResource(R.string.section_title, it.n, it.title) }
                        ?: stringResource(R.string.library_choose_chapter),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.library_choose_chapter),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sections.forEach { section ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.section_title, section.n, section.title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        Text(
                            stringResource(R.string.library_entry_count, section.entries),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectSection(section.n)
                    },
                )
            }
            if (articleCount > 0) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_articles_entry)) },
                    trailingIcon = {
                        Text(
                            stringResource(R.string.library_articles_count, articleCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onOpenArticleList()
                    },
                )
            }
        }
    }
}

/** 最近搜索:搜索框上方的一排可点词。样式跟目录区同一层级 —— 分段列表的世界里它是「轻标题 + 文字行」 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(history: List<String>, onPick: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.space4)) {
        Text(
            text = stringResource(R.string.library_recent_searches),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.space2))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
            verticalArrangement = Arrangement.spacedBy(Spacing.space1),
        ) {
            history.forEach { term ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.clip(MaterialTheme.shapes.extraLarge).clickable { onPick(term) },
                ) {
                    Text(
                        text = term,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Spacing.space3, vertical = Spacing.space1),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.space2))
    }
}

@Composable
private fun SearchResults(
    results: List<RetrievedEntry>,
    totalHits: Int,
    doneIds: Set<String>,
    plannedIds: Set<String>,
    onOpenEntry: (String) -> Unit,
) {
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
        // 命中数放结果顶部:topK 截断后「还有 N 条没显示」才有处得知
        item(key = "hit-count") {
            Text(
                text = if (totalHits > results.size) {
                    stringResource(R.string.library_search_hit_count_more, totalHits, results.size)
                } else {
                    stringResource(R.string.library_search_hit_count, totalHits)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space2),
            )
        }
        items(results, key = { it.entry.id }) { scored ->
            val entry = scored.entry
            SafeListItem(
                overlineContent = { Text(entry.id) },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                        RatioBadge(entry.ratio)
                        GradeBadge(entry.grade)
                        if (entry.dispute) DisputeBadge()
                        if (entry.todo) TodoBadge()
                        if (entry.id in doneIds) DoneBadge()
                        if (entry.id in plannedIds) PlannedBadge()
                    }
                },
                modifier = Modifier.clickable { onOpenEntry(entry.id) },
            ) {
                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 章目录:左边是这章的主导口径(图标 + 口径色),右边是它在全书里的分量 + 状态统计;
 * 全部章节之后跟一行「长文」入口(articleCount > 0 才显示),直达长文列表页 */
@Composable
private fun Catalog(
    sections: List<SectionDto>,
    sectionLens: Map<Int, String>,
    sectionStats: Map<Int, EntryStats>,
    totalEntries: Int,
    articleCount: Int,
    onOpenSection: (Int) -> Unit,
    onOpenArticleList: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.space4)) {
        items(sections, key = { it.n }) { section ->
            val lens = sectionLens[section.n].orEmpty()
            SafeListItem(
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
                        sectionStats[section.n]?.let { stats ->
                            Spacer(Modifier.height(Spacing.space1))
                            EntryStatsLine(stats)
                        }
                    }
                },
                modifier = Modifier.clickable { onOpenSection(section.n) },
            ) {
                Text(section.title)
            }
        }
        // 长文入口:篇数来自运行时内容库,空库(老库未补播等)不显示
        if (articleCount > 0) {
            item(key = "articles-entry") {
                SafeListItem(
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Spacing.space6),
                        )
                    },
                    overlineContent = {
                        Text(
                            stringResource(R.string.library_articles_overline),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    trailingContent = {
                        Text(
                            text = stringResource(R.string.library_articles_count, articleCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { onOpenArticleList() },
                ) {
                    Text(stringResource(R.string.library_articles_entry))
                }
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
