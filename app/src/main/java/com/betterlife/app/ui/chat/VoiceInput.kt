// N8 语音问 AI:聊天页输入栏的麦克风,走系统 SpeechRecognizer(中文 zh-CN,
// partial results 实时上屏),不接三方 SDK。
//
// 交互定为「点击开始/再点结束」而不是长按说话:不需要处理抬手取消/超时,
// 是点击切换里最不容易误触的形态。识别结果只填入输入框,由用户确认后再发;
// 错误(无匹配/网络错等)安静收尾——已经上屏的 partial 文本留在输入框里,不弹窗。
//
// 降级:设备没有识别服务、或用户拒绝了麦克风权限,都安静隐藏麦克风按钮,
// 不给报错弹窗。
package com.betterlife.app.ui.chat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** 麦克风按钮可见性:识别服务不可用或权限被拒后都不再展示,安静降级 */
internal fun shouldShowVoiceButton(recognitionAvailable: Boolean, permissionDenied: Boolean): Boolean =
    recognitionAvailable && !permissionDenied

/** 识别文本接在点击麦克风之前已有的输入之后;已有输入尾部的空白不留 */
internal fun mergeVoiceText(base: String, recognized: String): String =
    base.trimEnd() + recognized

/** 设备是否带语音识别服务;isRecognitionAvailable 是 API 31 才有的,低版本查 RecognitionService */
internal fun isVoiceRecognitionAvailable(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SpeechRecognizer.isRecognitionAvailable(context)
    } else {
        context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .isNotEmpty()
    }

/**
 * SpeechRecognizer 的壳:创建/销毁与回调都在这里,ChatScreen 只面对
 * listening 状态与 onText 回调(partial 与最终结果是同一条当前假设,都走它)。
 * 必须在主线程使用(SpeechRecognizer 的约定),Composable 天然满足。
 */
internal class VoiceInputController(
    private val context: Context,
    private val onText: (String) -> Unit,
) {
    var listening by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null

    fun toggle() {
        if (listening) stop() else start()
    }

    fun start() {
        if (listening || !isVoiceRecognitionAvailable(context)) return
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        listening = true
        suppressResults = false
        r.startListening(recognizeIntent())
    }

    /** 用户主动结束:stopListening 后识别服务仍会回调 onResults 把已说内容送回来 */
    fun stop() {
        listening = false
        recognizer?.stopListening()
    }

    /** 发送等场景的静音收尾:停掉识别,迟到的 partial/最终结果不再回灌输入框 */
    fun stopQuietly() {
        listening = false
        suppressResults = true
        recognizer?.stopListening()
    }

    /** 页面离开时调用:停掉识别并释放,之后这个实例不再可用 */
    fun release() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    private var suppressResults = false

    private fun deliver(bundle: Bundle?) {
        if (suppressResults) return
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(onText)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        // 错误(无匹配/网络错/服务不可用)一律安静收尾:partial 文本已经上屏,留在输入框
        override fun onError(error: Int) {
            listening = false
        }

        override fun onResults(results: Bundle?) {
            listening = false
            deliver(results)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            deliver(partialResults)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun recognizeIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
}
