package com.betterlife.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 博查 web-search 响应解析：data.webPages.value[] → WebSearchItem */
class WebSearchParseTest {

    @Test
    fun `正常响应解析出条目`() {
        val json = """
        {
          "code": 200,
          "data": {
            "webPages": {
              "value": [
                {"name": "标题一", "url": "https://a.com/1", "snippet": "摘要一", "datePublished": "2026-03-01"},
                {"name": "标题二", "url": "https://a.com/2", "snippet": "摘要二"}
              ]
            }
          }
        }
        """.trimIndent()
        val items = parseWebSearchResponse(json)
        assertEquals(2, items.size)
        assertEquals("标题一", items[0].title)
        assertEquals("https://a.com/1", items[0].url)
        assertEquals("摘要一", items[0].snippet)
        assertEquals("2026-03-01", items[0].datePublished)
        assertEquals(null, items[1].datePublished)
    }

    @Test
    fun `没有 webPages 字段时返回空列表`() {
        assertTrue(parseWebSearchResponse("""{"code":200,"data":{}}""").isEmpty())
    }

    @Test
    fun `坏 JSON 返回空列表而不是抛异常`() {
        assertTrue(parseWebSearchResponse("not json at all").isEmpty())
    }

    @Test
    fun `url 为空的条目被过滤`() {
        val json = """
        {"data":{"webPages":{"value":[{"name":"x","url":"","snippet":"s"}]}}}
        """.trimIndent()
        assertTrue(parseWebSearchResponse(json).isEmpty())
    }
}
