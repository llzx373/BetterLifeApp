package com.betterlife.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLabelsTest {

    @Test
    fun `每个目标都有对应文案`() {
        Goal.entries.forEach { goal ->
            assertTrue("目标 $goal 缺少文案映射", ProfileLabels.goal(goal) != 0)
        }
    }

    @Test
    fun `职业后缀只在程序员与学生时有值`() {
        assertNotNull(ProfileLabels.occupationSuffix(Occupation.PROGRAMMER))
        assertNotNull(ProfileLabels.occupationSuffix(Occupation.STUDENT))
        assertNull(ProfileLabels.occupationSuffix(Occupation.OTHER))
    }

    @Test
    fun `摘要后缀只在吸烟或几乎不运动时追加`() {
        // 默认档案:不吸烟、exercise 默认是「几乎不运动」,所以只有一个后缀
        val default = ProfileLabels.summaryFlags(Profile())
        assertEquals(1, default.size)

        val clean = ProfileLabels.summaryFlags(Profile(smoking = Smoking.NO, exercise = Exercise.OK))
        assertTrue(clean.isEmpty())

        val risky = ProfileLabels.summaryFlags(Profile(smoking = Smoking.YES, exercise = Exercise.NONE))
        assertEquals(2, risky.size)
    }
}
