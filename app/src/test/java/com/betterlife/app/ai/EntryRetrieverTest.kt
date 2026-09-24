package com.betterlife.app.ai

import com.betterlife.app.data.EntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryRetrieverTest {

    private val retriever = EntryRetriever()

    private val quitSmoking = EntryDto(
        id = "02-01", sec = 2, n = 1, title = "戒烟",
        human = "戒烟能显著降低全因死亡率",
        hay = "戒烟\n戒烟能显著降低全因死亡率\n烟草 肺癌 心血管",
    )
    private val carInsurance = EntryDto(
        id = "05-03", sec = 5, n = 3, title = "买对车险",
        human = "三者险保额要买够",
        hay = "买对车险\n三者险保额要买够\n交通事故 赔偿",
    )
    private val entries = listOf(quitSmoking, carInsurance)

    @Test
    fun `查询戒烟命中戒烟条目并排在无关条目前`() {
        val results = retriever.search("戒烟", entries)
        assertTrue(results.isNotEmpty())
        assertEquals("02-01", results.first().entry.id)
        // 无关条目得分为 0，应被过滤
        assertTrue(results.none { it.entry.id == "05-03" })
    }

    @Test
    fun `标题直接命中有加权`() {
        // 另一条 hay 也含「戒烟」但标题不含，应排在标题命中者之后
        val mention = EntryDto(
            id = "09-09", sec = 9, n = 9, title = "全因死亡",
            human = "这里提到戒烟一次",
            hay = "全因死亡\n这里提到戒烟一次",
        )
        val results = retriever.search("戒烟", entries + mention)
        assertEquals("02-01", results.first().entry.id)
    }

    @Test
    fun `档案加分条目前移`() {
        // 两条 hay 同样命中「血压」，标题都不含查询词：基础分相同，无 boost 时按 id 排
        val a = EntryDto(id = "01-01", sec = 1, n = 1, title = "睡眠充足", hay = "睡眠充足\n睡眠不足会升高血压")
        val b = EntryDto(id = "01-02", sec = 1, n = 2, title = "定期体检", hay = "定期体检\n睡眠不足会升高血压")
        val withoutBoost = retriever.search("血压", listOf(a, b)).map { it.entry.id }
        val withBoost = retriever.search("血压", listOf(a, b), boostIds = setOf("01-02")).map { it.entry.id }
        assertEquals("01-01", withoutBoost.first())
        assertEquals("01-02", withBoost.first())
    }

    @Test
    fun `空查询返回空结果`() {
        assertTrue(retriever.search("   ", entries).isEmpty())
    }
}
