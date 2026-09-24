package com.betterlife.app.data

/**
 * entries.json / relevance_rules.json 里的机器可读取值。
 *
 * UI 层按这些值做分支时一律用常量，不要散落中文字面量：
 * 既避免与内容管线漂移，也让「UI 包内 0 处中文字面量」这条规约可以被 lint 卡住。
 */
object EntryKeys {
    // 口径（lens）
    const val LENS_MORTALITY = "死亡率"
    const val LENS_MONEY = "金钱"
    const val LENS_TIME = "时间"
    const val LENS_FREEDOM = "自由"

    // 性价比档（ratio）
    const val RATIO_VERY_HIGH = "极高"
    const val RATIO_HIGH = "高"
    const val RATIO_NORMAL = "一般"

    // 成本档位（money / time / will）
    const val COST_MORE = "多"
    const val COST_LESS = "少"
    const val COST_MID = "中"
    const val WILL_YES = "是"
    const val WILL_SOME = "些"

    // 收益档（level）
    const val GAIN_BIG = "大"
    const val GAIN_MID = "中"
    const val GAIN_SMALL = "小"
}
