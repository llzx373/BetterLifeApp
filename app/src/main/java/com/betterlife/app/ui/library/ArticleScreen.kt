// 长文阅读页:上游 docs/ 深度文章的 App 内阅读器。
//
// 渲染走 data/content/MarkdownLite.kt 的 markdown-lite 解析(标题/段落/列表/表格/粗体/
// 链接),不引第三方 markdown 库 —— 上游 index.html 也是手写 lite 渲染。
// 正文里的「第 X 节第 Y 条」渲染为可点交叉引用,命中在架条目(EntriesData.entryBySecN)
// 跳条目详情,不命中按纯文本;https 链接走系统浏览器;指向其他长文的 docs 链接跳对应长文。
package com.betterlife.app.ui.library

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.R
import com.betterlife.app.data.ArticleDto
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.content.MdBlock
import com.betterlife.app.data.content.MdLink
import com.betterlife.app.data.content.parseInline
import com.betterlife.app.data.content.parseMarkdownLite
import com.betterlife.app.ui.common.MotionEntrance
import com.betterlife.app.ui.common.predictiveBackTransition
import com.betterlife.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 表格单元格统一列宽:等宽不破版,超宽整体横向滑动 */
private val TABLE_CELL_WIDTH = 168.dp

/**
 * 长文入口卡片(详情页「延伸阅读」与章节页「本节长文」共用):
 * 标题 + 右侧箭头,整卡可点;长辈模式靠 typography/间距放大自然生效。
 */
@Composable
internal fun ArticleEntryCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 可选副标题(如所属节名),小字弱化显示在标题下 */
    subtitle: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(Spacing.space3),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface ArticleState {
    data object Loading : ArticleState
    data object Missing : ArticleState
    data class Found(
        val article: ArticleDto,
        val entryBySecN: Map<String, EntryDto>,
        val articleByFile: Map<String, ArticleDto>,
    ) : ArticleState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    articleKey: String,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as BetterLifeApp).container.entryRepository
    val state by produceState<ArticleState>(ArticleState.Loading, articleKey) {
        value = withContext(Dispatchers.IO) {
            val data = repo.entriesData()
            val article = data.articleByKey[articleKey]
            if (article == null) {
                ArticleState.Missing
            } else {
                ArticleState.Found(article, data.entryBySecN, data.articleByFile)
            }
        }
    }

    Scaffold(
        modifier = Modifier.predictiveBackTransition(onBack),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (state as? ArticleState.Found)?.article?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
        when (val s = state) {
            ArticleState.Loading -> Text(
                text = stringResource(R.string.article_loading),
                modifier = Modifier.padding(padding).padding(Spacing.space4),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ArticleState.Missing -> Text(
                text = stringResource(R.string.article_missing),
                modifier = Modifier.padding(padding).padding(Spacing.space4),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ArticleState.Found -> ArticleBody(
                state = s,
                onOpenEntry = onOpenEntry,
                onOpenArticle = onOpenArticle,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ArticleBody(
    state: ArticleState.Found,
    onOpenEntry: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(state.article.body) { parseMarkdownLite(state.article.body) }
    // 加载态到正文给一层淡入,与条目详情页同一做法
    MotionEntrance(
        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.space4,
                end = Spacing.space4,
                top = Spacing.space4,
                bottom = Spacing.space12,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            items(blocks) { block ->
                when (block) {
                    is MdBlock.Heading -> Text(
                        text = block.text,
                        style = if (block.level <= 2) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        modifier = Modifier.padding(top = Spacing.space2),
                    )
                    is MdBlock.Paragraph -> InlineText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        state = state,
                        onOpenEntry = onOpenEntry,
                        onOpenArticle = onOpenArticle,
                    )
                    is MdBlock.BulletList -> ListBlock(
                        items = block.items,
                        ordered = false,
                        state = state,
                        onOpenEntry = onOpenEntry,
                        onOpenArticle = onOpenArticle,
                    )
                    is MdBlock.OrderedList -> ListBlock(
                        items = block.items,
                        ordered = true,
                        state = state,
                        onOpenEntry = onOpenEntry,
                        onOpenArticle = onOpenArticle,
                    )
                    is MdBlock.Table -> TableBlock(
                        rows = block.rows,
                        state = state,
                        onOpenEntry = onOpenEntry,
                        onOpenArticle = onOpenArticle,
                    )
                }
            }
        }
    }
}

/** 行内富文本:粗体 / https 链接(系统浏览器)/ 条目交叉引用 / 长文互链 */
@Composable
private fun InlineText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    state: ArticleState.Found,
    onOpenEntry: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, state.entryBySecN, state.articleByFile, linkColor) {
        buildInlineAnnotated(text, state, linkColor, onOpenEntry, onOpenArticle)
    }
    Text(annotated, style = style, modifier = modifier)
}

private fun buildInlineAnnotated(
    text: String,
    state: ArticleState.Found,
    linkColor: androidx.compose.ui.graphics.Color,
    onOpenEntry: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
    for (span in parseInline(text)) {
        when (val link = span.link) {
            null -> if (span.bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            } else {
                append(span.text)
            }
            is MdLink.Url -> withLink(LinkAnnotation.Url(link.url, styles = linkStyles)) {
                append(span.text)
            }
            is MdLink.EntryRef -> {
                // 只链到在架条目;查不到(下架/号对不上)按纯文本渲染
                val entry = state.entryBySecN["${link.sec}-${link.n}"]
                if (entry == null) {
                    append(span.text)
                } else {
                    val listener = LinkInteractionListener { onOpenEntry(entry.id) }
                    withLink(LinkAnnotation.Clickable(entry.id, styles = linkStyles, linkInteractionListener = listener)) {
                        append(span.text)
                    }
                }
            }
            is MdLink.ArticleFile -> {
                val target = state.articleByFile[link.file]
                if (target == null) {
                    append(span.text)
                } else {
                    val listener = LinkInteractionListener { onOpenArticle(target.key) }
                    withLink(LinkAnnotation.Clickable(target.key, styles = linkStyles, linkInteractionListener = listener)) {
                        append(span.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListBlock(
    items: List<String>,
    ordered: Boolean,
    state: ArticleState.Found,
    onOpenEntry: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = if (ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.space2))
                InlineText(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    state = state,
                    onOpenEntry = onOpenEntry,
                    onOpenArticle = onOpenArticle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 表格:等宽列 + 横向滑动,首行表头加粗;单元格同样走行内解析(里面可能有交叉引用) */
@Composable
private fun TableBlock(
    rows: List<List<String>>,
    state: ArticleState.Found,
    onOpenEntry: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(1.dp, borderColor, MaterialTheme.shapes.small),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row {
                row.forEach { cell ->
                    InlineText(
                        text = cell,
                        style = MaterialTheme.typography.bodySmall,
                        state = state,
                        onOpenEntry = onOpenEntry,
                        onOpenArticle = onOpenArticle,
                        modifier = Modifier
                            .width(TABLE_CELL_WIDTH)
                            .padding(Spacing.space2),
                    )
                }
            }
            if (rowIndex < rows.lastIndex) {
                HorizontalDivider(color = borderColor)
            }
        }
    }
}
