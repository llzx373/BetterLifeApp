package com.betterlife.app.data.content

import com.betterlife.app.data.EntriesFile
import com.betterlife.app.data.EntriesData
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.RulesFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长文 markdown-lite 解析与 docs 链接提取的单测。
 * 覆盖上游长文实际用到的语法(标题/段落/列表/表格/粗体/链接/「第 X 节第 Y 条」交叉引用),
 * 以及备注/节首引言里的 docs 链接提取与剥离。
 */
class MarkdownLiteTest {

    // ---------- 块级解析 ----------

    @Test
    fun `标题按级别解析`() {
        val blocks = parseMarkdownLite("## 一、先判断\n\n正文一段\n\n### 小节\n\n又一段")
        assertEquals(MdBlock.Heading(2, "一、先判断"), blocks[0])
        assertEquals(MdBlock.Paragraph("正文一段"), blocks[1])
        assertEquals(MdBlock.Heading(3, "小节"), blocks[2])
        assertEquals(MdBlock.Paragraph("又一段"), blocks[3])
    }

    @Test
    fun `连续行合并为一个段落,空行分段`() {
        val blocks = parseMarkdownLite("第一行\n第二行\n\n第二段")
        assertEquals(listOf(MdBlock.Paragraph("第一行\n第二行"), MdBlock.Paragraph("第二段")), blocks)
    }

    @Test
    fun `无序列表连续行归并`() {
        val blocks = parseMarkdownLite("- 甲\n- 乙\n\n尾巴")
        assertEquals(MdBlock.BulletList(listOf("甲", "乙")), blocks[0])
        assertEquals(MdBlock.Paragraph("尾巴"), blocks[1])
    }

    @Test
    fun `有序列表剥掉序号前缀`() {
        val blocks = parseMarkdownLite("1. 第一条\n2. 第二条\n10. 第十条")
        assertEquals(listOf(MdBlock.OrderedList(listOf("第一条", "第二条", "第十条"))), blocks)
    }

    @Test
    fun `表格剔除分隔行,首行是表头`() {
        val md = "| 列一 | 列二 |\n|---|---|\n| a | b |\n| c | d |"
        val table = parseMarkdownLite(md).single() as MdBlock.Table
        assertEquals(listOf(listOf("列一", "列二"), listOf("a", "b"), listOf("c", "d")), table.rows)
    }

    // ---------- 行内解析 ----------

    @Test
    fun `粗体切成独立 span`() {
        val spans = parseInline("买的时候认 **CCC 标志** 就行")
        assertEquals(3, spans.size)
        assertEquals(MdSpan("买的时候认 "), spans[0])
        assertEquals(MdSpan("CCC 标志", bold = true), spans[1])
        assertEquals(MdSpan(" 就行"), spans[2])
    }

    @Test
    fun `尖括号 URL 与裸 URL 都可点`() {
        val angle = parseInline("来源 <https://example.com/a> 完毕")
        assertEquals(MdLink.Url("https://example.com/a"), angle[1].link)

        val bare = parseInline("见 https://example.com/b 即可")
        assertEquals(MdLink.Url("https://example.com/b"), bare[1].link)
    }

    @Test
    fun `https markdown 链接保留 label`() {
        val spans = parseInline("见 [说明页面](https://example.com/c)")
        assertEquals(MdSpan("说明页面", link = MdLink.Url("https://example.com/c")), spans[1])
    }

    @Test
    fun `指向长文的 docs 链接解析为 ArticleFile,其他相对路径只留 label`() {
        val article = parseInline("另见 [被裁了之后先做什么](../docs/被裁了之后先做什么.md) 一篇")
        assertEquals(MdLink.ArticleFile("被裁了之后先做什么.md"), article[1].link)

        val relative = parseInline("见 [核实记录](核实记录/追加.md)")
        assertEquals(MdSpan("核实记录"), relative[1])
    }

    @Test
    fun `交叉引用含空格与无空格变体`() {
        val spaced = parseInline("见第 16 节第 9 条(确诊就吃药)")
        assertEquals(MdLink.EntryRef(16, 9), (spaced[1].link as MdLink.EntryRef))

        val compact = parseInline("见第16节第9条")
        assertEquals(MdLink.EntryRef(16, 9), compact[1].link)
    }

    @Test
    fun `区间写法「第 26 节第 5 到第 10 条」链接到起点条目`() {
        val spans = parseInline("第 26 节第 5 到第 10 条写的那些义务")
        assertEquals(MdLink.EntryRef(26, 5), spans[0].link)
        assertTrue(spans[1].text.contains("到第 10 条"))
    }

    @Test
    fun `同一段里 https 链接与交叉引用都可点`() {
        val spans = parseInline("见第 1 节第 7 条,来源 <https://example.com/d>")
        val links = spans.mapNotNull { it.link }
        assertEquals(2, links.size)
        assertTrue(links.any { it is MdLink.EntryRef })
        assertTrue(links.any { it is MdLink.Url })
    }

    // ---------- docs 链接提取与剥离(备注/节首引言) ----------

    @Test
    fun `备注里两种 docs 链接形态都能提取`() {
        val a = findDocsLinks("清单见 [docs/家庭应急装备清单.md](../docs/家庭应急装备清单.md)")
        assertEquals(1, a.size)
        assertEquals("家庭应急装备清单.md", a[0].file)

        val b = findDocsLinks("长文见 [docs/结婚划不划算.md](docs/结婚划不划算.md)。")
        assertEquals("结婚划不划算.md", b.single().file)
    }

    @Test
    fun `剥离已解析链接后剩余文本干净,剥完为空则整段省略`() {
        val text = "先算一笔账。完整清单见 [docs/结婚划不划算.md](../docs/结婚划不划算.md)"
        val links = findDocsLinks(text)
        assertEquals("先算一笔账。完整清单见", stripRanges(text, links.map { it.range }))

        val onlyLink = "[docs/结婚划不划算.md](../docs/结婚划不划算.md)"
        val onlyLinks = findDocsLinks(onlyLink)
        assertEquals("", stripRanges(onlyLink, onlyLinks.map { it.range }))
    }

    @Test
    fun `普通文本没有 docs 链接时提取为空`() {
        assertTrue(findDocsLinks("没有任何链接的备注").isEmpty())
        assertTrue(findDocsLinks("见 [外站](https://example.com)").isEmpty())
    }

    // ---------- entryBySecN 交叉引用索引 ----------

    private fun entry(sec: Int, n: Int, removed: Boolean = false) =
        EntryDto(id = "k-$sec-$n", key = "k-$sec-$n", sec = sec, n = n, title = "t$sec-$n", removed = removed)

    @Test
    fun `entryBySecN 键是 sec-n 且不含下架条目`() {
        val data = EntriesData(
            EntriesFile(entries = listOf(entry(1, 26), entry(2, 39), entry(16, 9, removed = true))),
            RulesFile(),
        )
        assertEquals("k-1-26", data.entryBySecN["1-26"]?.key)
        assertEquals("k-2-39", data.entryBySecN["2-39"]?.key)
        assertNull("下架条目不收进跳转索引", data.entryBySecN["16-9"])
    }
}
