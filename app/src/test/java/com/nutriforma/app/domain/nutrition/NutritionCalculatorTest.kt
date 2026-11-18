package com.nutriforma.app.domain.nutrition

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.blankWeek
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionCalculatorTest {

    private val calc = NutritionCalculator()
    private val byId = TestFoods.byId

    // 1 uovo (55 g) + 50 g di pane integrale.
    private val breakfast = PlannedMeal(
        0, 0, 0, MealSlot.COLAZIONE, MealType.COLAZIONE_STANDARD,
        listOf(
            PlannedComponent(MealRole.COLAZIONE_G1, "uovo", 1.0, PortionUnit.PEZZI, 55.0),
            PlannedComponent(MealRole.COLAZIONE_G2, "pane_integrale", 50.0, PortionUnit.GRAMMI, 50.0),
        ),
        false,
    )

    @Test
    fun mealTotalsMatchHandComputedValues() {
        val t = calc.meal(breakfast, byId)
        assertEquals(128 * 0.55 + 224 * 0.5, t.kcal, 0.01) // 182.4
        assertEquals(12.4 * 0.55 + 7.5 * 0.5, t.proteinG, 0.01) // 10.57
        assertEquals(0.0 + 41.8 * 0.5, t.carbsG, 0.01) // 20.9
        assertEquals(8.7 * 0.55 + 1.3 * 0.5, t.fatG, 0.01) // 5.435
        assertEquals(0.0 + 6.5 * 0.5, t.fiberG, 0.01) // 3.25
    }

    @Test
    fun unknownFoodContributesNothing() {
        val m = breakfast.copy(components = listOf(PlannedComponent(MealRole.COLAZIONE_G1, "non_esiste", 1.0, PortionUnit.PEZZI, 100.0)))
        assertEquals(0.0, calc.meal(m, byId).kcal, 0.0)
    }

    @Test
    fun dayIsTheSumOfItsMealsAndFlagsPastoLibero() {
        val week = blankWeek(cheat = 1)
            .with(5, MealSlot.COLAZIONE, MealRole.COLAZIONE_G1 to "uovo", MealRole.COLAZIONE_G2 to "pane_integrale")
            .with(5, MealSlot.PRANZO, MealRole.PRANZO_LIBERO to "pasto_libero")
        val saturday = calc.day(week, 5, byId)
        assertTrue(saturday.includesPastoLibero)
        val colazione = calc.meal(week.first { it.dayIndex == 5 && it.slot == MealSlot.COLAZIONE }, byId)
        assertEquals(colazione.kcal, saturday.totals.kcal, 1e-9) // il pasto libero vale 0
        assertFalse(calc.day(week, 4, byId).includesPastoLibero)
    }

    @Test
    fun weekAverageIsTheSumOverSeven() {
        val week = blankWeek()
            .with(0, MealSlot.COLAZIONE, MealRole.COLAZIONE_G1 to "uovo")
            .with(3, MealSlot.COLAZIONE, MealRole.COLAZIONE_G1 to "uovo")
        val sum = calc.week(week, byId).sumOf { it.totals.kcal }
        assertEquals(sum / 7, calc.weekAverage(week, byId).kcal, 1e-9)
        assertEquals(7, calc.week(week, byId).size)
    }

    @Test
    fun noRoundingOfIntermediateSums() {
        val t = calc.meal(breakfast, byId)
        assertTrue(t.kcal != Math.round(t.kcal).toDouble())
    }
}
