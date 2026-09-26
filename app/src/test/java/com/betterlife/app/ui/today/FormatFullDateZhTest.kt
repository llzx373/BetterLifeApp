package com.betterlife.app.ui.today

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** 问候日期固定中文格式，不随系统 locale 变化 */
class FormatFullDateZhTest {

    @Test
    fun `日期格式固定中文不随系统locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val text = formatFullDateZh(LocalDate.of(2026, 3, 8))
            assertTrue(text, text.contains("星期") || text.contains("年"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
