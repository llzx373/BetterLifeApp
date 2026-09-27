package com.betterlife.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 行解析与增量内容提取：data 行、[DONE] 结束标记、注释/keep-alive、
 * 损坏 JSON、只带 role 的首块、多字节内容。
 */
class SseParserTest {

    @Test
    fun `data行解析出载荷并能提取增量文本`() {
        val line = """data: {"choices":[{"delta":{"content":"你好"}}]}"""

        val event = parseSseLine(line)

        assertTrue(event is SseEvent.Data)
        assertEquals("你好", extractDeltaContent((event as SseEvent.Data).payload))
    }

    @Test
    fun `data后不带空格也能解析`() {
        val event = parseSseLine("""data:{"choices":[{"delta":{"content":"a"}}]}""")

        assertTrue(event is SseEvent.Data)
        assertEquals("a", extractDeltaContent((event as SseEvent.Data).payload))
    }

    @Test
    fun `DONE标记解析为流结束`() {
        assertEquals(SseEvent.Done, parseSseLine("data: [DONE]"))
        assertEquals(SseEvent.Done, parseSseLine("data:[DONE]"))
    }

    @Test
    fun `注释与keep-alive行被忽略`() {
        assertNull(parseSseLine(": keep-alive"))
        assertNull(parseSseLine(":"))
    }

    @Test
    fun `空行与非data字段被忽略`() {
        assertNull(parseSseLine(""))
        assertNull(parseSseLine("event: message"))
        assertNull(parseSseLine("id: 1"))
        assertNull(parseSseLine("retry: 3000"))
    }

    @Test
    fun `空data载荷被忽略`() {
        assertNull(parseSseLine("data:"))
        assertNull(parseSseLine("data: "))
    }

    @Test
    fun `损坏的JSON返回null`() {
        val event = parseSseLine("data: {not json")

        assertTrue(event is SseEvent.Data)
        assertNull(extractDeltaContent((event as SseEvent.Data).payload))
    }

    @Test
    fun `只带role的首块没有增量文本`() {
        val payload = """{"choices":[{"delta":{"role":"assistant"}}]}"""

        assertNull(extractDeltaContent(payload))
    }

    @Test
    fun `空content与缺choices都返回null`() {
        assertNull(extractDeltaContent("""{"choices":[{"delta":{"content":""}}]}"""))
        assertNull(extractDeltaContent("""{"choices":[]}"""))
        assertNull(extractDeltaContent("""{}"""))
    }

    @Test
    fun `多字节与emoji内容原样返回`() {
        val text = "多喝水🌱，每天 8 杯"
        val payload = """{"choices":[{"delta":{"content":"多喝水🌱，每天 8 杯"}}]}"""

        assertEquals(text, extractDeltaContent(payload))
    }

    @Test
    fun `未知字段被忽略`() {
        val payload =
            """{"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"ok"},"finish_reason":null}]}"""

        assertEquals("ok", extractDeltaContent(payload))
    }
}
