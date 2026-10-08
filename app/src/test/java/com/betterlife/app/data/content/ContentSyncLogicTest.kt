package com.betterlife.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.Json

/**
 * 内容同步的纯逻辑单测：路径决策（chooseSyncPath）、落库后与清单的全量比对
 * （contentMismatch）、旧 id 改写（LegacyEntryId.remap）。
 * 真实网络/DB 部分见 androidTest 的 ContentBootstrapTest（本机无模拟器，CI 也不跑 androidTest）。
 */
class ContentSyncLogicTest {

    private fun manifest(
        version: String = "b",
        patchUrl: String? = "https://x/patch.json",
        patchBase: String? = "a",
        entries: List<ContentManifest.EntryHash> = emptyList(),
    ) = ContentManifest(
        contentVersion = version,
        patchUrl = patchUrl,
        patchBase = patchBase,
        entries = entries,
    )

    // ---------- chooseSyncPath ----------

    @Test
    fun `本地版本与清单一致则 UpToDate`() {
        assertEquals(SyncPath.UP_TO_DATE, chooseSyncPath("b", manifest()))
    }

    @Test
    fun `本地未播种（meta 为空）直接全量——同步顺带完成首装`() {
        assertEquals(SyncPath.FULL, chooseSyncPath(null, manifest()))
    }

    @Test
    fun `本地版本等于 patchBase 且有 patchUrl 走增量`() {
        assertEquals(SyncPath.PATCH, chooseSyncPath("a", manifest()))
    }

    @Test
    fun `基线不匹配或无增量包都回退全量`() {
        assertEquals(SyncPath.FULL, chooseSyncPath("z", manifest()))
        assertEquals(SyncPath.FULL, chooseSyncPath("a", manifest(patchUrl = null)))
        assertEquals(SyncPath.FULL, chooseSyncPath("a", manifest(patchUrl = null, patchBase = null)))
    }

    // ---------- contentMismatch ----------

    @Test
    fun `在架集合与清单一致时无差异`() {
        val m = manifest(entries = listOf(h("k1", "h1"), h("k2", "h2")))
        assertNull(contentMismatch(mapOf("k1" to "h1", "k2" to "h2"), m))
    }

    @Test
    fun `缺失 多出 hash 不同三种偏离都被判为不一致`() {
        val m = manifest(entries = listOf(h("k1", "h1"), h("k2", "h2")))
        assertNotNull("缺一条", contentMismatch(mapOf("k1" to "h1"), m))
        assertNotNull("多一条", contentMismatch(mapOf("k1" to "h1", "k2" to "h2", "k3" to "h3"), m))
        assertNotNull("hash 不同", contentMismatch(mapOf("k1" to "h1", "k2" to "CHANGED"), m))
    }

    // ---------- LegacyEntryId.remap ----------

    private val idToKey = mapOf("02-01" to "47386c8e5c06")

    @Test
    fun `SS-NN 命中映射则改写成 key`() {
        assertEquals("47386c8e5c06", LegacyEntryId.remap("02-01", idToKey))
    }

    @Test
    fun `SS-NN 映射不上（上游已删的条目）保留原值`() {
        assertEquals("09-99", LegacyEntryId.remap("09-99", idToKey))
    }

    @Test
    fun `稳定 key 与 custom 前缀不受影响`() {
        assertEquals("95eb456c7501", LegacyEntryId.remap("95eb456c7501", idToKey))
        assertEquals("custom:uuid-1", LegacyEntryId.remap("custom:uuid-1", idToKey))
    }

    // ---------- manifest 别名表(在线分发) ----------

    private val wireJson = Json { ignoreUnknownKeys = true }

    @Test
    fun `清单携带别名表时原样解析`() {
        val m = wireJson.decodeFromString(
            ContentManifest.serializer(),
            """{"contentVersion":"b","aliases":{"oldKey":"newKey"}}""",
        )
        assertEquals(mapOf("oldKey" to "newKey"), m.aliases)
    }

    @Test
    fun `旧版清单没有 aliases 字段时按空表处理`() {
        val m = wireJson.decodeFromString(
            ContentManifest.serializer(),
            """{"contentVersion":"b","entries":[]}""",
        )
        assertEquals(emptyMap<String, String>(), m.aliases)
    }

    private fun h(key: String, hash: String) = ContentManifest.EntryHash(key = key, hash = hash)
}
