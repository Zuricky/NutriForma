package com.nutriforma.app.domain.swap

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.assertMealStructure
import com.nutriforma.app.domain.component
import com.nutriforma.app.domain.generate
import com.nutriforma.app.domain.meal
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.nutrition.NutritionCalculator
import com.nutriforma.app.domain.rules.DietRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealSwapUseCaseTest {

    private val useCase = MealSwapUseCase()
    private val engine = DietRuleEngine()
    private val foods = TestFoods.all
    private val byId = TestFoods.byId
    private val profile = UserProfile()
    private val week = generate().meals

    private fun alternatives(meal: PlannedMeal, week: List<PlannedMeal> = this.week, p: UserProfile = profile, max: Int = 500) =
        useCase.alternativesFor(meal, week, foods, p, maxAlternatives = max)

    private fun MealAlternative.has(foodId: String) = components.any { it.foodId == foodId }

    // ---- Weekly caps are flagged with a clear Italian message ---------------------------------------------------

    @Test
    fun potatoesAlreadyUsedThisWeekAreFlagged() {
        // Default week: patate a pranzo Mercoledì. Il pranzo di Lunedì non ne ha.
        val monday = week.meal(0, MealSlot.PRANZO)
        assertNull(monday.component(MealRole.PRANZO_ACCOMPAGNAMENTO)?.takeIf { it.foodId == "patate" })
        val potato = alternatives(monday).first { it.has("patate") && it.changedRoles == listOf(MealRole.PRANZO_ACCOMPAGNAMENTO) }
        assertTrue(potato.exceedsWeeklyCap)
        assertFalse(potato.allowed)
        assertEquals(listOf("Supereresti il limite settimanale: Patate 2/1"), potato.blockingReasonsIt)
        assertEquals("Patate già usate questa settimana (max 1).", potato.warningMessageIt)
    }

    @Test
    fun swappingTheMealThatHoldsThePotatoStaysAllowed() {
        val wednesday = week.meal(2, MealSlot.PRANZO)
        assertEquals("patate", wednesday.component(MealRole.PRANZO_ACCOMPAGNAMENTO)!!.foodId)
        val keep = alternatives(wednesday).filter { it.changedRoles == listOf(MealRole.PRANZO_SECONDO) }
        assertTrue(keep.isNotEmpty())
        assertTrue("cambiare il secondo non tocca le patate", keep.filter { it.has("patate") }.all { it.allowed || it.blockingReasonsIt.none { r -> r.contains("Patate") } })
        // Sostituire proprio le patate le rimuove: sempre permesso.
        val noPotato = alternatives(wednesday).filter { it.changedRoles == listOf(MealRole.PRANZO_ACCOMPAGNAMENTO) }
        assertTrue(noPotato.all { it.allowed })
    }

    @Test
    fun anExtraEggMealIsBlockedByOccasionsAndTotal() {
        // La settimana ha già 3 occasioni e 5 uova: pollo → uova a pranzo (Lunedì) = 4 occasioni, 7 uova.
        val monday = week.meal(0, MealSlot.PRANZO)
        val eggs = alternatives(monday).first { it.has("uovo") && it.changedRoles == listOf(MealRole.PRANZO_SECONDO) }
        assertTrue(eggs.exceedsWeeklyCap)
        assertTrue(eggs.blockingReasonsIt.contains("Supereresti il limite settimanale: Uova (volte) 4/3"))
        assertTrue(eggs.blockingReasonsIt.contains("Supereresti il limite settimanale: Uova (totale) 7/6"))
        assertEquals("Hai già raggiunto il massimo di 3 pasti con uova questa settimana.", eggs.warningMessageIt)
    }

    @Test
    fun oneEggVersusTwoEggsChangesTheTotalDelta() {
        // Sostituire l'uovo di colazione (1 uovo) con yogurt abbassa il totale a 4 (< 5): consentito ma con avviso soft.
        val friday = week.meal(4, MealSlot.COLAZIONE)
        assertEquals("uovo", friday.component(MealRole.COLAZIONE_G1)!!.foodId)
        val yogurt = alternatives(friday).first { it.has("yogurt_greco_2") && it.changedRoles == listOf(MealRole.COLAZIONE_G1) }
        assertTrue(yogurt.allowed)
        assertTrue(yogurt.softWarningsIt.any { it.startsWith("Uova (totale)") })
        // Le occasioni scendono da 3 a 2: ancora nel range 2-3, nessun avviso.
        assertTrue(yogurt.softWarningsIt.none { it.startsWith("Uova (volte)") })
    }

    @Test
    fun redMeatBeyondTheLimitIsBlocked() {
        val monday = week.meal(0, MealSlot.PRANZO) // petto di pollo/tacchino/coniglio; la carne rossa è già a mercoledì? (indice 2 del template)
        val red = alternatives(monday).filter { it.changedRoles == listOf(MealRole.PRANZO_SECONDO) && byId.getValue(it.components.first { c -> c.role == MealRole.PRANZO_SECONDO }.foodId).category == FoodCategory.CARNE_ROSSA }
        assertTrue(red.isNotEmpty())
        assertTrue(red.all { it.exceedsWeeklyCap })
        assertTrue(red.all { it.blockingReasonsIt.any { r -> r.startsWith("Supereresti il limite settimanale: Carne rossa 2/1") } })
    }

    // ---- Validity ---------------------------------------------------------------------------------------------------

    @Test
    fun allowedAlternativesNeverCreateAViolation() {
        var checked = 0
        for (meal in week) {
            for (alt in alternatives(meal, max = 60)) {
                val newMeal = meal.copy(type = alt.type, components = alt.components)
                assertMealStructure(newMeal)
                val newWeek = week.map { if (it.dayIndex == meal.dayIndex && it.slot == meal.slot) newMeal else it }
                val violated = engine.evaluate(newWeek, foods, profile).hasViolations
                if (alt.allowed) assertFalse("${meal.type} g${meal.dayIndex}: ${alt.descriptionIt}", violated)
                else assertTrue(alt.blockingReasonsIt.isNotEmpty() && alt.exceedsWeeklyCap)
                checked++
            }
        }
        assertTrue("controllate $checked alternative", checked > 500)
    }

    @Test
    fun dislikedFoodsAreNeverOffered() {
        val p = profile.copy(dislikedFoodIds = setOf("pane_comune", "petto_tacchino", "orata", "mela"))
        val w = generate(profile = p).meals
        for (meal in w) {
            alternatives(meal, w, p).forEach { alt ->
                assertTrue(alt.components.none { it.foodId in p.dislikedFoodIds })
            }
        }
    }

    @Test
    fun currentFoodIsNotOfferedBackForTheSameRole() {
        val meal = week.meal(0, MealSlot.PRANZO)
        val currentSecondo = meal.component(MealRole.PRANZO_SECONDO)!!.foodId
        alternatives(meal).filter { it.changedRoles == listOf(MealRole.PRANZO_SECONDO) }.forEach {
            assertFalse(it.components.first { c -> c.role == MealRole.PRANZO_SECONDO }.foodId == currentSecondo)
        }
        assertTrue(alternatives(meal).none { it.components == meal.components })
    }

    @Test
    fun wholeMealCombinationsAreOffered() {
        val meal = week.meal(1, MealSlot.PRANZO)
        val combos = alternatives(meal).filter { it.changedRoles.size > 1 }
        assertTrue("almeno una combinazione con più ruoli diversi", combos.isNotEmpty())
        assertTrue(combos.all { it.allowed })
    }

    @Test
    fun resultsAreSortedAllowedFirstAndLimited() {
        val meal = week.meal(0, MealSlot.PRANZO)
        val all = alternatives(meal)
        val firstBlocked = all.indexOfFirst { !it.allowed }
        assertTrue(firstBlocked > 0)
        assertTrue(all.drop(firstBlocked).none { it.allowed })
        assertEquals(5, alternatives(meal, max = 5).size)
    }

    @Test
    fun kcalDeltaIsNewMinusOld() {
        val calc = NutritionCalculator()
        val meal = week.meal(0, MealSlot.PRANZO)
        alternatives(meal, max = 30).forEach { alt ->
            val delta = calc.meal(meal.copy(components = alt.components), byId).kcal - calc.meal(meal, byId).kcal
            assertEquals(Math.round(delta).toInt(), alt.kcalDelta)
        }
    }

    @Test
    fun categoryChangeChangesThePortion() {
        val meal = week.meal(0, MealSlot.PRANZO)
        val egg = alternatives(meal).first { it.has("uovo") && it.changedRoles == listOf(MealRole.PRANZO_SECONDO) }
        val c = egg.components.first { it.role == MealRole.PRANZO_SECONDO }
        assertEquals(2.0, c.quantity, 0.0)
        assertEquals(110.0, c.grams, 0.0)
    }

    // ---- Special meals ----------------------------------------------------------------------------------------------

    @Test
    fun pastoLiberoAndSkippedSnacksHaveNoAlternatives() {
        assertTrue(alternatives(week.meal(5, MealSlot.PRANZO)).isEmpty())
        val turno = generate(turnoLungo = listOf(TurnoLungoDay(1, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_POMERIGGIO))).meals
        assertEquals(MealType.SPUNTINO_SALTATO, turno.meal(1, MealSlot.META_MATTINA).type)
        assertTrue(alternatives(turno.meal(1, MealSlot.META_MATTINA), turno).isEmpty())
    }

    @Test
    fun turnoLungoSnackHasFruitAndNutAlternatives() {
        val turno = generate(turnoLungo = listOf(TurnoLungoDay(1, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_POMERIGGIO))).meals
        val snack = turno.meal(1, MealSlot.META_POMERIGGIO)
        val alts = alternatives(snack, turno)
        assertTrue(alts.any { it.changedRoles == listOf(MealRole.SPUNTINO_FRUTTA) })
        assertTrue(alts.any { it.changedRoles == listOf(MealRole.SPUNTINO_SECCA) })
        assertNotNull(alts.first().descriptionIt)
    }

    @Test
    fun descriptionsAreItalianFoodNamesWithoutOil() {
        val alt = alternatives(week.meal(0, MealSlot.PRANZO)).first()
        assertFalse(alt.descriptionIt.contains("Olio"))
        assertTrue(alt.descriptionIt.contains(" + "))
    }
}
