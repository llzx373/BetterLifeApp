// 条目库的屏级截图基线。
//
// 重点是宽屏分档:600–839dp 双栏(目录 | 章内条目)、840–1199dp 双栏(章内条目 | 条目详情)、
// ≥1200dp 三栏。每个档位都得有一张图来证明 ——
// 光靠读代码看不出 ListDetailPaneScaffold 到底有没有把详情栏渲染出来。
package com.betterlife.app.ui.library

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.data.SectionDto
import com.betterlife.app.ui.PreviewScreen
import com.betterlife.app.ui.fakeEntry
import com.betterlife.app.viewmodel.EntrySort
import com.betterlife.app.viewmodel.LibraryViewModel

private val sections = listOf(
    SectionDto(n = 1, title = "先保命：把最坏的情况挡住", entries = 24),
    SectionDto(n = 2, title = "吃的盐、油和糖", entries = 31),
    SectionDto(n = 6, title = "守住钱：别让一次意外掏空", entries = 28),
    SectionDto(n = 8, title = "法律与合同里最容易踩的坑", entries = 19),
    SectionDto(n = 11, title = "动起来：最低有效剂量", entries = 22),
    SectionDto(n = 13, title = "睡够：把睡眠当成基础设施", entries = 17),
)

private val sectionLens = mapOf(
    1 to EntryKeys.LENS_MORTALITY,
    2 to EntryKeys.LENS_MORTALITY,
    6 to EntryKeys.LENS_MONEY,
    8 to EntryKeys.LENS_FREEDOM,
    11 to EntryKeys.LENS_MORTALITY,
    13 to EntryKeys.LENS_TIME,
)

private val sectionEntries = listOf(
    fakeEntry(id = "02-01", sec = 2, title = "把家里的食盐换成低钠盐", lens = EntryKeys.LENS_MORTALITY),
    fakeEntry(
        id = "02-04",
        sec = 2,
        title = "炒菜用油换小瓶装，别放在灶台边",
        lens = EntryKeys.LENS_MORTALITY,
        ratio = EntryKeys.RATIO_HIGH,
    ),
    fakeEntry(
        id = "02-07",
        sec = 2,
        title = "含糖饮料改成白水或茶",
        lens = EntryKeys.LENS_MORTALITY,
        level = EntryKeys.GAIN_MID,
        ratio = EntryKeys.RATIO_HIGH,
        grade = "B",
    ),
    fakeEntry(
        id = "02-11",
        sec = 2,
        title = "买包装食品先看配料表的前三位",
        lens = EntryKeys.LENS_MONEY,
        money = EntryKeys.COST_LESS,
        level = EntryKeys.GAIN_SMALL,
        ratio = EntryKeys.RATIO_NORMAL,
        grade = "C",
    ),
)

private val twoPaneState = LibraryViewModel.UiState(
    sections = sections,
    sectionLens = sectionLens,
    selectedSection = 2,
    sectionEntries = sectionEntries,
    sort = EntrySort.RATIO,
)

private val compactState = twoPaneState.copy(selectedSection = null, sectionEntries = emptyList())

private const val PHONE_WIDTH = 412
private const val TABLET_WIDTH = 900
private const val LIST_DETAIL_WIDTH = 840
private const val THREE_PANE_WIDTH = 1200

@PreviewTest
@Preview(name = "list-detail-light", widthDp = LIST_DETAIL_WIDTH, heightDp = 700)
@Preview(
    name = "list-detail-dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = LIST_DETAIL_WIDTH,
    heightDp = 700,
)
@Composable
fun LibraryListDetailExpanded() {
    PreviewScreen {
        LibraryListDetail(
            state = twoPaneState,
            onQueryChange = {},
            onSearchSubmit = {},
            onSelectSection = {},
            onSelectSort = {},
            onSetFilter = {},
            onSelectEntry = {},
            onOpenChat = {},
        )
    }
}

@PreviewTest
@Preview(name = "three-pane-light", widthDp = THREE_PANE_WIDTH, heightDp = 800)
@Preview(
    name = "three-pane-dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = THREE_PANE_WIDTH,
    heightDp = 800,
)
@Composable
fun LibraryThreePaneExpanded() {
    PreviewScreen {
        LibraryThreePane(
            state = twoPaneState,
            onQueryChange = {},
            onSearchSubmit = {},
            onSelectSection = {},
            onSelectSort = {},
            onSetFilter = {},
            onSelectEntry = {},
            onOpenChat = {},
        )
    }
}

@PreviewTest
@Preview(name = "two-pane-light", widthDp = TABLET_WIDTH, heightDp = 700)
@Preview(
    name = "two-pane-dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = TABLET_WIDTH,
    heightDp = 700,
)
@Composable
fun LibraryTwoPaneExpanded() {
    PreviewScreen {
        LibraryTwoPane(
            state = twoPaneState,
            onQueryChange = {},
            onSearchSubmit = {},
            onSelectSection = {},
            onSelectSort = {},
            onSetFilter = {},
            onOpenEntry = {},
        )
    }
}

@PreviewTest
@Preview(name = "compact-light", widthDp = PHONE_WIDTH, heightDp = 915)
@Preview(
    name = "compact-dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = PHONE_WIDTH,
    heightDp = 915,
)
@Composable
fun LibraryCatalogCompact() {
    PreviewScreen {
        LibraryCatalogContent(
            state = compactState,
            onQueryChange = {},
            onSearchSubmit = {},
            onOpenSection = {},
            onOpenEntry = {},
        )
    }
}
