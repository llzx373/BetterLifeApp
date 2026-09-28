package com.betterlife.app.data.content

import com.betterlife.app.data.EntriesFile
import com.betterlife.app.data.RulesFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 捆绑内容包（assets/entries.json + relevance_rules.json）的完整性校验。
 * 这是 Room 播种与 SS-NN→key 迁移的数据前提：格式退化（比如 key 字段丢了）
 * 会在首启时静默坏掉用户数据，必须在单测层拦住。
 *
 * key 的生成约定：tools/build_content.py 用 sha1(节标题) / sha1(节标题+条目标题)
 * 取前 12 hex —— 上游插入条目只会让 SS-NN 顺延，标题不变则 key 不变，
 * 这就是用户数据（任务/收藏/笔记里的 entryId）能跨版本存活的根基。
 */
class ContentPackIntegrityTest {

    private val json = Json { ignoreUnknownKeys = true }

    // 与 RulesConsistencyTest 同款：单测工作目录可能是 app/ 模块目录或仓库根目录
    private fun assetFile(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name", "../app/src/main/assets/$name")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?: error("$name 未找到，工作目录=${File("").absolutePath}")

    private fun loadEntries(): EntriesFile =
        json.decodeFromString(EntriesFile.serializer(), assetFile("entries.json").readText())

    private fun loadRules(): RulesFile =
        json.decodeFromString(RulesFile.serializer(), assetFile("relevance_rules.json").readText())

    @Test
    fun `每个条目的 key secKey hash 非空且 key 是 12 位小写 hex`() {
        val keyFormat = Regex("[0-9a-f]{12}")
        val bad = loadEntries().entries.filter {
            it.key.isBlank() || !keyFormat.matches(it.key) ||
                it.secKey.isBlank() || !keyFormat.matches(it.secKey) ||
                it.hash.isBlank()
        }
        assertTrue("条目缺 key/secKey/hash 或格式不对: ${bad.map { it.id }}", bad.isEmpty())
    }

    @Test
    fun `key 全局唯一且节 key 唯一`() {
        val file = loadEntries()
        val keys = file.entries.map { it.key }
        assertEquals("条目 key 有重复", keys.size, keys.toSet().size)
        val secKeys = file.sections.map { it.key }
        assertTrue("节 key 为空或格式不对", secKeys.all { Regex("[0-9a-f]{12}").matches(it) })
        assertEquals("节 key 有重复", secKeys.size, secKeys.toSet().size)
    }

    @Test
    fun `每条 entry 的 secKey 就是其 sec 对应节的 key`() {
        val file = loadEntries()
        val secKeyByN = file.sections.associate { it.n to it.key }
        val bad = file.entries.filter { secKeyByN[it.sec] != it.secKey }
        assertTrue("secKey 与节表对不上: ${bad.map { it.id }}", bad.isEmpty())
    }

    @Test
    fun `id(SS-NN) 与 key 一一对应——首启迁移映射的前提`() {
        val entries = loadEntries().entries
        val ids = entries.map { it.id }
        assertTrue("id 不是 SS-NN 形态: ${ids.filterNot { LegacyEntryId.REGEX.matches(it) }}",
            ids.all { LegacyEntryId.REGEX.matches(it) })
        assertEquals("id 有重复", ids.size, ids.toSet().size)
        // id 与 key 各自唯一，合起来即双射
        assertEquals(ids.size, entries.associate { it.id to it.key }.size)
        assertEquals(ids.size, entries.associate { it.key to it.id }.size)
    }

    @Test
    fun `已知条目的 id→key 锁定——防 assets 退回旧格式或管线漂移`() {
        // key 由 tools/build_content.py 从标题派生：标题不变 key 不变。
        // 这三条若变动，说明生成管线或标题被改了，用户数据迁移映射必须跟着重审。
        val byId = loadEntries().entries.associate { it.id to it.key }
        assertEquals("47386c8e5c06", byId["01-01"])
        assertEquals("95eb456c7501", byId["02-12"])
        assertEquals("cd69bdc261d0", byId["33-01"])
    }

    @Test
    fun `规则文件里的 id 与节引用都能在内容包里解析到 key`() {
        val file = loadEntries()
        val entryKeys = file.entries.mapTo(HashSet()) { it.key }
        val sectionKeys = file.sections.mapTo(HashSet()) { it.key }
        val rules = loadRules()

        val badSeeds = rules.seedEntryIds.filter { it !in entryKeys }
        assertTrue("seedEntryIds 指向不存在的条目: $badSeeds", badSeeds.isEmpty())
        rules.rules.forEachIndexed { index, rule ->
            val badBoost = rule.boostEntryIds.filter { it !in entryKeys }
            val badExclude = rule.excludeEntryIds.filter { it !in entryKeys }
            val badSecs = rule.boostSections.filter { it !in sectionKeys }
            assertTrue(
                "rule#$index 引用解析失败: boost=$badBoost exclude=$badExclude sections=$badSecs",
                badBoost.isEmpty() && badExclude.isEmpty() && badSecs.isEmpty(),
            )
        }
    }
}
