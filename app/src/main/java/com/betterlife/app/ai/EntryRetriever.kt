package com.betterlife.app.ai

import com.betterlife.app.data.EntryDto

data class RetrievedEntry(
    val entry: EntryDto,
    val score: Double,
)

/**
 * 轻量检索器，纯 Kotlin、可单测。
 * 评分 = 查询与 hay 的字符 bigram 重叠率 + 标题命中加权 + 规则命中条目的少量加分。
 * 对中文这种无空格语言，bigram 比重在短查询上比词匹配稳。
 */
class EntryRetriever {

    companion object {
        private const val TITLE_RECALL_WEIGHT = 0.6
        private const val TITLE_HIT_BONUS = 1.0
        private const val PROFILE_BOOST_BONUS = 0.15
    }

    fun search(
        query: String,
        entries: List<EntryDto>,
        boostIds: Set<String> = emptySet(),
        topK: Int = 6,
    ): List<RetrievedEntry> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val qBigrams = bigrams(q)
        if (qBigrams.isEmpty()) return emptyList()

        return entries.asSequence()
            .map { entry ->
                val hayRecall = recall(qBigrams, bigrams(entry.hay))
                val title = normalize(entry.title)
                val titleRecall = recall(qBigrams, bigrams(title))
                val titleHit = q.length >= 2 && title.contains(q)
                var score = hayRecall + TITLE_RECALL_WEIGHT * titleRecall
                if (titleHit) score += TITLE_HIT_BONUS
                if (entry.id in boostIds) score += PROFILE_BOOST_BONUS
                RetrievedEntry(entry, score)
            }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<RetrievedEntry> { it.score }.thenBy { it.entry.id })
            .take(topK)
            .toList()
    }

    private fun normalize(s: String): String =
        s.lowercase().filter { !it.isWhitespace() }

    private fun bigrams(s: String): Set<String> =
        if (s.length < 2) setOf(s) else s.windowed(2).toSet()

    private fun recall(query: Set<String>, doc: Set<String>): Double =
        query.count { it in doc }.toDouble() / query.size
}
