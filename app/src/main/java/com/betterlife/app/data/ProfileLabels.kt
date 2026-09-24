package com.betterlife.app.data

import androidx.annotation.StringRes
import com.betterlife.app.R

/**
 * 档案字段 → 文案资源的映射。
 *
 * 从前写在我的页的界面文件里,UI 同时承担了领域映射和渲染;
 * 放到 data 层之后界面只负责消费,映射本身也能被单测覆盖。
 */
object ProfileLabels {

    @StringRes
    fun goal(goal: Goal): Int = when (goal) {
        Goal.HEALTH -> R.string.goal_health
        Goal.MONEY -> R.string.goal_money
        Goal.TIME -> R.string.goal_time
        Goal.CAREER -> R.string.goal_career
        Goal.FAMILY -> R.string.goal_family
        Goal.RELAX -> R.string.goal_relax
    }

    /** 职业后缀;没有对应文案时返回 null */
    @StringRes
    fun occupationSuffix(occupation: Occupation): Int? = when (occupation) {
        Occupation.PROGRAMMER -> R.string.mine_suffix_programmer
        Occupation.STUDENT -> R.string.mine_suffix_student
        else -> null
    }

    /** 摘要卡里要追加的状态后缀,按展示顺序 */
    fun summaryFlags(profile: Profile): List<Int> = buildList {
        if (profile.smoking == Smoking.YES) add(R.string.mine_suffix_smoking)
        if (profile.exercise == Exercise.NONE) add(R.string.mine_suffix_no_exercise)
    }
}
