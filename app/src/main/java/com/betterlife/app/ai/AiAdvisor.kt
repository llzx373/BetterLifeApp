package com.betterlife.app.ai

import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RulesFile
import com.betterlife.app.data.SettingsGateway
import com.betterlife.app.recommend.RecommendationEngine

/**
 * 组合检索器与 LLM 客户端的顾问层。
 * 没有 apiKey 时不报错，返回基于规则引擎/检索结果的降级文本。
 */
class AiAdvisor(
    private val llm: LlmClient,
    private val retriever: EntryRetriever,
    private val settings: SettingsGateway,
) {

    suspend fun ask(
        profile: Profile,
        question: String,
        entries: List<EntryDto>,
        rules: RulesFile,
    ): Result<String> {
        val boostIds = profileBoostIds(profile, rules)
        val top = retriever.search(question, entries, boostIds, topK = 6)

        if (settings.current().apiKey.isBlank()) {
            return Result.success(fallbackAnswer(question, top))
        }

        val context = buildString {
            appendLine("【书中相关条目】")
            for (r in top) appendLine(formatEntry(r.entry))
        }
        val userMsg = buildString {
            appendLine("【用户档案】${profile.describe()}")
            appendLine()
            append(context)
            appendLine()
            append("【用户问题】").append(question)
        }
        return llm.chat(
            listOf(
                ChatMessage(ChatMessage.ROLE_SYSTEM, SystemPrompts.RAG_QA),
                ChatMessage(ChatMessage.ROLE_USER, userMsg),
            ),
            model = "",
        )
    }

    suspend fun explainEntry(profile: Profile, entryId: String, entries: List<EntryDto>): Result<String> {
        val entry = entries.firstOrNull { it.id == entryId }
            ?: return Result.failure(IllegalArgumentException("找不到条目 $entryId"))
        if (settings.current().apiKey.isBlank()) {
            return Result.success(fallbackExplain(profile, entry))
        }
        val userMsg = buildString {
            appendLine("【用户档案】${profile.describe()}")
            appendLine()
            append(formatEntry(entry))
        }
        return llm.chat(
            listOf(
                ChatMessage(ChatMessage.ROLE_SYSTEM, SystemPrompts.EXPLAIN_ENTRY),
                ChatMessage(ChatMessage.ROLE_USER, userMsg),
            ),
            model = "",
        )
    }

    /** 该档案命中的规则所 boost 的条目集合，用于检索加分 */
    private fun profileBoostIds(profile: Profile, rules: RulesFile): Set<String> {
        val matched = RecommendationEngine.matchedRules(profile, rules).filter { it.weight > 0 }
        return matched.flatMapTo(HashSet()) { it.boostEntryIds }
    }

    /** 上下文里的条目格式：id、节号条号、标题、说人话、收益、备注 */
    private fun formatEntry(e: EntryDto): String = buildString {
        appendLine("- [${e.id}] 第${e.sec}节第${e.n}条《${e.title}》")
        appendLine("  说人话：${e.human}")
        appendLine("  收益：${e.gain}")
        if (e.note.isNotBlank()) appendLine("  备注：${e.note}")
    }.trimEnd()

    private fun fallbackAnswer(question: String, top: List<RetrievedEntry>): String = buildString {
        appendLine("还没有配置 API Key，先用书里的条目直接回答你（配置 Key 后会有更连贯的解读）：")
        appendLine()
        if (top.isEmpty()) {
            append("书里没找到和「$question」直接相关的条目，可以换个说法再问问。")
        } else {
            for (r in top.take(3)) {
                val e = r.entry
                appendLine("第${e.sec}节第${e.n}条《${e.title}》：${e.human}")
                appendLine()
            }
        }
        append("以上是通用口径的建议，不替代医生或律师的意见。")
    }.trim()

    private fun fallbackExplain(profile: Profile, entry: EntryDto): String = buildString {
        append("《${entry.title}》：${entry.human} ")
        if (entry.gain.isNotBlank()) append("收益方面：${entry.gain} ")
        append("（配置 API Key 后可生成结合你档案的个性化解读。）")
    }
}
