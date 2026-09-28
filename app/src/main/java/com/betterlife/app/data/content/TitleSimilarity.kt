package com.betterlife.app.data.content

/**
 * 中文标题相似度：bigram Jaccard（|A∩B|/|A∪B|）。
 * 「增删几个字」场景下比编辑距离稳：改动只破坏局部 bigram，整体重合度仍然高。
 * ContentSyncRepository 的下架重挂用它给有用户数据的旧 key 找后继条目。
 */
internal object TitleSimilarity {

    /** pickCandidate 要求与次优候选的分差下限：并列/接近都宁可不挂，留人工别名 */
    const val MIN_MARGIN = 0.05

    fun similarity(a: String, b: String): Double {
        val ta = bigrams(a)
        val tb = bigrams(b)
        if (ta.isEmpty() && tb.isEmpty()) return 1.0
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size
        return inter.toDouble() / (ta.size + tb.size - inter)
    }

    /**
     * 在候选 [(key, 标题)] 里给 title 找唯一后继：
     * 唯一 ≥ [threshold] 且与次优分差 ≥ [MIN_MARGIN] 才返回 key，否则 null。
     */
    fun pickCandidate(
        title: String,
        candidates: List<Pair<String, String>>,
        threshold: Double,
    ): String? {
        if (candidates.isEmpty()) return null
        val scored = candidates
            .map { (key, t) -> key to similarity(title, t) }
            .sortedByDescending { it.second }
        val best = scored.first()
        if (best.second < threshold) return null
        val second = scored.getOrNull(1) ?: return best.first
        if (second.second >= threshold) return null
        if (best.second - second.second < MIN_MARGIN) return null
        return best.first
    }

    /** 单字串退化为字符集合（没有 bigram 可切）；空白折叠与 build_content.py 的 _normalize 对齐 */
    private fun bigrams(s: String): Set<String> {
        val t = s.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return emptySet()
        if (t.length < 2) return setOf(t)
        return t.windowed(2).toSet()
    }
}
