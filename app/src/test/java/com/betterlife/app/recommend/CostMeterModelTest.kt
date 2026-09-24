package com.betterlife.app.recommend

import com.betterlife.app.data.EntryDto
import org.junit.Assert.assertEquals
import org.junit.Test

class CostMeterModelTest {

    private fun entry(
        money: String = "",
        time: String = "",
        will: String = "",
        level: String = "",
    ): EntryDto = EntryDto(
        id = "01-01",
        sec = 1,
        n = 1,
        title = "t",
        money = money,
        time = time,
        will = will,
        level = level,
    )

    @Test
    fun moneyLevelMapsThreeTiers() {
        assertEquals(2, CostMeterModel.moneyLevel(entry(money = "多")))
        assertEquals(1, CostMeterModel.moneyLevel(entry(money = "少")))
        assertEquals(0, CostMeterModel.moneyLevel(entry(money = "0")))
        assertEquals(0, CostMeterModel.moneyLevel(entry(money = "")))
    }

    @Test
    fun timeLevelMapsThreeTiers() {
        assertEquals(2, CostMeterModel.timeLevel(entry(time = "多")))
        assertEquals(1, CostMeterModel.timeLevel(entry(time = "中")))
        assertEquals(0, CostMeterModel.timeLevel(entry(time = "少")))
        assertEquals(0, CostMeterModel.timeLevel(entry(time = "")))
    }

    @Test
    fun willLevelMapsThreeTiers() {
        assertEquals(2, CostMeterModel.willLevel(entry(will = "是")))
        assertEquals(1, CostMeterModel.willLevel(entry(will = "些")))
        assertEquals(0, CostMeterModel.willLevel(entry(will = "否")))
        assertEquals(0, CostMeterModel.willLevel(entry(will = "")))
    }

    @Test
    fun costScoreSumsThreeItems() {
        assertEquals(6, CostMeterModel.costScore(entry(money = "多", time = "多", will = "是")))
        assertEquals(0, CostMeterModel.costScore(entry(money = "0", time = "少", will = "否")))
        assertEquals(
            2 + 1 + 1,
            CostMeterModel.costScore(entry(money = "多", time = "中", will = "些")),
        )
    }

    @Test
    fun gainLevelMapsLevelField() {
        assertEquals(3, CostMeterModel.gainLevel(entry(level = "大")))
        assertEquals(2, CostMeterModel.gainLevel(entry(level = "中")))
        assertEquals(1, CostMeterModel.gainLevel(entry(level = "小")))
        assertEquals(0, CostMeterModel.gainLevel(entry(level = "")))
    }
}
