/**
 * 长文 markdown-lite 解析:只覆盖上游长文(upstream/HowToLiveBetter/docs 下的 .md)实际
 * 用到的语法 —— `##`/`###` 标题、段落、`- ` 无序列表、`1. ` 有序列表、`| ... |` 表格、
 * `**粗体**`、`[label](url)` 链接、`<https://...>` 与裸 https 链接、「第 X 节第 Y 条」
 * 条目交叉引用。不支持的语法原样按纯文本渲染,不丢内容。
 *
 * 纯 Kotlin 零 Android 依赖,单测直接覆盖(见 MarkdownLiteTest)。
 * 放在 data 包而不是 ui 包:交叉引用正则含中文字面量,ui 包被 UiNoChineseLiteralTest 卡。
 */
package com.betterlife.app.data.content

/** 块级元素 */
sealed interface MdBlock {
    /** level 取 2/3(正文不含 H1);level 1 兜底按 2 渲染 */
    data class Heading(val level: Int, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock

    data class BulletList(val items: List<String>) : MdBlock

    /** items 已剥掉序号前缀,序号由渲染层按位置生成 */
    data class OrderedList(val items: List<String>) : MdBlock

    /** rows[0] 是表头;分隔行 |---|---| 已剔除 */
    data class Table(val rows: List<List<String>>) : MdBlock
}

/** 行内链接目标 */
sealed interface MdLink {
    data class Url(val url: String) : MdLink

    /** 「第 X 节第 Y 条」→ 条目交叉引用(sec/n 即 entryBySecN 的 "sec-n" 键) */
    data class EntryRef(val sec: Int, val n: Int) : MdLink

    /** 指向另一篇长文的 docs 链接,file 如 "被裁了之后先做什么.md" */
    data class ArticleFile(val file: String) : MdLink
}

/** 一段行内文本:纯文本 / 粗体 / 链接(三者可叠加的只有 bold,链接内不再解析粗体) */
data class MdSpan(val text: String, val bold: Boolean = false, val link: MdLink? = null)

/** 「第 X 节第 Y 条」,含无空格变体;尾字「条」可省(兼容「第 26 节第 5 到第 10 条」这类区间写法) */
val ENTRY_REF_REGEX = Regex("""第\s*(\d+)\s*节\s*第\s*(\d+)\s*条?""")

/** markdown 链接里指向上游长文的 target:`(../)?docs/<file>.md` */
private val DOCS_TARGET_REGEX = Regex("""(?:\.\./)?docs/([^\s)]+\.md)""")

/**
 * 备注/节首引言里指向长文的 markdown 链接(label 任意,含 `[docs/x.md](../docs/x.md)`
 * 与 `[任意文字](docs/x.md)` 两种形态)。
 */
data class DocsLink(val label: String, val file: String, val range: IntRange)

private val DOCS_LINK_REGEX = Regex("""\[([^\]]*)\]\(\s*(?:\.\./)?docs/([^\s)]+\.md)\s*\)""")

fun findDocsLinks(text: String): List<DocsLink> =
    DOCS_LINK_REGEX.findAll(text).map {
        DocsLink(label = it.groupValues[1], file = it.groupValues[2], range = it.range)
    }.toList()

/** 把已确认的链接原文从文本里剥掉(入口卡片已表达),顺带清掉行尾与收尾空白 */
fun stripRanges(text: String, ranges: List<IntRange>): String {
    if (ranges.isEmpty()) return text
    val sorted = ranges.sortedBy { it.first }
    val out = StringBuilder(text.length)
    var cursor = 0
    for (r in sorted) {
        if (r.first >= cursor) {
            out.append(text, cursor, r.first)
            cursor = r.last + 1
        }
    }
    out.append(text, cursor, text.length)
    return out.lines().joinToString("\n") { it.trimEnd() }.trim()
}

private val ORDERED_ITEM_REGEX = Regex("""^\d+[.、]\s+""")
private val TABLE_SEPARATOR_CELL = Regex(""":?-{2,}:?""")

/** 行内 token:markdown 链接 / 尖括号 URL / 裸 URL / 条目交叉引用 / 粗体,按出现顺序切分 */
private val INLINE_TOKEN_REGEX = Regex(
    """\[([^\]]+)\]\(([^)\s]+)\)""" +
        """|<(https?://[^>]+)>""" +
        """|(https?://[^\s<>"'）)。；,，;]+)""" +
        """|第\s*(\d+)\s*节\s*第\s*(\d+)\s*条?""" +
        """|\*\*([^*]+)\*\*""",
)

fun parseInline(text: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    var last = 0
    for (m in INLINE_TOKEN_REGEX.findAll(text)) {
        if (m.range.first > last) spans += MdSpan(text.substring(last, m.range.first))
        val groups = m.groups
        when {
            groups[1] != null -> {
                val label = groups[1]!!.value
                val target = groups[2]!!.value
                val docsFile = DOCS_TARGET_REGEX.matchEntire(target)?.groupValues?.get(1)
                spans += when {
                    docsFile != null -> MdSpan(label, link = MdLink.ArticleFile(docsFile))
                    target.startsWith("http") -> MdSpan(label, link = MdLink.Url(target))
                    // 相对路径链接(如 核实记录/…)App 里无处可跳,只留可读 label
                    else -> MdSpan(label)
                }
            }
            groups[3] != null -> spans += MdSpan(groups[3]!!.value, link = MdLink.Url(groups[3]!!.value))
            groups[4] != null -> spans += MdSpan(groups[4]!!.value, link = MdLink.Url(groups[4]!!.value))
            groups[5] != null -> spans += MdSpan(
                m.value,
                link = MdLink.EntryRef(sec = groups[5]!!.value.toInt(), n = groups[6]!!.value.toInt()),
            )
            groups[7] != null -> spans += MdSpan(groups[7]!!.value, bold = true)
        }
        last = m.range.last + 1
    }
    if (last < text.length) spans += MdSpan(text.substring(last))
    return spans.filter { it.text.isNotEmpty() }
}

fun parseMarkdownLite(body: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    val lines = body.lines()
    var i = 0
    while (i < lines.size) {
        val t = lines[i].trim()
        when {
            t.isEmpty() -> {
                flushParagraph()
                i++
            }
            t.startsWith("### ") -> {
                flushParagraph()
                blocks += MdBlock.Heading(3, t.removePrefix("###").trim())
                i++
            }
            t.startsWith("## ") || t.startsWith("# ") -> {
                flushParagraph()
                blocks += MdBlock.Heading(2, t.trimStart('#').trim())
                i++
            }
            t.startsWith("- ") -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("- ")) {
                    items += lines[i].trim().removePrefix("- ").trim()
                    i++
                }
                blocks += MdBlock.BulletList(items)
            }
            ORDERED_ITEM_REGEX.find(t) != null -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size && ORDERED_ITEM_REGEX.find(lines[i].trim()) != null) {
                    items += lines[i].trim().replaceFirst(ORDERED_ITEM_REGEX, "")
                    i++
                }
                blocks += MdBlock.OrderedList(items)
            }
            t.startsWith("|") -> {
                flushParagraph()
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                    if (!cells.all { TABLE_SEPARATOR_CELL.matches(it) }) rows += cells
                    i++
                }
                if (rows.isNotEmpty()) blocks += MdBlock.Table(rows)
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(t)
                i++
            }
        }
    }
    flushParagraph()
    return blocks
}
