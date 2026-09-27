// AI 问答页:安全提示固定在顶部 + 气泡对话 + 可点的示例问题
//
// 安全提示不是模型回答,所以它不做成列表里的第一个气泡,而是固定的 Surface;
// 无 Key 横幅走 secondaryContainer,避免和口径色里的「别踩线」撞色。
package com.betterlife.app.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.ai.AiSource
import com.betterlife.app.ai.ChatMessage
import com.betterlife.app.data.resolveActiveProvider
import com.betterlife.app.ui.common.predictiveBackTransition
import com.betterlife.app.ui.theme.LocalMotionLevel
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.motionEffectsSpec
import com.betterlife.app.viewmodel.ChatViewModel
import com.betterlife.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private val BubbleMaxWidth = 300.dp
private val ThinkingIndicatorWidth = 40.dp
private val SuggestionRes = listOf(
    R.string.chat_suggestion_1,
    R.string.chat_suggestion_2,
    R.string.chat_suggestion_3,
)

/** 知识库来源最多直接列出的条数,超出的折叠成「等 N 条来源」 */
private const val KB_SOURCES_SHOWN = 3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    entryId: String? = null,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory),
    settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var showProviderDialog by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val noApiKey = settingsState.loaded && resolveActiveProvider(
        settingsState.settings.aiProviders,
        settingsState.settings.activeProviderId,
    )?.apiKey.isNullOrBlank()

    // 条目详情页跳进来时自动解读一次;重复触发(旋转重建)由 VM 里的已解读集合挡住
    LaunchedEffect(entryId) {
        if (entryId != null) vm.explainEntry(entryId)
    }

    // 新消息到达时滚到底部。animateScrollToItem 不看全局档位,所以这里手动分流:
    // 标准档滚动动画,减弱/关闭档瞬移;首次进带历史的会话一律瞬移,不做长距离滚动
    val motionLevel = LocalMotionLevel.current
    var didInitialScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val last = state.messages.size - 1
        if (!didInitialScroll) {
            listState.scrollToItem(last)
            didInitialScroll = true
        } else if (motionLevel == MotionLevel.STANDARD) {
            listState.animateScrollToItem(last)
        } else {
            listState.scrollToItem(last)
        }
    }

    Scaffold(
        modifier = Modifier.predictiveBackTransition(onBack),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_chat)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            SafetyNotice()

            if (noApiKey) {
                NoKeyBanner(onOpenSettings = onOpenSettings)
            }
            if (state.webSearch && !state.searchConfigured) {
                Banner(
                    textRes = R.string.chat_web_not_configured,
                    onOpenSettings = onOpenSettings,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().imeNestedScroll(),
                contentPadding = PaddingValues(Spacing.space4),
                verticalArrangement = Arrangement.spacedBy(Spacing.space3),
            ) {
                if (state.messages.isEmpty() && noApiKey) {
                    item(key = "suggestions") {
                        Suggestions(onPick = { vm.ask(it) })
                    }
                }
                items(state.messages.size, key = { it }) { i ->
                    Bubble(msg = state.messages[i])
                }
                if (state.asking) {
                    item(key = "asking") { ThinkingRow(entryCount = state.entryCount) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 来源切换：知识库 / 互联网
                IconButton(onClick = vm::toggleWebSearch) {
                    Icon(
                        imageVector = if (state.webSearch) Icons.Filled.Public else Icons.Filled.MenuBook,
                        contentDescription = stringResource(
                            if (state.webSearch) R.string.chat_source_web else R.string.chat_source_kb,
                        ),
                        tint = if (state.webSearch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 并行供应商选择
                IconButton(onClick = { showProviderDialog = true }) {
                    Icon(
                        Icons.Filled.Groups,
                        contentDescription = stringResource(R.string.chat_providers_desc),
                        tint = if (state.selectedProviderIds.size > 1) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        val q = input.trim()
                        if (q.isNotEmpty()) {
                            vm.ask(q)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank() && !state.asking,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
                }
            }
        }
    }

    if (showProviderDialog) {
        ProviderDialog(
            providers = state.enabledProviders.map { it.id to it.name },
            initialSelected = state.selectedProviderIds,
            onConfirm = { ids ->
                showProviderDialog = false
                vm.setChatProviders(ids)
            },
            onDismiss = { showProviderDialog = false },
        )
    }
}

/** 并行回答的供应商多选:全不选 = 只用「当前使用」的那张 */
@Composable
private fun ProviderDialog(
    providers: List<Pair<String, String>>,
    initialSelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember { mutableStateOf(initialSelected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_providers_dialog_title)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(checked) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.chat_providers_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                providers.forEach { (id, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checked = if (id in checked) checked - id else checked + id
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = id in checked, onCheckedChange = null)
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}

/** 安全底线是常量,不是模型回答:固定在顶部,不随对话滚走 */
@Composable
private fun SafetyNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(Spacing.space4))
            Spacer(Modifier.width(Spacing.space2))
            Text(
                text = stringResource(R.string.chat_safety_notice),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NoKeyBanner(onOpenSettings: () -> Unit) {
    Banner(textRes = R.string.chat_no_key_banner, onOpenSettings = onOpenSettings)
}

@Composable
private fun Banner(textRes: Int, onOpenSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space1),
        ) {
            Text(
                stringResource(textRes),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.chat_go_settings)) }
        }
    }
}

/** 空状态:与其留白,不如给三个能直接点的问题 */
@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        Text(
            text = stringResource(R.string.chat_suggestions_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SuggestionRes.forEach { res ->
            val question = stringResource(res)
            SuggestionChip(
                onClick = { onPick(question) },
                label = { Text(question) },
            )
        }
    }
}

/** 思考态:说清在检索什么,而不是干等一个「思考中」 */
@Composable
private fun ThinkingRow(entryCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        LinearWavyProgressIndicator(
            modifier = Modifier.width(ThinkingIndicatorWidth),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (entryCount > 0) stringResource(R.string.chat_thinking, entryCount)
            else stringResource(R.string.chat_thinking_plain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 气泡入场三拍:0.95 → 1.02 → 1.0,同时淡入。节奏按 §6.2 */
private const val BUBBLE_POP_FROM = 0.95f
private const val BUBBLE_POP_PEAK = 1.02f
private const val BUBBLE_POP_MILLIS = 250

/** 气泡:用户侧大圆角右对齐,助手侧小圆角左对齐,方向感不靠颜色也能看出来 */
@Composable
private fun Bubble(msg: ChatViewModel.ChatUiMessage) {
    val isUser = msg.role == ChatMessage.ROLE_USER
    val isError = msg.isError
    val container = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = if (isUser) MaterialTheme.shapes.large else MaterialTheme.shapes.extraSmall

    // 入场三拍。关闭档停在终态;减弱档只留 100ms 淡入,不做缩放。
    // 已知取舍:LazyColumn 的 item 被回收后再滚回来会重播一次;只播「新消息」需要
    // 把消息 id 与已播集合提到 VM,不等这笔复杂度。
    val level = LocalMotionLevel.current
    // 协程里不是 Composable 上下文,spec 必须先取出来
    val effects = motionEffectsSpec<Float>()
    // 初始值直接按档位给:先在终态绘制一帧、LaunchedEffect 里再 snapTo 回起点会闪跳
    val scale = remember { Animatable(if (level == MotionLevel.STANDARD) BUBBLE_POP_FROM else 1f) }
    val alpha = remember { Animatable(if (level == MotionLevel.OFF) 1f else 0f) }
    LaunchedEffect(level) {
        when (level) {
            MotionLevel.STANDARD -> {
                // snapTo 保留:运行中切档位要先归位再播
                scale.snapTo(BUBBLE_POP_FROM)
                alpha.snapTo(0f)
                launch { alpha.animateTo(1f, animationSpec = effects) }
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = BUBBLE_POP_MILLIS
                        BUBBLE_POP_PEAK at BUBBLE_POP_MILLIS / 2
                        1f at BUBBLE_POP_MILLIS
                    },
                )
            }
            MotionLevel.REDUCED -> {
                scale.snapTo(1f)
                alpha.snapTo(0f)
                alpha.animateTo(1f, animationSpec = effects)
            }
            MotionLevel.OFF -> {
                scale.snapTo(1f)
                alpha.snapTo(1f)
            }
        }
    }

    val text = msg.contentRes?.let { stringResource(it) }
        ?: msg.content.ifBlank { stringResource(R.string.chat_error_generic) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = BubbleMaxWidth),
        ) {
            Surface(
                color = container,
                contentColor = content,
                shape = shape,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.space3, vertical = Spacing.space2),
                ) {
                    // 多供应商并行时标注这张气泡是谁答的
                    msg.providerName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = content.copy(alpha = 0.7f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isError) {
                            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(Spacing.space4))
                            Spacer(Modifier.width(Spacing.space2))
                        }
                        Text(text = text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (!isUser && !isError && msg.sources.isNotEmpty()) {
                SourcesRow(sources = msg.sources)
            }
        }
    }
}

/** 答案来源:知识库列「第X节第Y条」(超出折叠),互联网列可点的链接标题 */
@Composable
private fun SourcesRow(sources: List<AiSource>) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.padding(top = Spacing.space1, start = Spacing.space2)) {
        Text(
            text = stringResource(R.string.chat_sources_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val web = sources.any { it.url != null }
        if (web) {
            sources.forEachIndexed { index, s ->
                Text(
                    text = "${index + 1}. ${s.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { s.url?.let(uriHandler::openUri) },
                )
            }
        } else {
            sources.take(KB_SOURCES_SHOWN).forEach { s ->
                Text(
                    text = s.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sources.size > KB_SOURCES_SHOWN) {
                Text(
                    text = stringResource(R.string.chat_sources_more, sources.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
