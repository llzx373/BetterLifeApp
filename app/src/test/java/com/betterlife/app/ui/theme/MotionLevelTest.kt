package com.betterlife.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 动效档位的合成逻辑。
 *
 * 这段是纯函数，所以它的正确性不该只靠肉眼在设置页点一遍 —— 尤其是
 * 「系统关了动画」这一条：真机上很难反复复现，写成单测才守得住。
 */
class MotionLevelTest {

    @Test
    fun systemDisabledAlwaysWins() {
        MotionLevel.entries.forEach { userLevel ->
            assertEquals(
                "系统关掉动画时，任何用户档位都必须降到 OFF",
                MotionLevel.OFF,
                effectiveMotionLevel(userLevel, systemAnimationsDisabled = true),
            )
        }
    }

    @Test
    fun userLevelAppliesWhenSystemAnimationsAreOn() {
        MotionLevel.entries.forEach { userLevel ->
            assertEquals(userLevel, effectiveMotionLevel(userLevel, systemAnimationsDisabled = false))
        }
    }

    @Test
    fun fromKeyFallsBackToStandard() {
        assertEquals(MotionLevel.STANDARD, MotionLevel.fromKey(null))
        assertEquals(MotionLevel.STANDARD, MotionLevel.fromKey("no_such_level"))
        MotionLevel.entries.forEach { assertEquals(it, MotionLevel.fromKey(it.key)) }
    }
}
