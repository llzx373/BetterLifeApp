// 长文列表页:全部深度文章的目录,条目库目录页「长文」入口直达。
// 每行复用 ArticleEntryCard,副标题显示所属节名( secs 里的节都能对上号);
// 排序走纯函数 sortArticlesForList(首节号升序)。空态兜底:入口在空时已不显示,
// 这里只防「老库未补播」的极端直达。
package com.betterlife.app.ui.library

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.R
import com.betterlife.app.data.ArticleDto
import com.betterlife.app.data.SectionDto
import com.betterlife.app.data.sortArticlesForList
import com.betterlife.app.ui.common.MotionEntrance
import com.betterlife.app.ui.common.predictiveBackTransition
import com.betterlife.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ArticleListState {
    data object Loading : ArticleListState
    data class Ready(
        val articles: List<ArticleDto>,
        val sectionByN: Map<Int, SectionDto>,
    ) : ArticleListState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as BetterLifeApp).container.entryRepository
    val state by produceState<ArticleListState>(ArticleListState.Loading) {
        value = withContext(Dispatchers.IO) {
            val data = repo.entriesData()
            ArticleListState.Ready(
                articles = sortArticlesForList(data.articles),
                sectionByN = data.sections.associateBy { it.n },
            )
        }
    }

    Scaffold(
        modifier = Modifier.predictiveBackTransition(onBack),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.article_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            ArticleListState.Loading -> Text(
                text = stringResource(R.string.article_loading),
                modifier = Modifier.padding(padding).padding(Spacing.space4),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ArticleListState.Ready -> {
                if (s.articles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.article_list_empty),
                        modifier = Modifier.padding(padding).padding(Spacing.space4),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Scaffold
                }
                // 加载态到列表一层淡入,与长文阅读页/条目详情页同一做法
                MotionEntrance(
                    visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                    modifier = Modifier.padding(padding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.space4,
                            end = Spacing.space4,
                            top = Spacing.space4,
                            bottom = Spacing.space12,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
                    ) {
                        items(s.articles, key = { it.key }) { article ->
                            ArticleEntryCard(
                                title = article.title,
                                subtitle = articleSectionLabel(article, s.sectionByN),
                                onClick = { onOpenArticle(article.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 所属节名,如「第 1 节 · 不要早死」;挂多节的都列出,对不上节号的跳过 */
@Composable
private fun articleSectionLabel(article: ArticleDto, sectionByN: Map<Int, SectionDto>): String =
    article.secs.mapNotNull { n ->
        sectionByN[n]?.let { stringResource(R.string.article_section_label, it.n, it.title) }
    }.joinToString("  ")
