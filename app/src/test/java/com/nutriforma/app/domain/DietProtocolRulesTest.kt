package com.nutriforma.app.domain

import com.nutriforma.app.domain.generator.WeeklyPlanGenerator
import com.nutriforma.app.domain.model.ComplianceStatus
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.nutrition.PortionCalculator
import com.nutriforma.app.domain.rules.DailyFoodCaps
import com.nutriforma.app.domain.rules.DietRuleEngine
import com.nutriforma.app.domain.swap.MealSwapUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Release 1.1 diet protocol: meat only at lunch, pasta only at dinner (max 2 + 2, total 4), daily caps for
 * peanut butter and orange juice, in generation, swaps and compliance.
 */
class DietProtocolRulesTest {

    private val foods = TestFoods.all
    private val byId = TestFoods.byId
    private val engine = DietRuleEngine()
    private val swapUseCase = MealSwapUseCase()
    private val generator = WeeklyPlanGenerator()
    private val portions = PortionCalculator()

    private val dishIds = setOf("pasticcio", "carbonara", "gnocchi_ragu", "tortellini_brodo")
    private val meatCategories = setOf(FoodCategory.CARNE_BIANCA, FoodCategory.CARNE_ROSSA)
    private val pastaCategories = setOf(FoodCategory.PASTA, FoodCategory.PIATTO_UNICO)

    private fun FoodItem?.isMeat() = this != null && category in meatCategories
    private fun FoodItem?.isPasta() = this != null && category in pastaCategories

    /** 15 goal x sport combinations, [seedsPerCombo] seeds each (14 -> 210 weeks). */
    private fun forEachWeek(seedsPerCombo: Int = 14, block: (label: String, profile: UserProfile, meals: List<PlannedMeal>) -> Unit) {
        var seed = 1L
        for (goal in Goal.entries) for (sport in SportLevel.entries) {
            for (i in 0 until seedsPerCombo) {
                val cheat = 1 + (i % 2)
                val turno = if (i % 3 == 0) listOf(TurnoLungoDay(1 + i % 5, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_MATTINA)) else emptyList()
                val profile = UserProfile(goal = goal, sportLevel = sport, cheatMealsPerWeek = cheat)
                val meals = generate(seed = seed, profile = profile, turnoLungo = turno).meals
                block("seed=$seed $goal/$sport", profile, meals)
                seed++
            }
        }
    }

    // ---- Meat: lunch only ---------------------------------------------------------------------------------------

    @Test
    fun generatedWeeksNeverHaveStandaloneMeatAtDinner() {
        var weeks = 0
        var lunchesWithMeat = 0
        forEachWeek { label, _, meals ->
            weeks++
            meals.filter { it.slot == MealSlot.CENA }.forEach { dinner ->
                dinner.components.forEach { assertFalse("$label: carne a cena (${it.foodId})", byId[it.foodId].isMeat()) }
            }
            meals.flatMap { m -> m.components.map { m to it } }.filter { (_, c) -> byId[c.foodId].isMeat() }.forEach { (m, c) ->
                assertEquals("$label: la carne e solo un secondo di pranzo", MealRole.PRANZO_SECONDO, c.role)
                assertEquals(MealSlot.PRANZO, m.slot)
                lunchesWithMeat++
            }
        }
        assertTrue("almeno 200 settimane controllate ($weeks)", weeks >= 200)
        assertTrue("la carne a pranzo resta presente", lunchesWithMeat > weeks)
    }

    @Test
    fun curedMeatsAreUnchangedAtBreakfastAndMinestrone() {
        var breakfast = 0
        var minestrone = 0
        forEachWeek(seedsPerCombo = 4) { _, _, meals ->
            meals.flatMap { it.components }.forEach { c ->
                if (byId[c.foodId]?.category == FoodCategory.AFFETTATO) {
                    assertTrue(c.role == MealRole.COLAZIONE_G1 || c.role == MealRole.CENA_MINESTRONE_AGGIUNTA)
                    if (c.role == MealRole.COLAZIONE_G1) breakfast++ else minestrone++
                }
            }
        }
        assertTrue(breakfast > 0 && minestrone > 0)
    }

    @Test
    fun dinnerFallbacksNeverUseMeatEvenWithEggsAndFishExcluded() {
        val profile = UserProfile(dislikedFoodIds = setOf("uovo") + TestFoods.idsOf(FoodCategory.PESCE))
        for (seed in 1L..25L) {
            val meals = generate(seed = seed, profile = profile).meals
            meals.filter { it.slot == MealSlot.CENA }.flatMap { it.components }.forEach {
                assertFalse("seed=$seed: carne a cena", byId[it.foodId].isMeat())
            }
            assertFalse(engine.evaluate(meals, foods, profile).hasViolations)
        }
    }

    @Test
    fun swapsNeverOfferMeatAtDinnerButStillOfferItAtLunch() {
        val profile = UserProfile()
        val week = generate(profile = profile).meals
        var lunchMeatOffers = 0
        for (meal in week) {
            val alternatives = swapUseCase.alternativesFor(meal, week, foods, profile, maxAlternatives = 500)
            alternatives.forEach { alt ->
                val withMeat = alt.components.filter { byId[it.foodId].isMeat() }
                if (meal.slot == MealSlot.CENA) {
                    assertTrue("cena g${meal.dayIndex} ${alt.descriptionIt}: carne", withMeat.isEmpty())
                } else {
                    withMeat.forEach { assertEquals(MealRole.PRANZO_SECONDO, it.role) }
                    if (withMeat.isNotEmpty()) lunchMeatOffers++
                }
            }
        }
        assertTrue("il pranzo offre ancora la carne", lunchMeatOffers > 0)
    }

    @Test
    fun thereIsNoDinnerMeatPortionRowOrRole() {
        assertTrue(foods.filter { it.isMeat() }.none { MealRole.CENA_SECONDO in it.roles })
        val steak = byId.getValue("manzo_magro")
        assertEquals(null, com.nutriforma.app.domain.rules.PortionTable.find(MealRole.CENA_SECONDO, steak))
        assertNotNull(com.nutriforma.app.domain.rules.PortionTable.find(MealRole.PRANZO_SECONDO, steak))
    }

    // ---- Pasta: dinner only, 2 + 2 <= 4 per week ---------------------------------------------------------------

    @Test
    fun pastaAndCompleteDishesNeverAppearAtLunchOrOtherSlots() {
        forEachWeek { label, _, meals ->
            meals.forEach { m ->
                m.components.forEach { c ->
                    if (byId[c.foodId].isPasta()) assertEquals("$label: pasta fuori dalla cena", MealSlot.CENA, m.slot)
                }
            }
            meals.filter { it.slot == MealSlot.PRANZO }.flatMap { it.components }.forEach {
                assertFalse("$label: pasta a pranzo", byId[it.foodId].isPasta())
            }
        }
    }

    @Test
    fun swapsNeverOfferPastaAtLunch() {
        val profile = UserProfile()
        for (seed in 1L..6L) {
            val week = generate(seed = seed, profile = profile).meals
            week.filter { it.slot == MealSlot.PRANZO }.forEach { lunch ->
                swapUseCase.alternativesFor(lunch, week, foods, profile, maxAlternatives = 500).forEach { alt ->
                    assertEquals(MealType.PRANZO_STANDARD, alt.type)
                    alt.components.forEach { assertFalse("pasta a pranzo: ${it.foodId}", byId[it.foodId].isPasta()) }
                }
            }
        }
    }

    @Test
    fun weeklyPastaLimitsHoldAcross210Weeks() {
        var weeks = 0
        val dishSeen = mutableMapOf<String, Int>()
        forEachWeek { label, profile, meals ->
            weeks++
            val dinners = meals.filter { it.slot == MealSlot.CENA }
            val veg = dinners.count { it.type == MealType.CENA_PRIMO_VERDURE }
            val protein = dinners.count { it.type == MealType.CENA_PRIMO_PROTEICO || it.type == MealType.CENA_PIATTO_UNICO }
            val dishes = dinners.flatMap { it.components }.filter { it.foodId in dishIds }
            dishes.forEach { dishSeen.merge(it.foodId, 1, Int::plus) }

            assertTrue("$label: pasta con verdure $veg > 2", veg <= 2)
            assertTrue("$label: pasta con proteine/carne $protein > 2", protein <= 2)
            assertTrue("$label: pasta totale ${veg + protein} > 4", veg + protein <= 4)
            // Ogni cena con pasta di base o piatto completo e una di queste.
            val pastaDinners = dinners.count { d -> d.components.any { byId[it.foodId].isPasta() || it.role == MealRole.CENA_PRIMO_BASE } }
            assertEquals("$label: cene con pasta", veg + protein, pastaDinners)

            val counts = countsOf(meals)
            assertEquals(veg, counts.cenePrimoVerdure)
            assertEquals(protein, counts.cenePrimoProteico)
            assertEquals(veg + protein, counts.cenePrimo)
            assertFalse("$label: violazioni", engine.evaluate(meals, foods, profile).hasViolations)
        }
        assertTrue("controllate $weeks settimane", weeks >= 200)
        dishIds.forEach { assertTrue("$it mai generato in $weeks settimane (${dishSeen})", (dishSeen[it] ?: 0) > 0) }
    }

    @Test
    fun completeDishesAreSingleDishDinnersWithoutSecondoOrSeparateSeasoning() {
        var checked = 0
        forEachWeek(seedsPerCombo = 6) { label, _, meals ->
            meals.filter { it.type == MealType.CENA_PIATTO_UNICO }.forEach { dinner ->
                checked++
                assertMealStructure(dinner)
                assertEquals(MealSlot.CENA, dinner.slot)
                assertEquals(listOf(MealRole.CENA_VERDURE, MealRole.CENA_PIATTO_UNICO), dinner.components.map { it.role })
                val dish = dinner.components.last()
                assertTrue("$label: ${dish.foodId}", dish.foodId in dishIds)
                assertEquals(PortionUnit.PORZIONE, dish.unit)
                assertEquals(1.0, dish.quantity, 0.0)
                assertTrue(dish.grams > 0.0)
                assertTrue("$label: niente secondo", dinner.components.none { it.role == MealRole.CENA_SECONDO })
            }
        }
        assertTrue("nessun piatto unico generato", checked > 0)
    }

    @Test
    fun excludingEveryCompleteDishKeepsTheProteinPrimoAndTheLimits() {
        val profile = UserProfile(dislikedFoodIds = dishIds)
        for (seed in 1L..40L) {
            val meals = generate(seed = seed, profile = profile).meals
            assertTrue(meals.none { it.type == MealType.CENA_PIATTO_UNICO })
            val c = countsOf(meals)
            assertEquals(2, c.cenePrimoProteico)
            assertEquals(4, c.cenePrimo)
        }
    }

    @Test
    fun theProteinLimitCountsCompleteDishesAndSauces() {
        val dish = byId.getValue("pasticcio")
        fun dinnerWithDish(week: List<PlannedMeal>, day: Int): List<PlannedMeal> = week.map { m ->
            if (m.dayIndex != day || m.slot != MealSlot.CENA) {
                m
            } else {
                val p = portions.portionFor(MealRole.CENA_PIATTO_UNICO, dish, UserProfile())
                m.copy(
                    type = MealType.CENA_PIATTO_UNICO,
                    components = listOf(PlannedComponent(MealRole.CENA_PIATTO_UNICO, dish.id, p.quantity, p.unit, p.grams)),
                )
            }
        }
        // Settimana vuota: 2 primi proteici (giorni 2 e 5). Un terzo piatto completo supera il limite di 2.
        val base = blankWeek()
        assertEquals(2, countsOf(base).cenePrimoProteico)
        val two = dinnerWithDish(dinnerWithDish(base, 2), 5)
        assertEquals(2, countsOf(two).cenePrimoProteico)
        val three = dinnerWithDish(two, 0) // il lunedi era "con verdure": ora il totale cambia
        val c = countsOf(three)
        assertEquals(3, c.cenePrimoProteico)
        assertEquals(1, c.cenePrimoVerdure)

        val profile = UserProfile()
        assertTrue(RuleId.CENA_PRIMO_PROTEICO in engine.exceededCaps(c, profile))
        val check = engine.evaluate(three, foods, profile).checks.first { it.ruleId == RuleId.CENA_PRIMO_PROTEICO }
        assertEquals(ComplianceStatus.VIOLAZIONE, check.status)
        assertEquals("Pasta con proteine o carne: 3 (obiettivo 2) — limite superato", check.messageIt)
    }

    @Test
    fun pastaWithVegetablesBeyondTwoIsAViolationWithAnItalianMessage() {
        // Il minestrone del giovedi diventa un terzo primo con verdure: 3 + 2 = 5 cene con pasta.
        val week = blankWeek().withType(3, MealSlot.CENA, MealType.CENA_PRIMO_VERDURE)
        val profile = UserProfile()
        val c = countsOf(week)
        assertEquals(3, c.cenePrimoVerdure)
        assertEquals(5, c.cenePrimo)
        val checks = engine.evaluate(week, foods, profile).checks
        assertEquals(ComplianceStatus.VIOLAZIONE, checks.first { it.ruleId == RuleId.CENA_PRIMO_VERDURE }.status)
        assertEquals(
            "Pasta con verdure: 3 (obiettivo 2) — limite superato",
            checks.first { it.ruleId == RuleId.CENA_PRIMO_VERDURE }.messageIt,
        )
        assertEquals(ComplianceStatus.VIOLAZIONE, checks.first { it.ruleId == RuleId.CENA_PRIMO }.status)
        assertEquals("Pasta a cena (totale)", RuleId.CENA_PRIMO.labelIt)
    }

    @Test
    fun swapBetweenProteinPrimoAndCompleteDishKeepsTheWeeklyCount() {
        val profile = UserProfile()
        val week = generate(seed = 3, profile = profile).meals
        val primo = week.first { it.type == MealType.CENA_PRIMO_PROTEICO }
        val toDish = swapUseCase.alternativesFor(primo, week, foods, profile, maxAlternatives = 500)
            .filter { it.type == MealType.CENA_PIATTO_UNICO }
        assertEquals(dishIds, toDish.flatMap { alt -> alt.components.map { it.foodId } }.filter { it in dishIds }.toSet())
        toDish.forEach { alt ->
            assertTrue(alt.allowed)
            assertEquals(listOf(MealRole.CENA_VERDURE, MealRole.CENA_PIATTO_UNICO), alt.components.map { it.role })
            val swapped = week.map { if (it.dayIndex == primo.dayIndex && it.slot == primo.slot) it.copy(type = alt.type, components = alt.components) else it }
            assertMealStructure(swapped.first { it.dayIndex == primo.dayIndex && it.slot == primo.slot })
            assertEquals(2, countsOf(swapped).cenePrimoProteico)
            assertFalse(engine.evaluate(swapped, foods, profile).hasViolations)
        }

        // E viceversa: dal piatto completo si torna a un primo con condimento proteico.
        val dishMeal = week.first { it.dayIndex == primo.dayIndex && it.slot == MealSlot.CENA }
            .let { m -> m.copy(type = toDish.first().type, components = toDish.first().components) }
        val dishWeek = week.map { if (it.dayIndex == dishMeal.dayIndex && it.slot == dishMeal.slot) dishMeal else it }
        val back = swapUseCase.alternativesFor(dishMeal, dishWeek, foods, profile, maxAlternatives = 500)
        assertTrue(back.any { it.type == MealType.CENA_PRIMO_PROTEICO && it.allowed })
        // Tra i quattro piatti si puo cambiare senza uscire dal tipo.
        assertTrue(back.any { it.type == MealType.CENA_PIATTO_UNICO && it.components.last().foodId != dishMeal.components.last().foodId })
        back.filter { it.type == MealType.CENA_PRIMO_PROTEICO }.forEach {
            val swapped = dishWeek.map { m -> if (m === dishMeal) m.copy(type = it.type, components = it.components) else m }
            assertMealStructure(swapped.first { m -> m.dayIndex == dishMeal.dayIndex && m.slot == dishMeal.slot })
            assertEquals(2, countsOf(swapped).cenePrimoProteico)
        }
    }

    @Test
    fun dinnerSecondoAndMinestroneAlternativesNeverContainCompleteDishes() {
        val profile = UserProfile()
        val week = generate(seed = 7, profile = profile).meals
        week.filter { it.type == MealType.CENA_SECONDO || it.type == MealType.CENA_MINESTRONE || it.type == MealType.CENA_PRIMO_VERDURE }
            .forEach { meal ->
                swapUseCase.alternativesFor(meal, week, foods, profile, maxAlternatives = 500).forEach { alt ->
                    assertEquals(meal.type, alt.type)
                    assertTrue(alt.components.none { it.foodId in dishIds })
                }
            }
    }

    // ---- Daily caps: burro di arachidi / spremuta d'arancia ----------------------------------------------------------

    private fun dayCount(meals: List<PlannedMeal>, day: Int, foodId: String) = DailyFoodCaps.countOnDay(meals, day, foodId)

    @Test
    fun generatedWeeksNeverRepeatPeanutButterOrJuiceInADay() {
        var butter = 0
        forEachWeek { label, profile, meals ->
            for (day in 0..6) for (id in DailyFoodCaps.maxPerDay.keys) {
                val n = dayCount(meals, day, id)
                assertTrue("$label: $id x$n il giorno $day", n <= 1)
                if (id == "burro_arachidi") butter += n
            }
            val report = engine.evaluate(meals, foods, profile)
            assertEquals(ComplianceStatus.OK, report.checks.first { it.ruleId == RuleId.BURRO_ARACHIDI_GIORNO }.status)
            assertEquals(ComplianceStatus.OK, report.checks.first { it.ruleId == RuleId.SPREMUTA_GIORNO }.status)
        }
        assertTrue("il burro di arachidi viene davvero generato", butter > 0)
    }

    private fun withComponent(week: List<PlannedMeal>, day: Int, slot: MealSlot, role: MealRole, foodId: String): List<PlannedMeal> =
        week.map { m ->
            if (m.dayIndex != day || m.slot != slot) {
                m
            } else {
                val p = portions.portionFor(role, byId.getValue(foodId), UserProfile())
                val c = PlannedComponent(role, foodId, p.quantity, p.unit, p.grams)
                m.copy(components = m.components.filter { it.role != role } + c)
            }
        }

    @Test
    fun generatorRespectsTheDailyCapWhenRegeneratingAMeal() {
        val profile = UserProfile()
        val week = generate(seed = 11, profile = profile).meals
        // Burro gia a meta mattina del giorno 0; unica alternativa per l'abbinamento del pomeriggio: il burro.
        val onlyButter = foods.filter { f -> f.category !in setOf(FoodCategory.CIOCCOLATO, FoodCategory.FORMAGGIO_STAGIONATO, FoodCategory.AVOCADO) ||
            MealRole.SPUNTINO_ABBINAMENTO !in f.roles }
        val morning = withComponent(week, 0, MealSlot.META_MATTINA, MealRole.SPUNTINO_SECCA, "burro_arachidi")
        val blocked = generator.generateMeal(MealType.SPUNTINO_POMERIGGIO, 0, morning, profile, onlyButter, Random(1), MealSlot.META_POMERIGGIO)
        assertTrue("burro due volte nello stesso giorno", blocked.meal.components.none { it.foodId == "burro_arachidi" })
        assertTrue(blocked.warnings.isNotEmpty())
        // Controllo: lo stesso giorno senza burro a meta mattina lo usa (la restrizione era solo il limite giornaliero).
        val free = withComponent(week, 0, MealSlot.META_MATTINA, MealRole.SPUNTINO_SECCA, "mandorle")
        val allowed = generator.generateMeal(MealType.SPUNTINO_POMERIGGIO, 0, free, profile, onlyButter, Random(1), MealSlot.META_POMERIGGIO)
        assertEquals("burro_arachidi", allowed.meal.components.single { it.role == MealRole.SPUNTINO_ABBINAMENTO }.foodId)

        // Spremuta a colazione: lo spuntino con frutta non puo ripeterla (unica frutta disponibile = spremuta).
        val onlyJuice = foods.filter { MealRole.SPUNTINO_FRUTTA !in it.roles || it.id == "spremuta_arancia" }
        val breakfast = withComponent(week, 2, MealSlot.COLAZIONE, MealRole.COLAZIONE_DOLCE_BEVANDA, "spremuta_arancia")
        val noJuice = generator.generateMeal(MealType.SPUNTINO_POMERIGGIO, 2, breakfast, profile, onlyJuice, Random(2), MealSlot.META_POMERIGGIO)
        assertTrue(noJuice.meal.components.none { it.foodId == "spremuta_arancia" })
        val firstJuice = generator.generateMeal(MealType.SPUNTINO_POMERIGGIO, 4, week, profile, onlyJuice, Random(2), MealSlot.META_POMERIGGIO)
        assertEquals("spremuta_arancia", firstJuice.meal.components.single { it.role == MealRole.SPUNTINO_FRUTTA }.foodId)
    }

    @Test
    fun swapsBlockTheSecondPeanutButterOrJuiceOfTheSameDay() {
        val profile = UserProfile()
        val base = generate(seed = 11, profile = profile).meals
        val week = withComponent(base, 0, MealSlot.META_MATTINA, MealRole.SPUNTINO_SECCA, "burro_arachidi")
        val afternoon = week.meal(0, MealSlot.META_POMERIGGIO)
        val alternatives = swapUseCase.alternativesFor(afternoon, week, foods, profile, maxAlternatives = 500)
        val withButter = alternatives.filter { alt -> alt.components.any { it.foodId == "burro_arachidi" } }
        assertTrue("l'alternativa con il burro viene proposta (bloccata)", withButter.isNotEmpty())
        withButter.forEach {
            assertFalse(it.allowed)
            assertTrue(it.exceedsDailyCap)
            assertEquals(listOf("Supereresti il limite giornaliero: Burro di arachidi (max 1 al giorno)"), it.blockingReasonsIt)
            assertEquals("Burro di arachidi già presente oggi (max 1 al giorno).", it.warningMessageIt)
        }
        // Lo stesso giorno dopo il burro a meta mattina: cambiare il pomeriggio con altro e permesso.
        assertTrue(alternatives.any { it.allowed && it.components.none { c -> c.foodId == "burro_arachidi" } })

        // Altro giorno senza burro: permesso.
        val freeDay = (1..6).first { dayCount(week, it, "burro_arachidi") == 0 }
        val otherDay = week.meal(freeDay, MealSlot.META_POMERIGGIO)
        val other = swapUseCase.alternativesFor(otherDay, week, foods, profile, maxAlternatives = 500)
            .filter { alt -> alt.components.any { it.foodId == "burro_arachidi" } }
        assertTrue(other.isNotEmpty() && other.all { it.allowed })

        // Spremuta: colazione (bevanda) + spuntino con frutta lo stesso giorno.
        val juiceWeek = withComponent(base, 1, MealSlot.COLAZIONE, MealRole.COLAZIONE_BEVANDA, "spremuta_arancia")
        val snack = juiceWeek.meal(1, MealSlot.META_POMERIGGIO)
        val juiceAlts = swapUseCase.alternativesFor(snack, juiceWeek, foods, profile, maxAlternatives = 500)
            .filter { alt -> alt.components.any { it.foodId == "spremuta_arancia" } }
        assertTrue(juiceAlts.isNotEmpty())
        juiceAlts.forEach {
            assertFalse(it.allowed)
            assertEquals(listOf("Supereresti il limite giornaliero: Spremuta d'arancia (max 1 al giorno)"), it.blockingReasonsIt)
        }
    }

    @Test
    fun everyAllowedSwapKeepsTheDailyCapsOverAWholeWeek() {
        val profile = UserProfile()
        var withCappedFood = 0
        for (seed in 1L..4L) {
            val week = generate(seed = seed, profile = profile).meals
            for (meal in week) {
                for (alt in swapUseCase.alternativesFor(meal, week, foods, profile, maxAlternatives = 500)) {
                    val swapped = week.map { if (it.dayIndex == meal.dayIndex && it.slot == meal.slot) it.copy(type = alt.type, components = alt.components) else it }
                    val over = DailyFoodCaps.maxPerDay.keys.any { id -> dayCount(swapped, meal.dayIndex, id) > 1 }
                    if (alt.components.any { it.foodId in DailyFoodCaps.maxPerDay }) withCappedFood++
                    if (alt.allowed) assertFalse("seed=$seed ${alt.descriptionIt}", over)
                    else if (over) assertTrue(alt.exceedsDailyCap)
                }
            }
        }
        assertTrue(withCappedFood > 0)
    }

    @Test
    fun complianceReportFlagsADayWithTwoPeanutButters() {
        val profile = UserProfile()
        var week = generate(seed = 11, profile = profile).meals
        week = withComponent(week, 4, MealSlot.META_MATTINA, MealRole.SPUNTINO_SECCA, "burro_arachidi")
        week = withComponent(week, 4, MealSlot.META_POMERIGGIO, MealRole.SPUNTINO_ABBINAMENTO, "burro_arachidi")
        val c = countsOf(week)
        assertEquals(1, c.giorniBurroArachidiOltreLimite)
        val check = engine.evaluate(week, foods, profile).checks.first { it.ruleId == RuleId.BURRO_ARACHIDI_GIORNO }
        assertEquals(ComplianceStatus.VIOLAZIONE, check.status)
        assertEquals("Burro di arachidi: in 1 giorno compare più di 1 volta al giorno — limite superato", check.messageIt)
        assertTrue(RuleId.BURRO_ARACHIDI_GIORNO in engine.exceededCaps(c, profile))
    }

    // ---- New foods: portions, macros, fish counting -----------------------------------------------------------

    @Test
    fun biscottiAreTwoPiecesForEveryGoalAndSportLevel() {
        val biscotti = byId.getValue("biscotti")
        for (goal in Goal.entries) for (sport in SportLevel.entries) {
            val p = portions.portionFor(MealRole.COLAZIONE_DOLCE, biscotti, goal, sport)
            assertEquals(PortionUnit.PEZZI, p.unit)
            assertEquals("$goal/$sport", 2.0, p.quantity, 0.0)
            assertEquals(26.0, p.grams, 0.0)
        }
        assertEquals("2 pz", com.nutriforma.app.domain.nutrition.PortionFormatter.format(2.0, PortionUnit.PEZZI, "biscotti"))
    }

    @Test
    fun polpoCountsAsFishAtLunchAndDinner() {
        val polpo = byId.getValue("polpo")
        val lunch = portions.portionFor(MealRole.PRANZO_SECONDO, polpo, Goal.EQUILIBRIO, SportLevel.SEDENTARIO)
        assertEquals(PortionUnit.GRAMMI, lunch.unit)
        assertEquals(165.0, lunch.quantity, 0.0) // 150-180 g
        val dinner = portions.portionFor(MealRole.CENA_SECONDO, polpo, Goal.EQUILIBRIO, SportLevel.SEDENTARIO)
        assertEquals(165.0, dinner.quantity, 0.0)
        assertEquals(100.0, portions.portionFor(MealRole.CENA_PRIMO_CONDIMENTO_PROTEICO, polpo, Goal.EQUILIBRIO, SportLevel.SEDENTARIO).quantity, 0.0)

        val week = blankWeek()
            .with(0, MealSlot.PRANZO, MealRole.PRANZO_SECONDO to "polpo")
            .with(1, MealSlot.CENA, MealRole.CENA_SECONDO to "polpo")
        assertEquals(2, countsOf(week).pesce)
        assertEquals(0, countsOf(week).carne)
    }
}
