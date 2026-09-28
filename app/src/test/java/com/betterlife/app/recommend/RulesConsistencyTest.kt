package com.betterlife.app.recommend

import com.betterlife.app.data.AgeRange
import com.betterlife.app.data.Alcohol
import com.betterlife.app.data.Children
import com.betterlife.app.data.Chronic
import com.betterlife.app.data.EntriesFile
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Exercise
import com.betterlife.app.data.Gender
import com.betterlife.app.data.Goal
import com.betterlife.app.data.Housing
import com.betterlife.app.data.Occupation
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RulesFile
import com.betterlife.app.data.Smoking
import com.betterlife.app.data.SugaryDrinks
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 真实 assets/relevance_rules.json 与 Profile.fieldValues() 的一致性：
 * 规则 when 的字段名与取值必须都落在档案实际可产生的值域内，否则规则永不命中、静默失效。
 * 值域不手抄，而是用各字段的取值变体构造 Profile 调 fieldValues() 实测得出。
 */
class RulesConsistencyTest {

    private val json = Json { ignoreUnknownKeys = true }

    // 单测工作目录通常是 app/ 模块目录，CI 上可能是仓库根目录，做路径兜底
    private fun assetFile(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name", "../app/src/main/assets/$name")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?: error("$name 未找到，工作目录=${File("").absolutePath}")

    private fun loadRules(): RulesFile =
        json.decodeFromString(RulesFile.serializer(), assetFile("relevance_rules.json").readText())

    /** 用一组档案变体实测 field 可产生的全部取值 */
    private fun producible(field: String, profiles: List<Profile>): Set<String> =
        profiles.flatMap { it.fieldValues(field) }.toSet()

    private fun boolField(field: String, copy: Profile.(Boolean) -> Profile): Set<String> =
        producible(field, listOf(Profile().copy(false), Profile().copy(true)))

    // chronic 的变体里特意放了 Profile()（无慢病）：fieldValues 给空慢性病的取值就在这里被实测
    private val domains: Map<String, Set<String>> = mapOf(
        "ageRange" to producible("ageRange", enumValues<AgeRange>().map { Profile(ageRange = it) }),
        "gender" to producible("gender", enumValues<Gender>().map { Profile(gender = it) }),
        "smoking" to producible("smoking", enumValues<Smoking>().map { Profile(smoking = it) }),
        "secondhandSmoke" to boolField("secondhandSmoke") { copy(secondhandSmoke = it) },
        "alcohol" to producible("alcohol", enumValues<Alcohol>().map { Profile(alcohol = it) }),
        "betelNut" to boolField("betelNut") { copy(betelNut = it) },
        "sugaryDrinks" to producible("sugaryDrinks", enumValues<SugaryDrinks>().map { Profile(sugaryDrinks = it) }),
        "exercise" to producible("exercise", enumValues<Exercise>().map { Profile(exercise = it) }),
        "sleepShort" to boolField("sleepShort") { copy(sleepShort = it) },
        "chronic" to producible("chronic", listOf(Profile()) + enumValues<Chronic>().map { Profile(chronic = setOf(it)) }),
        "occupation" to producible("occupation", enumValues<Occupation>().map { Profile(occupation = it) }),
        "financialStress" to boolField("financialStress") { copy(financialStress = it) },
        "housing" to producible("housing", enumValues<Housing>().map { Profile(housing = it) }),
        "children" to producible("children", enumValues<Children>().map { Profile(children = it) }),
        "hasElderly" to boolField("hasElderly") { copy(hasElderly = it) },
        "pregnant" to boolField("pregnant") { copy(pregnant = it) },
        "planningAbroad" to boolField("planningAbroad") { copy(planningAbroad = it) },
        "goals" to producible("goals", enumValues<Goal>().map { Profile(goals = setOf(it)) }),
    )

    @Test
    fun `值域表里的字段都已注册到 fieldValues`() {
        val empty = domains.filterValues { it.isEmpty() }.keys
        assertTrue("字段未在 Profile.fieldValues() 注册: $empty", empty.isEmpty())
    }

    @Test
    fun `规则 when 的字段与取值都在档案可产生的值域内`() {
        val problems = mutableListOf<String>()
        loadRules().rules.forEachIndexed { index, rule ->
            rule.`when`.forEach { (field, values) ->
                val domain = domains[field]
                if (domain == null) {
                    problems += "rule#$index: 未知字段 $field（${rule.reason}）"
                } else {
                    (values.toSet() - domain).forEach {
                        problems += "rule#$index: $field=$it 档案永远产生不了，规则死条款（${rule.reason}）"
                    }
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `真实规则下无慢病用户被剔除 02-12 而高血压用户保留`() {
        // 规则已改用稳定 key：从真实 entries.json 取 "02-12" 对应的 key 再参与断言
        val file = json.decodeFromString(EntriesFile.serializer(), assetFile("entries.json").readText())
        val legacy = file.entries.first { it.id == "02-12" }
        val entries = listOf(
            EntryDto(
                id = legacy.key, sec = legacy.sec, n = legacy.n, title = "t",
                key = legacy.key, secKey = legacy.secKey,
                lens = "死亡率", ratio = "高", grade = "A",
            ),
        )
        val rules = loadRules()
        val engine = RecommendationEngine()
        val healthy = engine.recommend(Profile(), entries, rules, emptySet())
        assertTrue("无慢病用户不应看到 02-12", healthy.values.flatten().none { it.entry.id == legacy.key })
        val hypertension = engine.recommend(Profile(chronic = setOf(Chronic.HYPERTENSION)), entries, rules, emptySet())
        assertTrue("高血压用户应保留 02-12", hypertension.values.flatten().any { it.entry.id == legacy.key })
    }

    @Test
    fun `每日种子池不含 todo 条目`() {
        val entries = json.decodeFromString(EntriesFile.serializer(), assetFile("entries.json").readText())
        // 种子池与 todo 集合都用稳定 key 比较（assets 里 EntryDto.id 仍是 SS-NN，见 EntryDto.id 约定）
        val todoKeys = entries.entries.filter { it.todo }.mapTo(mutableSetOf()) { it.key }
        val bad = loadRules().seedEntryIds.filter { it in todoKeys }
        assertTrue("seedEntryIds 含 todo 条目，永远不会被挑中: $bad", bad.isEmpty())
    }
}
