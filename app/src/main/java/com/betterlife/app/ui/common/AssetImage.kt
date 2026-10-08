// 打包在 assets 里的内容配图(章节横幅、条目插图)。
//
// 路径约定:章节图 images/sections/{secKey}.webp,条目图 images/entries/{entryKey}.webp。
// 条目详情页用 rememberEntryBanner:专属图优先,无条件显示;回退到章节图时先过
// images/manifest.json 的关键词相关性门控 —— 正文与图片主题不沾边就宁可不配图。
// 文件不存在就当作没配 —— 渲染方拿到 null 直接不画,不加占位符(占位框在图片解码快的场景反而是一帧闪烁)。
//
// rememberAssetBanner / rememberEntryBanner 必须放在稳定的组合作用域(页面级),不要放进 LazyColumn 的 item 里:
// item 内容是子组合,列表状态更新时会被销毁重建,里头的 produceState 会带着
// ExitedCompositionCancellationException 一起被取消,图永远出不来。
//
// 署名清单在 assets/images/credits.json,相关性关键词在 assets/images/manifest.json,
// 均由 tools/fetch_section_images.py 生成;门控规则与 tools/check_image_relevance.py 保持一致。
package com.betterlife.app.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.betterlife.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 章节横幅图路径 */
fun sectionImagePath(secKey: String): String? =
    secKey.takeIf { it.isNotBlank() }?.let { "images/sections/$it.webp" }

/** 一张可用的配图:解码后的位图 + 署名 */
class AssetBanner internal constructor(
    val bitmap: ImageBitmap,
    val author: String,
)

private const val CACHE_BYTES = 24 * 1024 * 1024

/** 解码结果缓存:同一张章节图会在章页与每个条目页反复出现 */
private val bitmapCache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** 记录解码失败的路径,避免每次进页面都重新 open 一遍 */
private val missingPaths = mutableSetOf<String>()

private suspend fun decodeAsset(context: Context, path: String): ImageBitmap? {
    bitmapCache.get(path)?.let { return it.asImageBitmap() }
    if (path in missingPaths) return null
    val decoded = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    if (decoded != null) bitmapCache.put(path, decoded) else missingPaths.add(path)
    return decoded?.asImageBitmap()
}

@Serializable
private class ImageCredit(
    val file: String = "",
    val author: String = "",
    val source: String = "",
    val license: String = "",
)

private var creditsCache: Map<String, ImageCredit>? = null

private suspend fun creditFor(context: Context, path: String): ImageCredit? {
    val credits = creditsCache ?: withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open("images/credits.json").use {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<Map<String, ImageCredit>>(it.readBytes().decodeToString())
            }
        }.getOrDefault(emptyMap<String, ImageCredit>())
            .values.associateBy { it.file }  // credits.json 以条目 key 为主键,file 字段才是路径
    }.also { creditsCache = it }
    return credits[path]?.takeIf { it.author.isNotBlank() }
}

@Serializable
internal class ImageManifest(
    val minBodyHits: Int = 2,
    val sections: Map<String, ManifestSection> = emptyMap(),
)

@Serializable
internal class ManifestSection(
    val keywords: List<String> = emptyList(),
)

private var manifestCache: ImageManifest? = null

private suspend fun imageManifest(context: Context): ImageManifest =
    manifestCache ?: withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open("images/manifest.json").use {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<ImageManifest>(it.readBytes().decodeToString())
            }
        }.getOrDefault(ImageManifest())
    }.also { manifestCache = it }

/**
 * 章节图回退的相关性门控:与 tools/check_image_relevance.py 同一套规则,改任一边要同步。
 * 标题命中任一关键词即相关;否则正文命中 minBodyHits 个不同关键词才算相关。
 * 章节没配关键词时不做门控(向后兼容)。关键词在 manifest 里已存成小写。
 */
internal fun isSectionImageRelevant(
    manifest: ImageManifest,
    secKey: String,
    title: String,
    body: String,
): Boolean {
    val keywords = manifest.sections[secKey]?.keywords.orEmpty()
    if (keywords.isEmpty()) return true
    if (keywords.any { it in title.lowercase() }) return true
    val b = body.lowercase()
    return keywords.count { it in b } >= manifest.minBodyHits
}

/**
 * 条目配图:专属图 images/entries/{entryKey}.webp 优先,无条件显示;
 * 没有专属图才回退章节图,且先过 manifest.json 的关键词相关性门控 ——
 * 正文与图片主题不沾边时宁可不配图。body 传条目的正文检索文本(human + hay)。
 * 放在页面级作用域调用,见文件头注释。
 */
@Composable
fun rememberEntryBanner(entryKey: String, secKey: String, title: String, body: String): AssetBanner? {
    val context = LocalContext.current
    val banner by produceState<AssetBanner?>(initialValue = null, entryKey, secKey, title, body) {
        var result: AssetBanner? = null
        if (entryKey.isNotBlank()) {
            val path = "images/entries/$entryKey.webp"
            decodeAsset(context, path)?.let { bmp ->
                result = AssetBanner(bmp, creditFor(context, path)?.author.orEmpty())
            }
        }
        if (result == null) {
            val path = sectionImagePath(secKey)
            if (path != null && isSectionImageRelevant(imageManifest(context), secKey, title, body)) {
                decodeAsset(context, path)?.let { bmp ->
                    result = AssetBanner(bmp, creditFor(context, path)?.author.orEmpty())
                }
            }
        }
        value = result
    }
    return banner
}

/**
 * 依次尝试候选路径,返回第一张解码成功的配图;都失败(或列表为空)为 null。
 * 放在页面级作用域调用,见文件头注释。
 */
@Composable
fun rememberAssetBanner(paths: List<String>): AssetBanner? {
    val context = LocalContext.current
    val banner by produceState<AssetBanner?>(initialValue = null, paths) {
        var result: AssetBanner? = null
        for (path in paths) {
            val bmp = decodeAsset(context, path) ?: continue
            result = AssetBanner(bmp, creditFor(context, path)?.author.orEmpty())
            break
        }
        value = result
    }
    return banner
}

/** 内容配图横幅:圆角裁切由调用方的 Modifier.clip 决定;有署名记录时右下角压作者角标 */
@Composable
fun AssetImageBanner(
    banner: AssetBanner?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (banner == null) return
    Box(modifier) {
        Image(
            bitmap = banner.bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (banner.author.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.space2),
            ) {
                Text(
                    text = banner.author,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = Spacing.space2, vertical = Spacing.space1),
                )
            }
        }
    }
}
