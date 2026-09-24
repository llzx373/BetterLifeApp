// 条目详情:完整展示成本/说人话/收益/证据等级/来源/备注六栏,长文可折叠,来源 URL 可点开
package com.betterlife.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryRepository
import com.betterlife.app.ui.common.CostChips
import com.betterlife.app.ui.common.DisputeBadge
import com.betterlife.app.ui.common.GradeBadge
import com.betterlife.app.ui.common.RatioBadge
import com.betterlife.app.ui.common.TodoBadge
import com.betterlife.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val URL_REGEX = Regex("""https?://[^\s)<>"']+""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val context = LocalContext.current
    val repo = remember { EntryRepository(context.applicationContext) }
    val entry by produceState<EntryDto?>(initialValue = null, entryId) {
        value = withContext(Dispatchers.IO) { repo.entriesData().byId[entryId] }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.id ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val e = entry
        if (e == null) {
            Text(
                "条目不存在或还在加载",
                modifier = Modifier.padding(padding).padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RatioBadge(e.ratio)
                    GradeBadge(e.grade)
                    if (e.dispute) DisputeBadge()
                    if (e.todo) TodoBadge()
                }
                Spacer(Modifier.height(8.dp))
                Text(e.title, style = MaterialTheme.typography.headlineSmall)
            }

            if (e.dispute || e.todo) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = buildString {
                                if (e.dispute) append("这条建议存在争议,请结合自己的情况判断。")
                                if (e.todo) append("这条内容还未核实,谨慎参考。")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            item {
                DetailSection(title = "成本", initiallyExpanded = true) {
                    CostChips(e)
                    if (e.level.isNotBlank()) {
                        Text(
                            "对人生的影响程度:${e.level}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (e.human.isNotBlank()) {
                item {
                    DetailSection(title = "说人话", initiallyExpanded = true) {
                        Text(e.human, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (e.gain.isNotBlank()) {
                item {
                    DetailSection(title = "收益", initiallyExpanded = true) {
                        Text(e.gain, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                DetailSection(title = "证据等级", initiallyExpanded = true) {
                    Text(
                        when (e.grade) {
                            "A" -> "A 级:证据比较扎实(高质量研究支持)"
                            "B" -> "B 级:有一定证据,但不够充分"
                            "C" -> "C 级:证据较弱,多为经验性建议"
                            else -> "未标注证据等级"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (e.src.isNotBlank()) {
                item {
                    DetailSection(title = "来源", initiallyExpanded = false) {
                        SourceText(e.src)
                    }
                }
            }
            if (e.note.isNotBlank()) {
                item {
                    DetailSection(title = "备注", initiallyExpanded = false) {
                        Text(e.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        vm.addToTodo(e.id)
                        scope.launch { snackbar.showSnackbar("已加入待办") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("加入待办") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 可折叠分区:默认状态由 initiallyExpanded 决定 */
@Composable
private fun DetailSection(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) { content() }
            }
        }
    }
}

/** 来源文本:其中的 URL 渲染为可点击链接 */
@Composable
private fun SourceText(src: String) {
    val uriHandler = LocalUriHandler.current
    val urls = remember(src) { URL_REGEX.findAll(src).map { it.value }.toList() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
