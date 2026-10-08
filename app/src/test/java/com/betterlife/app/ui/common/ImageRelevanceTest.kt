package com.betterlife.app.ui.common

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 章节图回退的相关性门控:
 * 一是 isSectionImageRelevant 本身的规则(与 tools/check_image_relevance.py 同步);
 * 二是真实 assets/images/manifest.json 与 entries.json 的一致性 ——
 * manifest 里的 secKey 必须是真实章节、关键词必须非空且全小写,否则门控静默失效。
 */
class ImageRelevanceTest {

    private fun manifest(vararg sections: Pair<String, List<String>>, minBodyHits: Int = 2) =
        ImageManifest(
            minBodyHits = minBodyHits,
            sections = sections.associate { (k, kw) -> k to ManifestSection(kw) },
        )

    @Test
    fun `标题命中任一关键词即相关`() {
        val m = manifest("s1" to listOf("跑步", "运动"))
        assertTrue(isSectionImageRelevant(m, "s1", "每周三次跑步", "毫无关联的正文"))
    }

    @Test
    fun `正文命中达到阈值的不同关键词才相关`() {
        val m = manifest("s1" to listOf("急救", "止血", "骨折"))
        // 两个不同关键词 → 相关
        assertTrue(isSectionImageRelevant(m, "s1", "标题不含关键词", "先止血,再固定骨折部位"))
        // 只有一个关键词,出现多次也不算 → 不相关
        assertFalse(isSectionImageRelevant(m, "s1", "标题不含关键词", "止血止血再止血"))
        // 一个都没有 → 不相关
        assertFalse(isSectionImageRelevant(m, "s1", "标题", "完全无关的正文"))
    }

    @Test
    fun `英文关键词大小写不敏感`() {
        val m = manifest("s1" to listOf("aed", "120"))
        assertTrue(isSectionImageRelevant(m, "s1", "让旁人找 AED 并打 120", ""))
    }

    @Test
    fun `章节未配关键词时不做门控`() {
        val m = manifest("s1" to emptyList())
        assertTrue(isSectionImageRelevant(m, "s1", "任意标题", "任意正文"))
        assertTrue(isSectionImageRelevant(m, "不存在的章节", "任意标题", "任意正文"))
    }

    @Test
    fun `阈值取 manifest 的 minBodyHits`() {
        val m = manifest("s1" to listOf("a", "b", "c"), minBodyHits = 3)
        assertFalse(isSectionImageRelevant(m, "s1", "标题", "a 和 b"))
        assertTrue(isSectionImageRelevant(m, "s1", "标题", "a 和 b 和 c"))
    }

    private val json = Json { ignoreUnknownKeys = true }

    // 单测工作目录通常是 app/ 模块目录,CI 上可能是仓库根目录,做路径兜底
    private fun assetFile(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name", "../app/src/main/assets/$name")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?: error("$name 未找到,工作目录=${File("").absolutePath}")

    @Test
    fun `manifest 与 entries 真实数据一致`() {
        val m = json.decodeFromString(
            ImageManifest.serializer(),
            assetFile("images/manifest.json").readText(),
        )
        val entries = json.decodeFromString(
            com.betterlife.app.data.EntriesFile.serializer(),
            assetFile("entries.json").readText(),
        )
        val secKeys = entries.sections.map { it.key }.toSet()
        assertEquals("manifest 应覆盖全部章节", secKeys, m.sections.keys)
        assertTrue("minBodyHits 至少为 1", m.minBodyHits >= 1)
        for ((secKey, section) in m.sections) {
            assertTrue("$secKey 关键词不能为空", section.keywords.isNotEmpty())
            for (kw in section.keywords) {
                assertEquals("$secKey 关键词必须全小写: $kw", kw.lowercase(), kw)
            }
        }
    }

    private fun assetDir(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name", "../app/src/main/assets/$name")
            .map { File(it) }
            .firstOrNull { it.isDirectory }
            ?: error("$name 目录未找到,工作目录=${File("").absolutePath}")

    /**
     * 配图规则硬检查(规则全文见 docs/BUILD.md「内容管线 · 配图」):
     * 每个在架条目要么有专属图 images/entries/{key}.webp,要么章节图回退能通过关键词门控,
     * 否则条目详情页会裸奔 —— 新增/改条目后此测试变红就是要补图了。
     */
    @Test
    fun `每个在架条目都有可用配图`() {
        val m = json.decodeFromString(
            ImageManifest.serializer(),
            assetFile("images/manifest.json").readText(),
        )
        val entries = json.decodeFromString(
            com.betterlife.app.data.EntriesFile.serializer(),
            assetFile("entries.json").readText(),
        )
        val entryImages = assetDir("images/entries").list().orEmpty().toSet()

        val missing = entries.entries.filter { e ->
            !e.removed &&
                "${e.key}.webp" !in entryImages &&
                !isSectionImageRelevant(m, e.secKey, e.title, e.human + "\n" + e.hay)
        }
        assertTrue(
            "${missing.size} 个在架条目没有任何可用配图(专属图缺失且章节图门控不通过)," +
                "按 docs/BUILD.md 配图规则补图: " +
                missing.take(5).joinToString("; ") { "${it.key} ${it.title}" },
            missing.isEmpty(),
        )
    }
}
