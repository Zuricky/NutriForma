package com.nutriforma.app.domain.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyTipsTest {

    @Test
    fun thereAre21UniqueNonBlankTips() {
        assertEquals(21, DailyTips.TIPS.size)
        assertEquals(21, DailyTips.TIPS.toSet().size)
        assertTrue(DailyTips.TIPS.all { it.isNotBlank() && it.endsWith(".") })
        assertTrue(DailyTips.TIPS[2].contains("1,7 litri"))
    }

    @Test
    fun tipRotatesByEpochDay() {
        assertEquals(DailyTips.TIPS[0], DailyTips.tipFor(LocalDate.ofEpochDay(0)))
        assertEquals(DailyTips.TIPS[1], DailyTips.tipFor(LocalDate.ofEpochDay(1)))
        assertEquals(DailyTips.TIPS[0], DailyTips.tipFor(LocalDate.ofEpochDay(21)))
        val d = LocalDate.of(2026, 11, 17)
        assertEquals(DailyTips.TIPS[(d.toEpochDay() % 21).toInt()], DailyTips.tipFor(d))
    }

    @Test
    fun datesBeforeTheEpochStillMapToATip() {
        assertEquals(DailyTips.TIPS[20], DailyTips.tipFor(LocalDate.ofEpochDay(-1)))
    }
}
