package com.betterlife.app.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 设计系统规约：UI 包内不得出现中文字面量，一律走 res/values/strings.xml。
 *
 * 文档（DESIGN_SYSTEM §8 P1）要求「用自定义 lint 规则卡住」。这里用 JVM 单测实现同等约束：
 * 不必新增 lint-api 模块、也不与 AGP 版本耦合，直接跑在现有的 testDebugUnitTest 里，
 * 违规即测试失败、打断构建。
 *
 * 只扫 `com/betterlife/app/ui/` —— ai / recommend / data 里的中文是提示词、领域文案与
 * 机器可读 key，不属于 UI 文案。
 */
class UiNoChineseLiteralTest {

    private val chinese = Regex("\\p{IsHan}")
    private val stringLiteral = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")

    @Test
    fun uiPackageHasNoChineseStringLiteral() {
        val root = locateUiSourceRoot()
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        // 防止目录定位错误导致测试空转通过
        assertTrue("未扫描到任何 ui 源码文件: ${root.absolutePath}", files.size >= 5)

        val offenders = mutableListOf<String>()
        files.forEach { file ->
            // 注释里的中文不算字面量，先去掉行注释
            val code = file.readLines().joinToString("\n") { stripLineComment(it) }
            stringLiteral.findAll(code).forEach { match ->
                if (chinese.containsMatchIn(match.value)) {
                    offenders += "${file.name}: ${match.value.trim()}"
                }
            }
        }

        assertTrue(
            "UI 包内出现中文字面量，请改到 res/values/strings.xml：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private fun stripLineComment(line: String): String {
        val idx = line.indexOf("//")
        return if (idx >= 0) line.substring(0, idx) else line
    }

    private fun locateUiSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/betterlife/app/ui"),
            File("app/src/main/java/com/betterlife/app/ui"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("找不到 ui 源码目录，当前工作目录 ${File(".").absolutePath}")
    }
}
