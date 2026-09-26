// AI 问答页:安全提示固定在顶部 + 气泡对话 + 可点的示例问题
//
// 安全提示不是模型回答,所以它不做成列表里的第一个气泡,而是固定的 Surface;
// 无 Key 横幅走 secondaryContainer,避免和口径色里的「别踩线」撞色。
package com.betterlife.app.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.ai.ChatMessage
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val listState = rememberLazyListState()

    val noApiKey = settingsState.loaded && settingsState.settings.apiKey.isBlank()

    // 条目详情页跳进来时自动解读一次;重复触发(旋转重建)由 VM 里的已解读集合挡住
    LaunchedEffect(entryId) {
        if (entryId != null) vm.explainEntry(entryId)
    }

    // 新消息到达时滚到底部
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
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

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.space4),
                verticalArrangement = Arrangement.spacedBy(Spacing.space3),
            ) {
                if (state.messages.isEmpty() && noApiKey) {
                    item(key = "suggestions") {
                        Suggestions(onPick = { vm.ask(it) })
                    }
                }
                items(state.messages.size, key = { it }) { i ->
                    val msg = state.messages[i]
                    Bubble(
                        text = msg.content.ifBlank { stringResource(R.string.chat_error_generic) },
                        isUser = msg.role == ChatMessage.ROLE_USER,
                        isError = msg.isError,
                    )
                }
                if (state.asking) {
                    item(key = "asking") { ThinkingRow(entryCount = state.entryCount) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                stringResource(R.string.chat_no_key_banner),
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
private fun Bubble(text: String, isUser: Boolean, isError: Boolean) {
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

    // 入场三拍。关闭/减弱档停在终态不动。
    // 已知取舍:LazyColumn 的 item 被回收后再滚回来会重播一次;只播「新消息」需要
    // 把消息 id 与已播集合提到 VM,不等这笔复杂度。
    val level = LocalMotionLevel.current
    // 协程里不是 Composable 上下文,spec 必须先取出来
    val effects = motionEffectsSpec<Float>()
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(level) {
        if (level != MotionLevel.STANDARD) {
            scale.snapTo(1f)
            alpha.snapTo(1f)
            return@LaunchedEffect
        }
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

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = container,
            contentColor = content,
            shape = shape,
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = BubbleMaxWidth)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.space3, vertical = Spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isError) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(Spacing.space4))
                    Spacer(Modifier.width(Spacing.space2))
                }
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
