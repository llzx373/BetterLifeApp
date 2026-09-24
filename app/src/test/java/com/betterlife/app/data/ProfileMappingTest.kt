package com.betterlife.app.data

import com.betterlife.app.data.db.ProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMappingTest {

    @Test
    fun `Profile 与 ProfileEntity 互转无损`() {
        val profile = Profile(
            ageRange = AgeRange.A36_45,
            gender = Gender.FEMALE,
            smoking = Smoking.QUIT,
            secondhandSmoke = true,
            alcohol = Alcohol.SOMETIMES,
            betelNut = true,
            sugaryDrinks = SugaryDrinks.DAILY,
            exercise = Exercise.LOW,
            sleepShort = true,
            chronic = setOf(Chronic.HYPERTENSION, Chronic.KIDNEY),
            occupation = Occupation.PROGRAMMER,
            financialStress = true,
            housing = Housing.OWN,
            children = Children.BABY,
            hasElderly = true,
            pregnant = false,
            planningAbroad = true,
            goals = setOf(Goal.HEALTH, Goal.TIME, Goal.RELAX),
        )
        val roundTrip = profile.toEntity().toProfile()
        assertEquals(profile, roundTrip)
    }

    @Test
    fun `默认档案互转无损`() {
        val profile = Profile()
        assertEquals(profile, profile.toEntity().toProfile())
    }

    @Test
    fun `多选字段序列化为逗号分隔且空集存空串`() {
        val entity = Profile(
            chronic = setOf(Chronic.DIABETES, Chronic.HEART),
            goals = emptySet(),
        ).toEntity()
        assertEquals("", entity.goals)
        val keys = entity.chronic.split(',')
        assertTrue("diabetes" in keys && "heart" in keys && keys.size == 2)
        // 空串还原为空集
        assertTrue(entity.toProfile().goals.isEmpty())
    }

    @Test
    fun `未知枚举值回退默认而不是崩溃`() {
        val entity = ProfileEntity(smoking = "vape", ageRange = "17-")
        val profile = entity.toProfile()
        assertEquals(Smoking.NO, profile.smoking)
        assertEquals(AgeRange.A26_35, profile.ageRange)
    }

    @Test
    fun `多选字段忽略未知项`() {
        val entity = ProfileEntity(chronic = "kidney,unknown,heart")
        val profile = entity.toProfile()
        assertEquals(setOf(Chronic.KIDNEY, Chronic.HEART), profile.chronic)
    }
}
