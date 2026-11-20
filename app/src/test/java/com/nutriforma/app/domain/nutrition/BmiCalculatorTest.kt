package com.nutriforma.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BmiCalculatorTest {

    @Test
    fun bmiIsWeightOverHeightInMetersSquared() {
        // 78 / 1,80² = 24,07
        assertEquals(24.1, BmiCalculator.calculate(78.0, 180)!!.value, 0.0)
        // 60 / 1,60² = 23,44
        assertEquals(23.4, BmiCalculator.calculate(60.0, 160)!!.value, 0.0)
        // 100 / 2,00² = 25
        assertEquals(25.0, BmiCalculator.calculate(100.0, 200)!!.value, 0.0)
    }

    @Test
    fun valueIsRoundedToOneDecimalLikeWhatTheUserReads() {
        // 73,5 / 1,75² = 24,0000 → 24,0 ; 56,7 / 1,75² = 18,5143 → 18,5
        assertEquals(24.0, BmiCalculator.calculate(73.5, 175)!!.value, 0.0)
        assertEquals(18.5, BmiCalculator.calculate(56.7, 175)!!.value, 0.0)
    }

    @Test
    fun categoryBoundariesFollowTheStandardBands() {
        fun at(bmi: Double) = BmiCalculator.classify(bmi)
        assertEquals(BmiCategory.SOTTOPESO, at(10.0))
        assertEquals(BmiCategory.SOTTOPESO, at(18.4))
        assertEquals("18,5 è già normopeso", BmiCategory.NORMOPESO, at(18.5))
        assertEquals(BmiCategory.NORMOPESO, at(24.9))
        assertEquals("25,0 è già sovrappeso", BmiCategory.SOVRAPPESO, at(25.0))
        assertEquals(BmiCategory.SOVRAPPESO, at(29.9))
        assertEquals("30,0 è già obesità", BmiCategory.OBESITA, at(30.0))
        assertEquals(BmiCategory.OBESITA, at(45.0))
    }

    @Test
    fun categoryIsDecidedOnTheValueTheUserSees() {
        // 24,98 si legge "25,0": la fascia deve essere quella del numero mostrato, non del valore grezzo.
        val result = BmiCalculator.calculate(76.5, 175)!!
        assertEquals(25.0, result.value, 0.0)
        assertEquals(BmiCategory.SOVRAPPESO, result.category)

        // 18,449 → 18,4 (sottopeso), 18,482 → 18,5 (normopeso)
        assertEquals(BmiCategory.SOTTOPESO, BmiCalculator.calculate(56.5, 175)!!.category)
        assertEquals(BmiCategory.NORMOPESO, BmiCalculator.calculate(56.6, 175)!!.category)
    }

    @Test
    fun calculateReturnsTheMatchingCategory() {
        assertEquals(BmiCategory.SOTTOPESO, BmiCalculator.calculate(50.0, 180)!!.category)
        assertEquals(BmiCategory.NORMOPESO, BmiCalculator.calculate(70.0, 175)!!.category)
        assertEquals(BmiCategory.SOVRAPPESO, BmiCalculator.calculate(85.0, 175)!!.category)
        assertEquals(BmiCategory.OBESITA, BmiCalculator.calculate(110.0, 175)!!.category)
        assertNotNull(BmiCalculator.calculate(30.0, 100))
        assertNotNull(BmiCalculator.calculate(250.0, 230))
    }

    @Test
    fun missingOrNonsenseInputsGiveNoResult() {
        assertNull(BmiCalculator.calculate(null, 180))
        assertNull(BmiCalculator.calculate(78.0, null))
        assertNull(BmiCalculator.calculate(null, null))
        assertNull(BmiCalculator.calculate(0.0, 180))
        assertNull(BmiCalculator.calculate(-5.0, 180))
        assertNull(BmiCalculator.calculate(78.0, 0))
        assertNull(BmiCalculator.calculate(78.0, -170))
        assertNull(BmiCalculator.calculate(Double.NaN, 180))
        assertNull(BmiCalculator.calculate(Double.POSITIVE_INFINITY, 180))
    }

    @Test
    fun italianLabelsAreNatural() {
        assertEquals(
            listOf("Sottopeso", "Normopeso", "Sovrappeso", "Obesità"),
            BmiCategory.entries.map { it.labelIt },
        )
    }
}
