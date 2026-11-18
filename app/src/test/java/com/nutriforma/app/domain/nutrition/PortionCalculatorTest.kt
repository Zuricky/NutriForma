package com.nutriforma.app.domain.nutrition

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.SportLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PortionCalculatorTest {

    private val calc = PortionCalculator()
    private fun food(id: String) = TestFoods.byId.getValue(id)

    @Test
    fun specExamples() {
        // EQUILIBRIO pasta 70-80 → 75; × 1.40 = 105.
        assertEquals(105.0, calc.portionFor(MealRole.CENA_PRIMO_BASE, food("pasta_integrale"), Goal.EQUILIBRIO, SportLevel.MOLTO_INTENSO).quantity, 0.0)
        // ENERGIA carne 120 × 1.40 = 168 → 170.
        assertEquals(170.0, calc.portionFor(MealRole.PRANZO_SECONDO, food("petto_pollo"), Goal.ENERGIA, SportLevel.MOLTO_INTENSO).quantity, 0.0)
        // DIMAGRIMENTO pane 40 × 1.00 = 40.
        assertEquals(40.0, calc.portionFor(MealRole.COLAZIONE_G2, food("pane_integrale"), Goal.DIMAGRIMENTO, SportLevel.SEDENTARIO).quantity, 0.0)
    }

    @Test
    fun paneColazioneForEveryGoal() {
        val pane = food("pane_integrale")
        assertEquals(40.0, calc.portionFor(MealRole.COLAZIONE_G2, pane, Goal.DIMAGRIMENTO, SportLevel.SEDENTARIO).quantity, 0.0)
        assertEquals(45.0, calc.portionFor(MealRole.COLAZIONE_G2, pane, Goal.EQUILIBRIO, SportLevel.SEDENTARIO).quantity, 0.0)
        assertEquals(50.0, calc.portionFor(MealRole.COLAZIONE_G2, pane, Goal.ENERGIA, SportLevel.SEDENTARIO).quantity, 0.0)
        // Sporty scaling: 45 × 1.4 = 63 → 65.
        assertEquals(65.0, calc.portionFor(MealRole.COLAZIONE_G2, pane, Goal.EQUILIBRIO, SportLevel.MOLTO_INTENSO).quantity, 0.0)
    }

    @Test
    fun fetteBiscottateAreThreeThreeFourAndNeverScaled() {
        val fette = food("fette_biscottate_integrali")
        for (sport in SportLevel.entries) {
            assertEquals(3.0, calc.portionFor(MealRole.COLAZIONE_G2, fette, Goal.DIMAGRIMENTO, sport).quantity, 0.0)
            assertEquals(3.0, calc.portionFor(MealRole.COLAZIONE_G2, fette, Goal.EQUILIBRIO, sport).quantity, 0.0)
            assertEquals(4.0, calc.portionFor(MealRole.COLAZIONE_G2, fette, Goal.ENERGIA, sport).quantity, 0.0)
        }
        val energia = calc.portionFor(MealRole.COLAZIONE_G2, fette, Goal.ENERGIA, SportLevel.MOLTO_INTENSO)
        assertEquals(PortionUnit.PEZZI, energia.unit)
        assertEquals(36.0, energia.grams, 0.0)
    }

    @Test
    fun eggsAreNeverScaled() {
        val uovo = food("uovo")
        for (goal in Goal.entries) for (sport in SportLevel.entries) {
            assertEquals(1.0, calc.portionFor(MealRole.COLAZIONE_G1, uovo, goal, sport).quantity, 0.0)
            assertEquals(2.0, calc.portionFor(MealRole.PRANZO_SECONDO, uovo, goal, sport).quantity, 0.0)
            assertEquals(2.0, calc.portionFor(MealRole.CENA_SECONDO, uovo, goal, sport).quantity, 0.0)
            assertEquals(1.0, calc.portionFor(MealRole.CENA_PRIMO_CONDIMENTO_PROTEICO, uovo, goal, sport).quantity, 0.0)
            assertEquals(1.0, calc.portionFor(MealRole.CENA_MINESTRONE_AGGIUNTA, uovo, goal, sport).quantity, 0.0)
        }
        assertEquals(110.0, calc.portionFor(MealRole.PRANZO_SECONDO, uovo, Goal.EQUILIBRIO, SportLevel.MOLTO_INTENSO).grams, 0.0)
    }

    @Test
    fun oilIsFifteenGrams() {
        val olio = food("olio_evo")
        for (role in listOf(MealRole.PRANZO_CONDIMENTO, MealRole.CENA_CONDIMENTO)) {
            val p = calc.portionFor(role, olio, Goal.EQUILIBRIO, SportLevel.MOLTO_INTENSO)
            assertEquals(3.0, p.quantity, 0.0)
            assertEquals(PortionUnit.CUCCHIAINI, p.unit)
            assertEquals(15.0, p.grams, 0.0)
        }
    }

    @Test
    fun pastoLiberoHasZeroGramsAndHint() {
        val p = calc.portionFor(MealRole.PRANZO_LIBERO, food("pasto_libero"), Goal.ENERGIA, SportLevel.MOLTO_INTENSO)
        assertEquals(PortionUnit.PORZIONE, p.unit)
        assertEquals(0.0, p.grams, 0.0)
        assertEquals("es. pasticcio o sushi", p.hintIt)
    }

    @Test
    fun nonScalableGramRowsStayFixed() {
        // Verdure 200 g, frutta 150 g, tonno 80-100 g: non scalabili.
        assertEquals(200.0, calc.portionFor(MealRole.PRANZO_ANTIPASTO, food("zucchine"), Goal.ENERGIA, SportLevel.MOLTO_INTENSO).quantity, 0.0)
        assertEquals(150.0, calc.portionFor(MealRole.SPUNTINO_FRUTTA, food("mela"), Goal.ENERGIA, SportLevel.MOLTO_INTENSO).quantity, 0.0)
        assertEquals(90.0, calc.portionFor(MealRole.PRANZO_SECONDO, food("tonno_sottolio"), Goal.EQUILIBRIO, SportLevel.MOLTO_INTENSO).quantity, 0.0)
    }

    @Test
    fun roundTo5IsHalfUp() {
        assertEquals(105.0, PortionCalculator.roundTo5(105.0), 0.0)
        assertEquals(110.0, PortionCalculator.roundTo5(107.5), 0.0)
        assertEquals(105.0, PortionCalculator.roundTo5(107.4), 0.0)
        assertEquals(170.0, PortionCalculator.roundTo5(168.0), 0.0)
    }

    @Test
    fun foodWithoutRowForRoleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            calc.portionFor(MealRole.PRANZO_SECONDO, food("mela"), Goal.EQUILIBRIO, SportLevel.SEDENTARIO)
        }
    }

    @Test
    fun hintComesFromTheRow() {
        assertEquals("preferibilmente con tuorlo morbido", calc.portionFor(MealRole.COLAZIONE_G1, food("uovo"), Goal.EQUILIBRIO, SportLevel.SEDENTARIO).hintIt)
        assertEquals("1 vasetto", calc.portionFor(MealRole.COLAZIONE_G1, food("yogurt_greco_2"), Goal.EQUILIBRIO, SportLevel.SEDENTARIO).hintIt)
        assertNull(calc.portionFor(MealRole.COLAZIONE_G1, food("prosciutto_crudo"), Goal.EQUILIBRIO, SportLevel.SEDENTARIO).hintIt.takeIf { it != "circa 2 fette" })
    }
}
