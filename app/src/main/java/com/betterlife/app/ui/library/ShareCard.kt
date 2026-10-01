// 分享卡片:把条目渲染成一张竖版 PNG 分享出去。
//
// 离屏渲染:ComposeView 挂到 Activity content 视图里(移出屏幕外),等首帧布局完成后
// 画进 Bitmap,再移出视图树。直接 measure/layout 一个未 attach 的 ComposeView 拿不到
// 组合结果 —— 组合要走到 attach 之后的首帧才发生。
package com.betterlife.app.ui.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import com.betterlife.app.R
import com.betterlife.app.data.EntryDto
import com.betterlife.app.ui.common.CostMeter
import com.betterlife.app.ui.common.lensGroupTitle
import com.betterlife.app.ui.theme.BetterLifeTheme
import com.betterlife.app.ui.theme.LocalLensColors
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.lensIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 卡片宽度:主流分享目标的预览都够清晰,PNG 体积也可控 */
private const val CARD_WIDTH_PX = 1080

/** 分享目录里超过一天的旧图顺手清掉,别让 cache 越攒越多 */
private const val STALE_FILE_AGE_MS = 24L * 60 * 60 * 1000

/**
 * 生成分享卡片 PNG 并通过 FileProvider 分享;任何一步失败都抛异常,由调用方回退到纯文本分享。
 * 必须在主线程调用(离屏渲染依赖视图树与首帧回调)。
 */
suspend fun shareEntryImage(context: Context, host: View, entry: EntryDto) {
    shareCardImage(context, host, "entry-${entry.id}", entry.shareText()) { ShareCardContent(entry) }
}

/**
 * 通用分享卡管线(N6 起条目分享卡与里程碑日签共用):离屏渲染 [content] → 写 PNG →
 * FileProvider 分享。任何一步失败都抛异常,由调用方回退到纯文本分享([fallbackText] 是给
 * 不认图片的目标 App 的兜底文案)。必须在主线程调用。
 */
suspend fun shareCardImage(
    context: Context,
    host: View,
    fileName: String,
    fallbackText: String,
    content: @Composable () -> Unit,
) {
    val bitmap = renderCardBitmap(host, content)
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        cleanupStaleShareFiles(dir)
        File(dir, "$fileName.png").also { f ->
            FileOutputStream(f).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        // 目标 App 不认图片时还有纯文本兜底
        putExtra(Intent.EXTRA_TEXT, fallbackText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** 离屏渲染:挂到 content 视图(平移出屏)等首帧,再画进 Bitmap */
private suspend fun renderCardBitmap(host: View, content: @Composable () -> Unit): Bitmap =
    suspendCancellableCoroutine { cont ->
        val root = host.rootView.findViewById<ViewGroup>(android.R.id.content)
        if (root == null) {
            cont.resumeWithException(IllegalStateException("no content root"))
            return@suspendCancellableCoroutine
        }
        val view = ComposeView(host.context)
        // 关闭动效:入场动画的中间态会被截图拍成空白(同预览的处理)
        view.setContent { BetterLifeTheme(motionLevel = MotionLevel.OFF) { content() } }
        root.addView(view, ViewGroup.LayoutParams(CARD_WIDTH_PX, ViewGroup.LayoutParams.WRAP_CONTENT))
        // 移出屏幕,渲染那一帧用户看不到;手动 draw 不经过父视图,不受平移影响
        view.translationX = -CARD_WIDTH_PX.toFloat()
        view.doOnPreDraw {
            root.removeView(view)
            val w = view.width.coerceAtLeast(1)
            val h = view.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            cont.resume(bitmap)
        }
        cont.invokeOnCancellation { root.removeView(view) }
    }

private fun cleanupStaleShareFiles(dir: File) {
    val now = System.currentTimeMillis()
    runCatching {
        dir.listFiles().orEmpty()
            .filter { now - it.lastModified() > STALE_FILE_AGE_MS }
            .forEach { it.delete() }
    }
}

/** 分享卡片本体:应用名 + 口径徽标,标题、说人话、收益、CostMeter、出处 */
@Composable
internal fun ShareCardContent(entry: EntryDto, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(Spacing.space6),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                val icon = lensIcon(entry.lens)
                val tint = LocalLensColors.current.forLens(entry.lens)
                    ?: MaterialTheme.colorScheme.onSurfaceVariant
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(Spacing.space4),
                    )
                    Spacer(Modifier.size(Spacing.space1))
                }
                Text(
                    text = lensGroupTitle(entry.lens),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                )
            }

            Text(entry.title, style = MaterialTheme.typography.headlineSmall)

            if (entry.human.isNotBlank()) {
                Text(entry.human, style = MaterialTheme.typography.bodyLarge)
            }

            if (entry.gain.isNotBlank()) {
                Column {
                    Text(
                        text = stringResource(R.string.detail_section_gain),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(Spacing.space1))
                    Text(entry.gain, style = MaterialTheme.typography.bodyMedium)
                }
            }

            CostMeter(entry)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (entry.src.isNotBlank()) {
                Text(
                    text = stringResource(R.string.share_card_source_label) + " · " + entry.src,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = entry.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
