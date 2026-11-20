package com.nutriforma.app.ui.profilo

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.model.SportLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfiloUiStateTest {

    @Test
    fun portionScaleTextMatchesSportLevel() {
        assertEquals("Porzioni standard", portionScaleText(SportLevel.SEDENTARIO))
        assertEquals("Porzioni +40%", portionScaleText(SportLevel.MOLTO_INTENSO))
    }

    @Test
    fun kcalUsesItalianThousandsSeparator() {
        assertEquals("2.650 kcal", formatKcal(2650))
    }

    @Test
    fun bmiIsFormattedWithAnItalianDecimalComma() {
        assertEquals("22,4", formatBmi(22.4))
        assertEquals("30,0", formatBmi(30.0))
    }

    @Test
    fun weightParsingAcceptsCommaAndDotAndChecksTheRange() {
        assertEquals(WeightResult(78.5, null), parseWeight("78,5"))
        assertEquals(WeightResult(78.5, null), parseWeight(" 78.5 "))
        assertEquals(WeightResult(null, null), parseWeight(""))
        assertEquals(WeightResult(30.0, null), parseWeight("30"))
        assertEquals(WeightResult(250.0, null), parseWeight("250"))
        listOf("29,9", "250,1", "abc", "7,8,5", "NaN").forEach {
            val result = parseWeight(it)
            assertNull(it, result.kg)
            assertEquals(it, "Peso non valido (tra 30 e 250 kg).", result.error)
        }
    }

    @Test
    fun weightInputIsShownWithoutAUselessDecimal() {
        assertEquals("78", formatWeightInput(78.0))
        assertEquals("78,5", formatWeightInput(78.5))
        assertEquals("78,6", formatWeightInput(78.55))
        assertEquals(78.5, roundWeight(78.54), 0.0)
    }

    @Test
    fun weightTypingKeepsDigitsAndOneSeparatorUpToSixCharacters() {
        assertEquals("78,5", sanitizeWeightInput("78,5"))
        assertEquals("78.5", sanitizeWeightInput("7x8.5"))
        assertEquals("78,5", sanitizeWeightInput("78,5,"))
        assertEquals("123456", sanitizeWeightInput("1234567890"))
        assertEquals("", sanitizeWeightInput("kg"))
    }

    @Test
    fun fullNameSkipsMissingParts() {
        assertEquals("Mario Rossi", fullName(" Mario ", "Rossi "))
        assertEquals("Mario", fullName("Mario", ""))
        assertEquals("Rossi", fullName("  ", "Rossi"))
        assertEquals("", fullName("", ""))
    }

    @Test
    fun exclusionSearchIgnoresCaseAndAccentsAndGroupsByCategory() {
        val all = buildExclusionGroups(TestFoods.all, emptySet(), "")
        assertTrue(all.size > 10)
        assertTrue("il pasto libero non è escludibile", all.flatMap { it.rows }.none { it.id == "pasto_libero" })

        val found = buildExclusionGroups(TestFoods.all, setOf("caffe_espresso"), "CAFFÈ")
        val rows = found.flatMap { it.rows }
        assertTrue(rows.any { it.id == "caffe_espresso" && it.excluded })
        assertTrue(rows.none { it.id == "mela" })

        assertTrue(buildExclusionGroups(TestFoods.all, emptySet(), "zzzz").isEmpty())
        // la ricerca per categoria funziona
        assertTrue(buildExclusionGroups(TestFoods.all, emptySet(), "pesce").flatMap { it.rows }.any { it.id == "orata" })
    }
}
