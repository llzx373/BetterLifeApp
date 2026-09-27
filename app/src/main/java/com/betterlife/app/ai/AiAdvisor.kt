package com.betterlife.app.ai

import com.betterlife.app.data.AiProvider
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.Profile
import com.betterlife.app.data.RulesFile
import com.betterlife.app.data.SettingsGateway
import com.betterlife.app.data.resolveActiveProvider
import com.betterlife.app.recommend.RecommendationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** 一条答案来源：知识库条目（entryId + 「第X节第Y条」标签）或网页（标题 + 链接） */
data class AiSource(
    val label: String,
    val entryId: String? = null,
    val url: String? = null,
)

/** 回答 + 结构化来源；来源以注入 prompt 的资料为准，不解析模型文本 */
data class AiAnswer(
    val text: String,
    val sources: List<AiSource>,
)

/** 流式回答事件：Delta 增量上屏，Done 携带全文与来源（降级/单次兜底也直接走 Done） */
sealed interface AiStreamEvent {
    data class Delta(val text: String) : AiStreamEvent
    data class Done(val fullText: String, val sources: List<AiSource>) : AiStreamEvent
}

/** 一次提问的预处理结果：拼好的 prompt、来源；degraded 非空 = 不发模型的降级答案 */
private class PreparedAsk(
    val messages: List<ChatMessage>,
    val sources: List<AiSource>,
    val degraded: AiAnswer? = null,
)

/**
 * 组合检索器、搜索服务与 LLM 客户端的顾问层。
 * 供应商没有 apiKey 时不报错，返回基于检索/搜索结果的降级文本。
 */
class AiAdvisor(
    private val llm: LlmClient,
    private val retriever: EntryRetriever,
    private val settings: SettingsGateway,
    private val webSearcher: WebSearcher,
) {

    /**
     * 用指定 [provider] 回答。[useWeb] 为 true 走「先搜后问」（博查 web-search 结果注入 prompt），
     * false 走本地知识库 RAG。
     */
    suspend fun ask(
        profile: Profile,
        question: String,
        entries: List<EntryDto>,
        rules: RulesFile,
        provider: AiProvider,
        useWeb: Boolean,
    ): Result<AiAnswer> =
        if (useWeb) askWeb(profile, question, provider)
        else askKnowledge(profile, question, entries, rules, provider)

    private suspend fun askKnowledge(
        profile: Profile,
        question: String,
        entries: List<EntryDto>,
        rules: RulesFile,
        provider: AiProvider,
    ): Result<AiAnswer> = complete(prepareKnowledge(profile, question, entries, rules, provider), provider)

    /** 检索 + 拼 prompt；无 key 时产出降级答案，不发模型 */
    private fun prepareKnowledge(
        profile: Profile,
        question: String,
        entries: List<EntryDto>,
        rules: RulesFile,
        provider: AiProvider,
    ): PreparedAsk {
        val boostIds = profileBoostIds(profile, rules)
        val top = retriever.search(question, entries, boostIds, topK = 6)
        val sources = top.map { r ->
            AiSource(
                label = "第${r.entry.sec}节第${r.entry.n}条《${r.entry.title}》",
                entryId = r.entry.id,
            )
        }

        if (provider.apiKey.isBlank()) {
            // 降级文本只引用了前 3 条，来源也随之收敛
            val degraded = AiAnswer(fallbackAnswer(question, top), sources.take(3))
            return PreparedAsk(emptyList(), degraded.sources, degraded)
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
        val messages = listOf(
            ChatMessage(ChatMessage.ROLE_SYSTEM, SystemPrompts.RAG_QA),
            ChatMessage(ChatMessage.ROLE_USER, userMsg),
        )
        return PreparedAsk(messages, sources)
    }

    private suspend fun askWeb(
        profile: Profile,
        question: String,
        provider: AiProvider,
    ): Result<AiAnswer> {
        val prepared = runCatching { prepareWeb(profile, question, provider) }
            .getOrElse { return Result.failure(it) }
        return complete(prepared, provider)
    }

    /** 搜索 + 拼 prompt；搜索失败直接抛出（单次入口转成 Result，流式入口上抛给调用方） */
    private suspend fun prepareWeb(
        profile: Profile,
        question: String,
        provider: AiProvider,
    ): PreparedAsk {
        val items = webSearcher.search(question).getOrThrow()
        val sources = items.map { AiSource(label = it.title, url = it.url) }

        if (provider.apiKey.isBlank()) {
            val degraded = AiAnswer(rawSearchAnswer(items), sources)
            return PreparedAsk(emptyList(), sources, degraded)
        }

        val userMsg = buildString {
            appendLine("【用户档案】${profile.describe()}")
            appendLine()
            appendLine("【网络搜索结果】")
            appendLine(formatSearchItems(items))
            appendLine()
            append("【用户问题】").append(question)
        }
        val messages = listOf(
            ChatMessage(ChatMessage.ROLE_SYSTEM, SystemPrompts.WEB_QA),
            ChatMessage(ChatMessage.ROLE_USER, userMsg),
        )
        return PreparedAsk(messages, sources)
    }

    /** 单次请求发模型；degraded 非空时直接返回降级答案 */
    private suspend fun complete(prepared: PreparedAsk, provider: AiProvider): Result<AiAnswer> {
        if (prepared.degraded != null) return Result.success(prepared.degraded)
        return llm.chat(
            prepared.messages,
            model = provider.model,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
        ).map { AiAnswer(it, prepared.sources) }
    }

    /**
     * 流式回答：先发 Delta 增量，结束发 Done。流式失败且没收到任何内容、或流为空时
     * 回退单次请求（收到部分内容后断流则保留已收到的部分）；单次也失败则向上抛。
     * 搜索失败、无 key 降级等行为与单次 ask 一致。
     */
    fun askStream(
        profile: Profile,
        question: String,
        entries: List<EntryDto>,
        rules: RulesFile,
        provider: AiProvider,
        useWeb: Boolean,
    ): Flow<AiStreamEvent> = flow {
        val prepared = if (useWeb) {
            prepareWeb(profile, question, provider)
        } else {
            prepareKnowledge(profile, question, entries, rules, provider)
        }
        if (prepared.degraded != null) {
            emit(AiStreamEvent.Done(prepared.degraded.text, prepared.degraded.sources))
            return@flow
        }
        val acc = StringBuilder()
        try {
            llm.chatStream(prepared.messages, provider.model, provider.baseUrl, provider.apiKey)
                .collect { delta ->
                    acc.append(delta)
                    emit(AiStreamEvent.Delta(delta))
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 流式失败：acc 非空走「保留残缺内容」，为空走下面单次兜底
        }
        if (acc.isNotEmpty()) {
            emit(AiStreamEvent.Done(acc.toString(), prepared.sources))
        } else {
            val answer = complete(prepared, provider).getOrThrow()
            emit(AiStreamEvent.Done(answer.text, answer.sources))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * C5③ 周期报告解读：统计摘要已经完整地在 [statsSummary] 里，不做检索、不搜网，
     * 直接用 STATS_INTERPRET 提示词流式生成回顾。失败回退单次，单次也失败向上抛。
     */
    fun interpretStatsStream(
        profile: Profile,
        statsSummary: String,
        provider: AiProvider,
    ): Flow<AiStreamEvent> = flow {
        if (provider.apiKey.isBlank()) {
            emit(AiStreamEvent.Done(NO_KEY_STATS_HINT, emptyList()))
            return@flow
        }
        val messages = listOf(
            ChatMessage(ChatMessage.ROLE_SYSTEM, SystemPrompts.STATS_INTERPRET),
            ChatMessage(ChatMessage.ROLE_USER, "【用户档案】${profile.describe()}\n\n$statsSummary"),
        )
        val acc = StringBuilder()
        try {
            llm.chatStream(messages, provider.model, provider.baseUrl, provider.apiKey)
                .collect { delta ->
                    acc.append(delta)
                    emit(AiStreamEvent.Delta(delta))
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 流式失败：acc 非空保留残缺内容，为空走单次兜底
        }
        if (acc.isNotEmpty()) {
            emit(AiStreamEvent.Done(acc.toString(), emptyList()))
        } else {
            val text = llm.chat(messages, provider.model, provider.baseUrl, provider.apiKey).getOrThrow()
            emit(AiStreamEvent.Done(text, emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun explainEntry(profile: Profile, entryId: String, entries: List<EntryDto>): Result<String> {
        val entry = entries.firstOrNull { it.id == entryId }
            ?: return Result.failure(IllegalArgumentException("找不到条目 $entryId"))
        if (!hasApiKey()) {
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

    /** 当前生效的供应商（active 且 enabled，否则第一个 enabled）有没有可用的 key */
    private suspend fun hasApiKey(): Boolean {
        val cfg = settings.current()
        return !resolveActiveProvider(cfg.aiProviders, cfg.activeProviderId)?.apiKey.isNullOrBlank()
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

    /** 搜索结果注入 prompt 的编号纯文本格式（同 HeartKindle 的 formatItems） */
    private fun formatSearchItems(items: List<WebSearchItem>): String =
        items.mapIndexed { index, item ->
            buildString {
                append("${index + 1}. ${item.title}\n${item.url}")
                item.datePublished?.takeIf { it.isNotBlank() }?.let { append("\n发布时间：$it") }
                append("\n${item.snippet}")
            }
        }.joinToString("\n\n")

    /** 互联网模式 + 没配模型 key：不发模型，直接给原始搜索结果 */
    private fun rawSearchAnswer(items: List<WebSearchItem>): String = buildString {
        appendLine("还没有配置模型的 API Key，先把搜到的原始结果给你（配置后可生成连贯的总结）：")
        appendLine()
        append(formatSearchItems(items))
    }.trim()

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

    companion object {
        private const val NO_KEY_STATS_HINT = "还没有配置 API Key，配置后再来回顾这段时间吧。"
    }
}
