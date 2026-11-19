package com.nutriforma.app.domain.nutrition

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Portions, formatting and macros of the release 1.1 foods. */
class NewFoodsNutritionTest {

    private val calc = PortionCalculator()
    private val nutrition = NutritionCalculator()
    private val byId = TestFoods.byId

    private fun portion(role: MealRole, id: String, goal: Goal = Goal.EQUILIBRIO, sport: SportLevel = SportLevel.SEDENTARIO) =
        calc.portionFor(role, byId.getValue(id), goal, sport)

    private fun component(role: MealRole, id: String, profile: UserProfile = UserProfile()): PlannedComponent {
        val p = calc.portionFor(role, byId.getValue(id), profile)
        return PlannedComponent(role, id, p.quantity, p.unit, p.grams)
    }

    private fun meal(type: MealType, slot: MealSlot, vararg parts: PlannedComponent) = PlannedMeal(0, 0, 0, slot, type, parts.toList(), false)

    @Test
    fun biscuitsShowTwoPiecesAndTwentySixGrams() {
        val p = portion(MealRole.COLAZIONE_DOLCE, "biscotti")
        assertEquals("2 pz", PortionFormatter.format(p.quantity, p.unit, "biscotti"))
        val totals = nutrition.component(component(MealRole.COLAZIONE_DOLCE, "biscotti"), byId)
        assertEquals(26 * 4.3, totals.kcal, 0.01)
        assertEquals(26 * 0.07, totals.proteinG, 0.01)
        assertEquals(26 * 0.76, totals.carbsG, 0.01)
    }

    @Test
    fun peanutButterIs15To20GramsByGoalInEveryRole() {
        listOf(MealRole.COLAZIONE_DOLCE, MealRole.SPUNTINO_SECCA, MealRole.SPUNTINO_ABBINAMENTO).forEach { role ->
            assertEquals(15.0, portion(role, "burro_arachidi", Goal.DIMAGRIMENTO).quantity, 0.0)
            assertEquals(20.0, portion(role, "burro_arachidi", Goal.EQUILIBRIO).quantity, 0.0)
            assertEquals(20.0, portion(role, "burro_arachidi", Goal.ENERGIA).quantity, 0.0)
            // Non scala con lo sport.
            assertEquals(
                portion(role, "burro_arachidi", Goal.EQUILIBRIO, SportLevel.SEDENTARIO).quantity,
                portion(role, "burro_arachidi", Goal.EQUILIBRIO, SportLevel.MOLTO_INTENSO).quantity, 0.0,
            )
        }
        val totals = nutrition.component(component(MealRole.SPUNTINO_SECCA, "burro_arachidi"), byId)
        assertEquals(0.20 * 600.0, totals.kcal, 0.01)
    }

    @Test
    fun orangeJuiceDisplaysMillilitresInItalian() {
        listOf(MealRole.COLAZIONE_BEVANDA, MealRole.COLAZIONE_DOLCE_BEVANDA, MealRole.SPUNTINO_FRUTTA).forEach { role ->
            val small = portion(role, "spremuta_arancia", Goal.DIMAGRIMENTO)
            val big = portion(role, "spremuta_arancia", Goal.ENERGIA)
            assertEquals(PortionUnit.MILLILITRI, small.unit)
            assertEquals("200 ml", PortionFormatter.format(small.quantity, small.unit))
            assertEquals("250 ml", PortionFormatter.format(big.quantity, big.unit))
            assertEquals(200.0, small.grams, 0.0)
        }
        val juice = component(MealRole.SPUNTINO_FRUTTA, "spremuta_arancia")
        assertEquals(0.45 * 200, nutrition.component(juice, byId).kcal, 0.01)
    }

    @Test
    fun polpoFollowsTheFishPortionsAndScalesWithSport() {
        assertEquals(150.0, portion(MealRole.PRANZO_SECONDO, "polpo", Goal.DIMAGRIMENTO).quantity, 0.0)
        assertEquals(180.0, portion(MealRole.CENA_SECONDO, "polpo", Goal.ENERGIA).quantity, 0.0)
        // Scalabile: x1,4 per lo sportivo molto intenso (165 -> 230).
        assertEquals(230.0, portion(MealRole.PRANZO_SECONDO, "polpo", Goal.EQUILIBRIO, SportLevel.MOLTO_INTENSO).quantity, 0.0)
        val totals = nutrition.component(component(MealRole.PRANZO_SECONDO, "polpo", UserProfile(sportLevel = SportLevel.SEDENTARIO)), byId)
        assertEquals(1.65 * 57.0, totals.kcal, 0.01)
        assertEquals(1.65 * 10.6, totals.proteinG, 0.01)
    }

    @Test
    fun completeDishesAreOnePortionWithTheirOwnMacrosAndNoQuantityText() {
        val expectedGrams = mapOf("pasticcio" to 330.0, "carbonara" to 320.0, "gnocchi_ragu" to 350.0, "tortellini_brodo" to 400.0)
        expectedGrams.forEach { (id, grams) ->
            Goal.entries.forEach { goal ->
                SportLevel.entries.forEach { sport ->
                    val p = portion(MealRole.CENA_PIATTO_UNICO, id, goal, sport)
                    assertEquals(PortionUnit.PORZIONE, p.unit)
                    assertEquals(1.0, p.quantity, 0.0)
                    assertEquals("$id: grammi di una porzione", grams, p.grams, 0.0)
                    assertEquals("", PortionFormatter.format(p.quantity, p.unit, id))
                }
            }
            val food = byId.getValue(id)
            val totals = nutrition.component(component(MealRole.CENA_PIATTO_UNICO, id), byId)
            assertEquals(food.kcalPer100g * grams / 100.0, totals.kcal, 0.01)
            assertTrue("$id: macro positivi", totals.proteinG > 0 && totals.carbsG > 0 && totals.fatG > 0)
            assertTrue("$id: una porzione e una cena credibile", totals.kcal in 380.0..650.0)
        }
        val dinner = meal(
            MealType.CENA_PIATTO_UNICO, MealSlot.CENA,
            component(MealRole.CENA_VERDURE, "zucchine"), component(MealRole.CENA_PIATTO_UNICO, "pasticcio"),
        )
        assertEquals(330 * 1.60 + 200 * 0.17, nutrition.meal(dinner, byId).kcal, 0.01)
    }

    @Test
    fun pastoLiberoStillCountsZeroCalories() {
        val libero = meal(MealType.PRANZO_LIBERO, MealSlot.PRANZO, component(MealRole.PRANZO_LIBERO, "pasto_libero"))
        assertEquals(0.0, nutrition.meal(libero, byId).kcal, 0.0)
        assertEquals(0.0, libero.components.single().grams, 0.0)
    }
}
