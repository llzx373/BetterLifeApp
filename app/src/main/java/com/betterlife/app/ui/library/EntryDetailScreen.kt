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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.R
import com.betterlife.app.data.EntryDto
import com.betterlife.app.ui.common.AssetImageBanner
import com.betterlife.app.ui.common.CostMeter
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.DoneBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.MotionEntrance
import com.betterlife.app.ui.common.PlannedBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.common.TodoBadge
import com.betterlife.app.ui.common.predictiveBackTransition
import com.betterlife.app.ui.common.rememberEntryBanner
import com.betterlife.app.ui.theme.LocalMotionLevel
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.motionEffectsSpec
import com.betterlife.app.ui.theme.motionSpatialSpec
import com.betterlife.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.CancellationException
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
    onExplain: (String) -> Unit,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    EntryDetailContent(
        entryId = entryId,
        onExplain = onExplain,
        onDismissed = onBack,
        vm = vm,
        modifier = Modifier.predictiveBackTransition(onBack),
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
    )
}

/**
 * 条目详情本体:分区 LazyColumn + 底部悬浮工具栏。
 *
 * 整屏路由(EntryDetailScreen)与三栏右栏(≥840dp)共用。整屏由调用方传 topBar 和
 * predictiveBackTransition 修饰符;嵌进 scaffold pane 时不要传 predictiveBackTransition ——
 * pane 切换动画由 ListDetailPaneScaffold 自己管。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailContent(
    entryId: String,
    onExplain: (String) -> Unit,
    /** 用户在工具栏点了「不再推荐」且屏蔽的正是当前展示的条目 */
    onDismissed: () -> Unit,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as BetterLifeApp).container.entryRepository
    val state by produceState<DetailState>(DetailState.Loading, entryId) {
        val found = withContext(Dispatchers.IO) { repo.entriesData().byId[entryId] }
        value = if (found == null) DetailState.Missing else DetailState.Found(found)
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val libraryState by vm.uiState.collectAsStateWithLifecycle()
    val userNote by remember(entryId) { vm.noteFlow(entryId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val addedTodoMessage = stringResource(R.string.detail_added_todo)
    val copiedMessage = stringResource(R.string.detail_copied)
    var editingNote by rememberSaveable { mutableStateOf(false) }
    /** 分享卡片在渲染/写盘期间挡掉重复点击 */
    var sharing by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { topBar?.invoke() },
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

        // 配图加载放在页面级作用域:LazyColumn item 是子组合,被销毁会连带取消解码。
        // 回退到章节图时带关键词门控:正文与图片主题不沾边的条目不显示图
        val banner = rememberEntryBanner(e.key, e.secKey, e.title, e.human + "\n" + e.hay)

        Box(Modifier.fillMaxSize().padding(padding)) {
            // 加载态到正文给一层淡入:加载快时整页硬切最刺眼。工具条的入场独立,不跟着走
            MotionEntrance(
                visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
            ) {
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
                    // 下架条目:内容快照照常展示,顶部先给一条来源状态说明
                    // (secondaryContainer:参照 OfflineBanner 的配色约定,「可读但有保留」的提示态)
                    if (e.removed) {
                        item {
                            NoticeRow(
                                textRes = R.string.entry_removed_banner,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    // 条目配图:专属图优先;回退章节图已过相关性门控,不相关时 banner 为 null 不画
                    if (banner != null) {
                        item {
                            AssetImageBanner(
                                banner = banner,
                                contentDescription = e.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(MaterialTheme.shapes.medium),
                            )
                        }
                    }

                    item {
                        HeroSection(
                            entry = e,
                            isDone = e.id in libraryState.doneIds,
                            isPlanned = e.id in libraryState.plannedIds,
                        )
                    }

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

                    // 用户笔记放在内容备注之后:书的备注是「内容」,笔记是「我的想法」,两者分开陈述
                    item {
                        NoteSection(
                            note = userNote?.text.orEmpty(),
                            onEdit = { editingNote = true },
                        )
                    }
                }
            }

            // 工具条从底部滑入(§6.2)。用 MutableTransitionState 才能在首帧就播,
            // 直接 visible = true 的 AnimatedVisibility 不会播入场
            MotionEntrance(
                visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                modifier = Modifier.align(Alignment.BottomCenter),
                slideFromBottom = true,
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
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
                        icon = if (e.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        labelRes = if (e.id in favorites) R.string.detail_unfavorite else R.string.detail_favorite,
                        onClick = { vm.toggleFavorite(e.id) },
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
                        onClick = {
                            if (sharing) return@ToolbarAction
                            sharing = true
                            scope.launch {
                                try {
                                    shareEntryImage(context, view, e)
                                } catch (t: Throwable) {
                                    if (t is CancellationException) throw t
                                    // 卡片渲染/写盘/授权任何一步失败,回退到纯文本分享
                                    shareEntry(context, e.shareText())
                                } finally {
                                    sharing = false
                                }
                            }
                        },
                    )
                    // 「不再推荐」写 DISMISSED,推荐引擎会排除它;屏蔽后页面没有留着的意义,交给调用方收尾
                    // (整屏:返回上一页;三栏:清掉右栏选中)
                    ToolbarAction(
                        icon = Icons.Filled.NotInterested,
                        labelRes = R.string.detail_dismiss,
                        onClick = {
                            vm.dismissEntry(e.id)
                            onDismissed()
                        },
                    )
                    ToolbarAction(
                        icon = Icons.Filled.AutoAwesome,
                        labelRes = R.string.detail_explain,
                        onClick = { onExplain(e.id) },
                    )
                }
            }
        }

        if (editingNote) {
            NoteEditDialog(
                initial = userNote?.text.orEmpty(),
                onDismiss = { editingNote = false },
                onSave = {
                    vm.saveNote(e.id, it)
                    editingNote = false
                },
                onDelete = {
                    vm.deleteNote(e.id)
                    editingNote = false
                },
            )
        }
    }
}

/** 首屏决策信息:徽标、标题、性价比图形;状态徽标(已完成/已加入)跟在内容徽标之后 */
@Composable
private fun HeroSection(
    entry: EntryDto,
    isDone: Boolean,
    isPlanned: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
            RatioBadge(entry.ratio)
            GradeBadge(entry.grade)
            if (entry.dispute) DisputeBadge()
            if (entry.todo) TodoBadge()
            if (isDone) DoneBadge()
            if (isPlanned) PlannedBadge()
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

/** 用户笔记:已有笔记全文展示(弱化色),空态只有入口;编辑走对话框 */
@Composable
private fun NoteSection(note: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.detail_note_section),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.space1))
        if (note.isNotBlank()) {
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(Spacing.space4),
            )
            Spacer(Modifier.width(Spacing.space1))
            Text(
                stringResource(
                    if (note.isBlank()) R.string.detail_note_write else R.string.detail_note_edit,
                ),
            )
        }
    }
}

/** 笔记编辑对话框:保存空文本等价于删除(与 LibraryViewModel.saveNote 的约定一致) */
@Composable
private fun NoteEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_note_section)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.detail_note_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
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
            MotionEntrance(visible = expanded, expand = true) {
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
        // 关闭档不套 AnimatedContent:静态帧否则可能抓到图标切换的中间态
        if (LocalMotionLevel.current == MotionLevel.OFF) {
            Icon(icon, contentDescription = label)
            return@IconButton
        }
        // transitionSpec 不是 Composable 上下文,spec 必须先取出来
        val spatial = motionSpatialSpec<Float>()
        val effects = motionEffectsSpec<Float>()
        // 收藏切换是详情页最高频的反馈动作,图标淡入+微缩放,不硬切(§6.2)
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(animationSpec = effects) +
                    scaleIn(initialScale = 0.9f, animationSpec = spatial)) togetherWith
                    fadeOut(animationSpec = effects)
            },
            label = "toolbarIcon",
        ) { targetIcon ->
            Icon(targetIcon, contentDescription = label)
        }
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
internal fun EntryDto.shareText(): String = buildString {
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
