package com.betterlife.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TitleSimilarity（bigram Jaccard）与 pickCandidate 的保守性单测。
 * 断言里的期望值是用 Python 同款算法离线算出的真实值，不是拍脑袋阈值。
 */
class TitleSimilarityTest {

    @Test
    fun `加两个字的长标题相似度最高`() {
        // 真实案例:02-12「…别自行停」→「…千万别自行停」
        val s = TitleSimilarity.similarity(
            "有高血压、高血脂就按医嘱规律吃药，别自行停",
            "有高血压、高血脂就按医嘱规律吃药，千万别自行停",
        )
        assertEquals(0.8182, s, 0.001)
    }

    @Test
    fun `加字与减字对称且都在高位`() {
        assertEquals(0.6667, TitleSimilarity.similarity("戒烟是延寿第一招", "戒烟是延寿的第一招"), 0.001)
        assertEquals(0.6667, TitleSimilarity.similarity("每天快走半小时", "快走半小时"), 0.001)
    }

    @Test
    fun `换序仍保留大部分 bigram`() {
        assertEquals(0.7143, TitleSimilarity.similarity("早睡早起身体好", "身体好早睡早起"), 0.001)
    }

    @Test
    fun `完全不同为 0`() {
        assertEquals(0.0, TitleSimilarity.similarity("戒烟", "定期体检"), 0.0)
    }

    @Test
    fun `单字串退化为字符集合——与多字标题无 bigram 交集时为 0`() {
        // 「系」退化为字符集 {系},与「系安全带」的 bigram {系安,安全,全带} 无交集
        assertEquals(0.0, TitleSimilarity.similarity("系安全带", "系"), 0.0)
        assertEquals(0.3333, TitleSimilarity.similarity("戒烟", "马上戒烟"), 0.001)
    }

    @Test
    fun `空串边界`() {
        assertEquals(1.0, TitleSimilarity.similarity("", ""), 0.0)
        assertEquals(0.0, TitleSimilarity.similarity("", "戒烟"), 0.0)
    }

    // ---------- pickCandidate ----------

    private val oldTitle = "有高血压、高血脂就按医嘱规律吃药，别自行停"
    private val renamed = "有高血压、高血脂就按医嘱规律吃药，千万别自行停" // sim 0.8182

    @Test
    fun `唯一过阈值候选被选中`() {
        val picked = TitleSimilarity.pickCandidate(
            oldTitle,
            listOf("newKey" to renamed, "other" to "定期体检发现异常怎么办"),
            threshold = 0.8,
        )
        assertEquals("newKey", picked)
    }

    @Test
    fun `无候选或全部低于阈值时返回 null`() {
        assertNull(TitleSimilarity.pickCandidate(oldTitle, emptyList(), 0.8))
        assertNull(TitleSimilarity.pickCandidate(oldTitle, listOf("x" to "定期体检"), 0.85))
    }

    @Test
    fun `多个候选并列过阈值时返回 null——不敢猜`() {
        val picked = TitleSimilarity.pickCandidate(
            oldTitle,
            listOf("a" to renamed, "b" to renamed),
            threshold = 0.8,
        )
        assertNull(picked)
    }

    @Test
    fun `次优低于阈值但分差不足阈值间隔时返回 null`() {
        // 相对「早睡早起身体好」:best=身体好早睡早起 0.7143,次优=早起身体好 0.6667,
        // 阈值 0.7 只过一个,但分差 0.0476 < 0.05 → 不敢猜
        val picked = TitleSimilarity.pickCandidate(
            "早睡早起身体好",
            listOf("a" to "身体好早睡早起", "b" to "早起身体好"),
            threshold = 0.7,
        )
        assertNull(picked)
    }

    @Test
    fun `次优与最优分差足够大时正常返回`() {
        // 阈值 0.7:best=改名 0.8182,次优=无关标题 0.0,分差足够 → 返回
        val picked = TitleSimilarity.pickCandidate(
            oldTitle,
            listOf("a" to renamed, "b" to "快走半小时"),
            threshold = 0.7,
        )
        assertEquals("a", picked)
    }

    @Test
    fun `单候选过阈值直接返回`() {
        assertEquals("only", TitleSimilarity.pickCandidate(oldTitle, listOf("only" to renamed), 0.8))
    }

    @Test
    fun `运行时阈值比构建期更保守——轻微改名的案例在运行时不会自动挂`() {
        // 刻意锁定这个语义:构建期(0.8)能接住的轻微改名,运行时兜底接不住,
        // 只处理几乎没改的情况;其余留给 key_aliases.json 人工登记
        assertTrue(TitleSimilarity.similarity(oldTitle, renamed) < 0.85)
    }
}
