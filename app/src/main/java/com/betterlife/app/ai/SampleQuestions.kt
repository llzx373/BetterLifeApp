package com.betterlife.app.ai

import com.betterlife.app.data.Alcohol
import com.betterlife.app.data.Chronic
import com.betterlife.app.data.Exercise
import com.betterlife.app.data.Profile
import com.betterlife.app.data.Smoking
import com.betterlife.app.data.SugaryDrinks

/**
 * 聊天页空态示例问题（N3b）的纯逻辑：按档案字段出静态模板，不调模型、零 Android 依赖。
 * 命中的字段按固定优先级取前 [MAX] 条；空档案/无命中时用 [GENERIC] 兜底。
 *
 * 已知性（N1 knownFields）不参与判定：档案经 Room 往返后不保留已知性，
 * 读出来的默认值档案就是「老用户已填完」，按字段实际值出对应模板即可。
 */
object SampleQuestions {

    const val MAX = 3

    /** 空档案/无字段命中时的通用兜底 */
    val GENERIC: List<String> = listOf(
        "久坐一天,先改哪一件小事?",
        "每天睡不够,先补什么?",
        "体检报告上最该盯哪几个指标?",
    )

    fun forProfile(profile: Profile): List<String> {
        if (profile.isEmpty) return GENERIC
        val hits = mutableListOf<String>()
        // 优先级：健康风险信号强的在前（吸烟/慢病/孕期 > 酒/槟榔 > 生活方式）
        if (profile.smoking == Smoking.YES) hits += "想戒烟,第一步做什么?"
        if (profile.smoking == Smoking.QUIT) hits += "戒烟之后,身体多久能恢复?"
        // chronic 是多选集合,按枚举声明顺序遍历保证输出稳定
        for (c in Chronic.entries) {
            if (c !in profile.chronic) continue
            hits += when (c) {
                Chronic.HYPERTENSION -> "有高血压,日常最该注意什么?"
                Chronic.DIABETES -> "有糖尿病,吃饭先改什么?"
                Chronic.KIDNEY -> "肾不太好,饮食上先避开什么?"
                Chronic.HEART -> "心脏不太好,运动上要注意什么?"
                Chronic.OTHER -> "有慢性病,日常先盯哪几件事?"
            }
        }
        if (profile.pregnant) hits += "孕期睡眠不好,怎么办?"
        if (profile.alcohol == Alcohol.OFTEN) hits += "经常喝酒,怎么把伤害降下来?"
        if (profile.betelNut) hits += "嚼槟榔,怎么戒掉?"
        if (profile.sugaryDrinks == SugaryDrinks.DAILY) hits += "每天都喝含糖饮料,怎么减?"
        if (profile.sleepShort) hits += "睡不够,怎么把睡眠补回来?"
        if (profile.exercise == Exercise.NONE) hits += "平时不运动,从哪件小事开始?"
        if (profile.financialStress) hits += "压力大到影响生活,先做什么?"
        // 命中不足 MAX 条时用通用问题补齐;通用问题不会与模板重复（模板都带具体情境）
        return (hits + GENERIC).take(MAX)
    }
}
