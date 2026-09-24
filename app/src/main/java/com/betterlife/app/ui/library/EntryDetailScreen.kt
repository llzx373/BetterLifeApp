// 条目详情:首屏给决策信息(成本/收益/性价比),次要信息分层,长尾信息折叠。
//
// 分级(见 docs/DESIGN_SYSTEM.md §5.2):
//  首屏必显 —— 徽标 + 标题 + CostMeter + 说人话 + 收益
//  次屏     —— 证据等级、成本明细
//  折叠     —— 来源、备注
// 底部动作是悬浮工具栏(加入待办 / 复制 / 分享),工具栏自带 WindowInsets 处理。
package com.betterlife.app.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.R
import com.betterlife.app.data.EntryDto
import com.betterlife.app.ui.common.CostMeter
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.common.TodoBadge
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val URL_REGEX = Regex("""https?://[^\s)<>"']+""")

/** 工具栏悬浮在内容之上,底部留出避让高度 */
private val TOOLBAR_CLEARANCE = 96.dp

private sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Found(val entry: EntryDto) : DetailState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as BetterLifeApp).container.entryRepository
    val state by produceState<DetailState>(DetailState.Loading, entryId) {
        val found = withContext(Dispatchers.IO) { repo.entriesData().byId[entryId] }
        value = if (found == null) DetailState.Missing else DetailState.Found(found)
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addedTodoMessage = stringResource(R.string.detail_added_todo)
    val copiedMessage = stringResource(R.string.detail_copied)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entryId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = state
        if (current !is DetailState.Found) {
            Text(
                text = stringResource(
                    if (current is DetailState.Loading) R.string.detail_loading else R.string.detail_missing,
                ),
                modifier = Modifier.padding(padding).padding(Spacing.space4),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }
        val e = current.entry

        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.space4,
                    end = Spacing.space4,
                    top = Spacing.space4,
                    bottom = TOOLBAR_CLEARANCE,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.space4),
            ) {
                item { HeroSection(e) }

                if (e.dispute || e.todo) {
                    item { NoticeSection(e) }
                }

                if (e.human.isNotBlank()) {
                    item {
                        DetailBlock(titleRes = R.string.detail_section_human) {
                            Text(e.human, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                if (e.gain.isNotBlank()) {
                    item {
                        DetailBlock(titleRes = R.string.detail_section_gain) {
                            Text(e.gain, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item { GradeSection(e.grade) }

                item {
                    CollapsibleSection(
                        title = stringResource(R.string.detail_section_cost),
                        initiallyExpanded = false,
                    ) {
                        CostRow(R.string.detail_cost_money, e.money)
                        CostRow(R.string.detail_cost_time, e.time)
                        CostRow(R.string.detail_cost_will, e.will)
                    }
                }

                if (e.src.isNotBlank()) {
                    item {
                        CollapsibleSection(
                            title = stringResource(R.string.detail_section_source),
                            initiallyExpanded = false,
                        ) {
                            SourceText(e.src)
                        }
                    }
                }

                if (e.note.isNotBlank()) {
                    item {
                        CollapsibleSection(
                            title = stringResource(R.string.detail_section_note),
                            initiallyExpanded = false,
                        ) {
                            Text(e.note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.align(Alignment.BottomCenter),
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            ) {
                ToolbarAction(
                    icon = Icons.Filled.Add,
                    labelRes = R.string.detail_add_todo,
                    onClick = {
                        vm.addToTodo(e.id)
                        scope.launch { snackbar.showSnackbar(addedTodoMessage) }
                    },
                )
                ToolbarAction(
                    icon = Icons.Filled.ContentCopy,
                    labelRes = R.string.detail_copy,
                    onClick = {
                        copyToClipboard(context, e.shareText())
                        scope.launch { snackbar.showSnackbar(copiedMessage) }
                    },
                )
                ToolbarAction(
                    icon = Icons.Filled.Share,
                    labelRes = R.string.detail_share,
                    onClick = { shareEntry(context, e.shareText()) },
                )
            }
        }
    }
}

/** 首屏决策信息:徽标、标题、性价比图形 */
@Composable
private fun HeroSection(entry: EntryDto, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
            RatioBadge(entry.ratio)
            GradeBadge(entry.grade)
            if (entry.dispute) DisputeBadge()
            if (entry.todo) TodoBadge()
        }
        Spacer(Modifier.height(Spacing.space2))
        Text(entry.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.space4))
        CostMeter(entry)
        if (entry.level.isNotBlank()) {
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = stringResource(R.string.detail_impact, entry.level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 争议与待核实是两条独立信息,分开陈述,不再用 append 拼成一段 */
@Composable
private fun NoticeSection(entry: EntryDto, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        if (entry.dispute) {
            NoticeRow(
                textRes = R.string.detail_dispute_notice,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (entry.todo) {
            NoticeRow(
                textRes = R.string.detail_todo_notice,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun NoticeRow(textRes: Int, container: Color, content: Color) {
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(Spacing.space3), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Spacer(Modifier.width(Spacing.space2))
            Text(stringResource(textRes), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 次屏信息:无卡片的文字块,靠字号和间距分层 */
@Composable
private fun DetailBlock(titleRes: Int, content: @Composable () -> Unit) {
    Column {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.space1))
        content()
    }
}

/** 证据等级:把 A/B/C 的含义讲清楚,不只是一个字母 */
@Composable
private fun GradeSection(grade: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.space3)) {
            Text(
                text = stringResource(R.string.detail_section_grade),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.space1))
            Text(
                text = when (grade) {
                    "A" -> stringResource(R.string.detail_grade_a)
                    "B" -> stringResource(R.string.detail_grade_b)
                    "C" -> stringResource(R.string.detail_grade_c)
                    else -> stringResource(R.string.detail_grade_none)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CostRow(labelRes: Int, value: String) {
    if (value.isBlank()) return
    Text(
        text = stringResource(labelRes, value),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 可折叠分区:默认状态由 initiallyExpanded 决定 */
@Composable
private fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.space3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.action_collapse else R.string.action_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.space2),
                    modifier = Modifier.padding(top = Spacing.space2),
                ) { content() }
            }
        }
    }
}

@Composable
private fun RowScope.ToolbarAction(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    val label = stringResource(labelRes)
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label)
    }
}

/** 来源文本:其中的 URL 渲染为可点击链接 */
@Composable
private fun SourceText(src: String) {
    val uriHandler = LocalUriHandler.current
    val urls = remember(src) { URL_REGEX.findAll(src).map { it.value }.toList() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        Text(src, style = MaterialTheme.typography.bodySmall)
        urls.forEach { url ->
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    runCatching { uriHandler.openUri(url) }
                },
            )
        }
    }
}

/** 分享/复制用的纯文本,带上出处 */
private fun EntryDto.shareText(): String = buildString {
    append(title)
    if (human.isNotBlank()) appendLine().appendLine(human)
    if (gain.isNotBlank()) appendLine().appendLine(gain)
    if (src.isNotBlank()) append(src)
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("entry", text))
}

private fun shareEntry(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
