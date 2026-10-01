package com.betterlife.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N8 语音输入的纯逻辑:麦克风按钮的可见性降级,与识别文本和已有输入的拼接。
 * 语音识别本体依赖系统服务,不进单测(真机冒烟见 todo.md A2)。
 */
class VoiceInputTest {

    @Test
    fun micHiddenWhenRecognitionUnavailable() {
        assertFalse(shouldShowVoiceButton(recognitionAvailable = false, permissionDenied = false))
    }

    @Test
    fun micHiddenWhenPermissionDenied() {
        assertFalse(shouldShowVoiceButton(recognitionAvailable = true, permissionDenied = true))
    }

    @Test
    fun micShownOnlyWhenAvailableAndNotDenied() {
        assertTrue(shouldShowVoiceButton(recognitionAvailable = true, permissionDenied = false))
    }

    @Test
    fun voiceTextAppendsAfterExistingInput() {
        assertEquals("已有的字今天天气怎么样", mergeVoiceText("已有的字", "今天天气怎么样"))
    }

    @Test
    fun voiceTextDropsTrailingWhitespaceOfBase() {
        assertEquals("已有的字今天天气怎么样", mergeVoiceText("已有的字 ", "今天天气怎么样"))
    }

    @Test
    fun voiceTextOnEmptyBaseIsJustRecognized() {
        assertEquals("今天天气怎么样", mergeVoiceText("", "今天天气怎么样"))
    }
}
