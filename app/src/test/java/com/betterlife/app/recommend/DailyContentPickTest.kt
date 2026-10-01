package com.betterlife.app.recommend

import com.betterlife.app.data.AppSettings
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RuleDto
import com.betterlife.app.data.RulesFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyContentPickTest {

    private fun entry(
        id: String,
        sec: Int,
        ratio: String = "高",
        grade: String = "A",
        human: String = "h$id",
        todo: Boolean = false,
        removed: Boolean = false,
        dispute: Boolean = false,
    ) = EntryDto(
        id = id, sec = sec, n = id.substringAfter('-').toInt(), title = "t$id",
        secKey = "s$sec", ratio = ratio, grade = grade, human = human,
        todo = todo, removed = removed, dispute = dispute,
    )

    private val entries = listOf(
        entry("01-01", 1, ratio = "极高"),
        entry("01-02", 1),
        entry("01-03", 1, removed = true),
        entry("02-01", 2, grade = "B"),
        entry("02-02", 2, todo = true),
        entry("03-01", 3, ratio = "一般"),
        entry("03-02", 3, dispute = true),
        entry("04-01", 4, human = ""),
    )

    /** 普惠规则：所有人命中，boost 第 1/2 节 */
    private fun rules(
        boostSecs: List<String> = listOf("s1", "s2"),
        weight: Int = 10,
        exclude: List<String> = emptyList(),
    ) = RulesFile(
        rules = listOf(RuleDto(emptyMap(), emptyList(), boostSecs, exclude, weight, "r")),
    )

    private val today = LocalDate.parse("2026-10-01")

    // ---------- 选条排除规则 ----------

    @Test
    fun `结果确定可复现`() {
        val a = pickDailyContent(Profile.EMPTY, entries, rules(), emptySet(), emptySet())
        val b = pickDailyContent(Profile.EMPTY, entries, rules(), emptySet(), emptySet())
        assertEquals(a, b)
    }

    @Test
    fun `removed todo dispute 与 human 空白的条目不入选`() {
        // 无 boost 命中时全量按稳定排序:01-01(极高) 应胜出;
        // 把 01-01/01-02 都排除后,顺序应落到 03-01(一般) 之前的 02-01
        val pick = pickDailyContent(
            Profile.EMPTY, entries, rules(),
            excludedIds = setOf("01-01", "01-02"),
            pushedIds = emptySet(),
        )
        assertEquals("02-01", pick?.id)
    }

    @Test
    fun `DONE 与 DISMISSED 走 excludedIds 剔除`() {
        val pick = pickDailyContent(
            Profile.EMPTY, entries, rules(),
            excludedIds = setOf("01-01"),
            pushedIds = emptySet(),
        )
        assertEquals("01-02", pick?.id)
    }

    @Test
    fun `命中规则的 exclude 生效`() {
        val pick = pickDailyContent(
            Profile.EMPTY, entries, rules(exclude = listOf("01-01")),
            excludedIds = emptySet(), pushedIds = emptySet(),
        )
        assertEquals("01-02", pick?.id)
    }

    @Test
    fun `档案命中分高的优先,并列走稳定排序`() {
        // 档案命中的规则只 boost 第 2 节 → 02-01 得分 > 普惠条目
        val fieldRules = RulesFile(
            rules = listOf(
                RuleDto(emptyMap(), emptyList(), listOf("s1"), emptyList(), 5, "普惠"),
                RuleDto(mapOf("smoking" to listOf("yes")), emptyList(), listOf("s2"), emptyList(), 10, "吸烟"),
            ),
        )
        val profile = Profile(smoking = com.betterlife.app.data.Smoking.YES)
        val pick = pickDailyContent(profile, entries, fieldRules, emptySet(), emptySet())
        assertEquals("02-01", pick?.id)
        // 空档案不命中带字段的规则 → 只有普惠 boost 生效,01-01 胜出
        val emptyPick = pickDailyContent(Profile.EMPTY, entries, fieldRules, emptySet(), emptySet())
        assertEquals("01-01", emptyPick?.id)
    }

    // ---------- 30 天去重 ----------

    @Test
    fun `近 30 天已推过的排除,满 30 天又可推`() {
        val pushed29 = pushedIdsInWindow(listOf("01-01,${today.minusDays(29)}"), today)
        assertTrue("01-01" in pushed29)
        assertEquals(
            "01-02",
            pickDailyContent(Profile.EMPTY, entries, rules(), emptySet(), pushed29)?.id,
        )
        // 满 30 天出窗,01-01 回来
        val pushed30 = pushedIdsInWindow(listOf("01-01,${today.minusDays(30)}"), today)
        assertFalse("01-01" in pushed30)
        assertEquals(
            "01-01",
            pickDailyContent(Profile.EMPTY, entries, rules(), emptySet(), pushed30)?.id,
        )
    }

    @Test
    fun `裁剪清掉过期行与坏行`() {
        val pruned = prunePushedLines(
            listOf("01-01,${today.minusDays(30)}", "bad-line", "01-02,${today.minusDays(1)}", ",2026-01-01"),
            today,
        )
        assertEquals(listOf("01-02,${today.minusDays(1)}"), pruned)
    }

    @Test
    fun `空候选不发`() {
        // 全部可推条目都进 30 天窗口 → 无可推,返回 null 而不是硬凑
        val allPushed = entries.map { "${it.id},$today" }
        val pick = pickDailyContent(
            Profile.EMPTY, entries, rules(),
            excludedIds = emptySet(),
            pushedIds = pushedIdsInWindow(allPushed, today),
        )
        assertNull(pick)
    }

    // ---------- 触发时刻 ----------

    @Test
    fun `08点07分之前触发当天,之后顺延次日,避开整点`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val before = Instant.parse("2026-10-01T00:00:00Z") // 北京时间 08:00
        val t1 = Instant.ofEpochMilli(nextDailyContentMillis(before.toEpochMilli(), zone))
            .atZone(zone)
        assertEquals(8, t1.hour)
        assertEquals(7, t1.minute)
        assertEquals(1, t1.dayOfMonth)

        val after = Instant.parse("2026-10-01T00:10:00Z") // 北京时间 08:10,已过点
        val t2 = Instant.ofEpochMilli(nextDailyContentMillis(after.toEpochMilli(), zone))
            .atZone(zone)
        assertEquals(2, t2.dayOfMonth)
        assertEquals(8, t2.hour)
        assertEquals(7, t2.minute)
    }

    // ---------- 开关默认值 ----------

    @Test
    fun `每日一条默认关`() {
        assertFalse(AppSettings().dailyContentEnabled)
    }
}
