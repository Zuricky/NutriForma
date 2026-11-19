package com.nutriforma.app.domain.generator

import com.nutriforma.app.domain.MONDAY
import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.assertMealStructure
import com.nutriforma.app.domain.component
import com.nutriforma.app.domain.countsOf
import com.nutriforma.app.domain.generate
import com.nutriforma.app.domain.meal
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.model.WarningCode
import com.nutriforma.app.domain.nutrition.PortionCalculator
import com.nutriforma.app.domain.rules.DietRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeeklyPlanGeneratorTest {

    private val engine = DietRuleEngine()
    private val portions = PortionCalculator()
    private val byId = TestFoods.byId
    private val generator = WeeklyPlanGenerator()

    // ---- Invariants (SPEC §7.8) -------------------------------------------------------------------------------

    /** Every hard and structural invariant of SPEC §7.8 plus the weekly targets of SPEC §4.2. */
    private fun assertValidWeek(
        meals: List<PlannedMeal>, profile: UserProfile, noWarnings: Boolean, label: String,
    ) {
        // 1. 35 meals, one per (day, slot), sorted.
        assertEquals("$label: 35 pasti", 35, meals.size)
        assertEquals(35, meals.map { it.dayIndex to it.slot }.toSet().size)
        assertEquals(meals.sortedWith(compareBy({ it.dayIndex }, { it.slot.order })), meals)
        meals.forEach {
            assertEquals(0L, it.id); assertEquals(0L, it.planId); assertFalse(it.isUserModified)
            assertEquals("tipo/slot", it.slot, it.type.slot ?: it.slot)
        }
        // 2. Structure per meal type.
        meals.forEach(::assertMealStructure)
        // 5. No disliked food.
        meals.flatMap { it.components }.forEach {
            assertFalse("$label: ${it.foodId} escluso", it.foodId in profile.dislikedFoodIds)
        }
        // 6. Quantities come from PortionCalculator.
        meals.flatMap { it.components }.forEach { c ->
            val expected = portions.portionFor(c.role, byId.getValue(c.foodId), profile)
            assertEquals("$label: ${c.role}/${c.foodId}", expected.quantity, c.quantity, 0.0)
            assertEquals(expected.unit, c.unit)
            assertEquals(expected.grams, c.grams, 1e-9)
        }
        // 3. No violation; 4. no warning at all without exclusions.
        val report = engine.evaluate(meals, TestFoods.all, profile)
        assertFalse("$label: violazioni ${report.checks.filter { it.status.name == "VIOLAZIONE" }}", report.hasViolations)
        if (noWarnings) {
            assertEquals("$label: attenzioni ${report.checks.filter { it.status.name == "ATTENZIONE" }}", 0, report.warningCount)
        }
    }

    private fun assertWeeklyTargets(meals: List<PlannedMeal>, cheat: Int, label: String) {
        val c = countsOf(meals)
        assertEquals("$label dolci", 2, c.colazioniDolci)
        assertEquals("$label liberi", cheat, c.pastiLiberi)
        assertEquals("$label primi", 4, c.cenePrimo)
        assertEquals(2, c.cenePrimoVerdure)
        assertEquals(2, c.cenePrimoProteico)
        assertEquals(2, c.ceneSecondo)
        assertEquals(1, c.ceneMinestrone)
        assertTrue("$label carne ${c.carne}", c.carne in 0..4)
        assertTrue("$label rossa ${c.carneRossa}", c.carneRossa <= 1)
        assertTrue("$label pesce ${c.pesce}", c.pesce >= 2)
        assertTrue("$label uova occasioni ${c.uovaOccasioni}", c.uovaOccasioni in 2..3)
        assertTrue("$label uova totale ${c.uovaTotale}", c.uovaTotale in 5..6)
        assertTrue("$label tonno ${c.tonno}", c.tonno <= 1)
        assertTrue("$label affettato ${c.affettato}", c.affettato <= 2)
        assertTrue("$label patate ${c.patate}", c.patate <= 1)
    }

    // ---- Default week (PLAN acceptance #4) ------------------------------------------------------------------

    @Test
    fun defaultWeekMatchesTheSpecDescription() {
        val result = generate(seed = MONDAY.toEpochDay())
        val meals = result.meals
        assertValidWeek(meals, UserProfile(), noWarnings = true, label = "default")
        assertTrue("nessun avviso", result.warnings.isEmpty())

        val c = countsOf(meals)
        assertEquals(3, c.carne); assertEquals(1, c.carneRossa); assertEquals(3, c.pesce)
        assertEquals(3, c.uovaOccasioni); assertEquals(5, c.uovaTotale)
        assertEquals(1, c.patate); assertTrue(c.tonno <= 1); assertTrue(c.affettato in 1..2)
        assertEquals(2, c.colazioniDolci); assertEquals(1, c.pastiLiberi)
        assertEquals(4, c.cenePrimo); assertEquals(2, c.ceneSecondo); assertEquals(1, c.ceneMinestrone)

        // Fixed day patterns of SPEC §7.2.
        assertEquals(MealType.COLAZIONE_DOLCE, meals.meal(2, MealSlot.COLAZIONE).type)
        assertEquals(MealType.COLAZIONE_DOLCE, meals.meal(6, MealSlot.COLAZIONE).type)
        assertEquals(MealType.PRANZO_LIBERO, meals.meal(5, MealSlot.PRANZO).type)
        // "Pasta con proteine o carne": primo con condimento proteico oppure piatto completo (stesso limite).
        val proteico = setOf(MealType.CENA_PRIMO_PROTEICO, MealType.CENA_PIATTO_UNICO)
        val cene = (0..6).map { meals.meal(it, MealSlot.CENA).type }
        assertEquals(MealType.CENA_PRIMO_VERDURE, cene[0]); assertEquals(MealType.CENA_SECONDO, cene[1])
        assertEquals(MealType.CENA_MINESTRONE, cene[3]); assertEquals(MealType.CENA_PRIMO_VERDURE, cene[4])
        assertEquals(MealType.CENA_SECONDO, cene[6])
        assertTrue(cene[2] in proteico && cene[5] in proteico)
        assertEquals(MealType.CENA_PRIMO_PROTEICO, cene[2]) // la prima volta è sempre il condimento di pesce
    }

    @Test
    fun defaultWeekEggsAreOneThenTwoThenTwo() {
        val eggs = generate().meals.flatMap { m -> m.components.filter { it.foodId == "uovo" }.map { m.dayIndex to it } }
        // colazione venerdì 1 uovo, pranzo giovedì 2 uova, cena martedì 2 uova.
        assertEquals(
            listOf(
                Triple(1, MealRole.CENA_SECONDO, 2.0),
                Triple(3, MealRole.PRANZO_SECONDO, 2.0),
                Triple(4, MealRole.COLAZIONE_G1, 1.0),
            ),
            eggs.map { (day, c) -> Triple(day, c.role, c.quantity) }.sortedBy { it.first },
        )
        assertEquals(5.0, eggs.sumOf { it.second.quantity }, 0.0)
    }

    @Test
    fun withTwoPastiLiberiTheEggLunchMovesToFriday() {
        val meals = generate(profile = UserProfile(cheatMealsPerWeek = 2)).meals
        assertValidWeek(meals, UserProfile(cheatMealsPerWeek = 2), noWarnings = true, label = "cheat2")
        assertEquals(MealType.PRANZO_LIBERO, meals.meal(2, MealSlot.PRANZO).type)
        assertEquals(MealType.PRANZO_LIBERO, meals.meal(5, MealSlot.PRANZO).type)
        assertEquals("uovo", meals.meal(4, MealSlot.PRANZO).component(MealRole.PRANZO_SECONDO)!!.foodId)
        assertEquals("uovo", meals.meal(4, MealSlot.COLAZIONE).component(MealRole.COLAZIONE_G1)!!.foodId)
        val c = countsOf(meals)
        assertEquals(2, c.pastiLiberi); assertEquals(2, c.carne); assertEquals(5, c.uovaTotale)
    }

    // ---- 20 randomized generations -----------------------------------------------------------------------------

    @Test
    fun twentyRandomizedWeeksObeyEveryRule() {
        val rnd = Random(2026)
        repeat(20) { i ->
            val cheat = 1 + rnd.nextInt(2)
            val profile = UserProfile(
                goal = Goal.entries[rnd.nextInt(3)],
                sportLevel = SportLevel.entries[rnd.nextInt(5)],
                cheatMealsPerWeek = cheat,
            )
            val turno = (0..6).filter { rnd.nextInt(3) == 0 }.map {
                TurnoLungoDay(
                    it,
                    TurnoLungoOption.entries[rnd.nextInt(2)],
                    if (rnd.nextBoolean()) MealSlot.META_MATTINA else MealSlot.META_POMERIGGIO,
                )
            }
            val seed = rnd.nextLong()
            val label = "#$i seed=$seed cheat=$cheat turno=${turno.map { it.dayIndex }}"
            val result = generate(seed, profile, turno)
            assertValidWeek(result.meals, profile, noWarnings = true, label = label)
            assertWeeklyTargets(result.meals, cheat, label)
            assertTrue("$label: avvisi ${result.warnings}", result.warnings.isEmpty())
            assertEquals(
                "$label: soft rules", 0,
                engine.evaluate(result.meals, TestFoods.all, profile).checks.count { it.ruleId.ordinal >= RuleId.CARNE_BIANCA_PREFERITA.ordinal && it.status.name != "OK" },
            )
        }
    }

    @Test
    fun seeds1To200AcrossProfilesNeverViolate() {
        for (seed in 1L..200L) {
            val cheat = 1 + (seed % 2).toInt()
            val profile = UserProfile(
                goal = Goal.entries[(seed % 3).toInt()],
                sportLevel = SportLevel.entries[(seed % 5).toInt()],
                cheatMealsPerWeek = cheat,
            )
            val turno = when (seed % 3) {
                0L -> emptyList()
                1L -> listOf(
                    TurnoLungoDay(1, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_POMERIGGIO),
                    TurnoLungoDay(3, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_MATTINA),
                    TurnoLungoDay(4, TurnoLungoOption.BARRETTA, MealSlot.META_POMERIGGIO),
                )
                else -> listOf(TurnoLungoDay(0, TurnoLungoOption.BARRETTA, MealSlot.META_MATTINA))
            }
            val result = generate(seed, profile, turno)
            assertValidWeek(result.meals, profile, noWarnings = true, label = "seed=$seed")
            assertWeeklyTargets(result.meals, cheat, "seed=$seed")
        }
    }

    // ---- Spremuta d'arancia nel gruppo automatico ----------------------------------------------------------------

    private val juiceId = "spremuta_arancia"

    private fun juiceComponents(meals: List<PlannedMeal>) =
        meals.flatMap { m -> m.components.filter { it.foodId == juiceId }.map { m to it } }

    private fun assertJuiceDailyCap(meals: List<PlannedMeal>, label: String) {
        meals.groupBy { it.dayIndex }.forEach { (day, dayMeals) ->
            val count = dayMeals.sumOf { m -> m.components.count { it.foodId == juiceId } }
            assertTrue("$label: spremuta $count volte il giorno $day", count <= 1)
        }
    }

    @Test
    fun spremutaIsGeneratedAutomaticallyAtBreakfastAndAsAfternoonSnack() {
        var atBreakfast = 0
        var atSnack = 0
        var weeksWithJuice = 0
        for (seed in 1L..200L) {
            val meals = generate(seed).meals
            val juice = juiceComponents(meals)
            if (juice.isNotEmpty()) weeksWithJuice++
            juice.forEach { (meal, component) ->
                when (component.role) {
                    MealRole.COLAZIONE_BEVANDA -> {
                        assertEquals(MealType.COLAZIONE_STANDARD, meal.type)
                        atBreakfast++
                    }
                    MealRole.SPUNTINO_FRUTTA -> {
                        assertEquals(MealType.SPUNTINO_POMERIGGIO, meal.type)
                        atSnack++
                    }
                    else -> throw AssertionError("spremuta in un ruolo inatteso: ${component.role}")
                }
                assertEquals(PortionUnit.MILLILITRI, component.unit)
            }
            // Mai in ogni pasto: al massimo una a colazione e una nello spuntino per settimana.
            assertTrue("seed=$seed: spremuta ${juice.size} volte", juice.size <= 2)
        }
        assertTrue("spremuta a colazione mai generata ($atBreakfast)", atBreakfast > 0)
        assertTrue("spremuta nello spuntino mai generata ($atSnack)", atSnack > 0)
        // Frequenza naturale: compare quasi ogni settimana ma resta un'eccezione tra le colazioni e gli spuntini
        // del pomeriggio (14 posti a settimana): meno di 1 posto su 6.
        assertTrue("settimane con spremuta: $weeksWithJuice/200", weeksWithJuice >= 100)
        val share = (atBreakfast + atSnack) / (200.0 * 14)
        assertTrue("spremuta in troppi pasti: $share", share < 1.0 / 6)
    }

    @Test
    fun spremutaNeverExceedsOnePerDayAcrossSeedsGoalsAndSportLevels() {
        var juiceDays = 0
        for (seed in 1L..200L) {
            for (goal in Goal.entries) for (sport in SportLevel.entries) {
                val cheat = 1 + (seed % 2).toInt()
                val profile = UserProfile(goal = goal, sportLevel = sport, cheatMealsPerWeek = cheat)
                val label = "seed=$seed $goal $sport"
                val meals = generate(seed, profile).meals
                assertJuiceDailyCap(meals, label)
                juiceDays += meals.filter { m -> m.components.any { it.foodId == juiceId } }.map { it.dayIndex }.toSet().size
                assertEquals(label, 0, countsOf(meals).giorniSpremutaOltreLimite)
                assertEquals(label, 0, countsOf(meals).giorniBurroArachidiOltreLimite)
                assertValidWeek(meals, profile, noWarnings = true, label = label)
                assertWeeklyTargets(meals, cheat, label)
            }
        }
        assertTrue("la spremuta deve comparire anche con obiettivi e sport diversi", juiceDays > 0)
    }

    @Test
    fun spremutaIsNeverPlannedWhenDisliked() {
        val profile = UserProfile(dislikedFoodIds = setOf(juiceId))
        for (seed in 1L..200L) {
            val result = generate(seed, profile)
            assertTrue("seed=$seed", juiceComponents(result.meals).isEmpty())
            assertValidWeek(result.meals, profile, noWarnings = true, label = "senza spremuta seed=$seed")
        }
    }

    @Test
    fun spremutaDoesNotEnterTurnoLungoOrBarrettaSnacks() {
        for (seed in 1L..100L) {
            val meals = generate(seed, UserProfile(), turno).meals
            assertJuiceDailyCap(meals, "turno seed=$seed")
            juiceComponents(meals).forEach { (meal, _) ->
                assertTrue("seed=$seed: spremuta in ${meal.type}", meal.type != MealType.SPUNTINO_TURNO_LUNGO)
                assertTrue(meal.type != MealType.BARRETTA_TURNO_LUNGO)
            }
        }
    }

    @Test
    fun regeneratedSnackNeverRepeatsTheJuiceOfTheSameDay() {
        val profile = UserProfile()
        var checked = 0
        for (seed in 1L..200L) {
            val week = generate(seed).meals
            // Giorni con la spremuta già a colazione.
            val days = week.filter { m -> m.type == MealType.COLAZIONE_STANDARD && m.components.any { it.foodId == juiceId } }
                .map { it.dayIndex }
            for (day in days) {
                val snack = week.meal(day, MealSlot.META_POMERIGGIO)
                repeat(10) { r ->
                    val rebuilt = generator.generateMeal(
                        snack.type, day, week, profile, TestFoods.all, Random(seed * 31 + r), snack.slot,
                    ).meal
                    assertTrue("seed=$seed giorno $day", rebuilt.components.none { it.foodId == juiceId })
                    checked++
                }
            }
        }
        assertTrue("nessun caso verificato", checked > 0)
    }

    @Test
    fun regeneratedBreakfastNeverRepeatsTheJuiceOfTheSameDay() {
        val profile = UserProfile()
        var checked = 0
        for (seed in 1L..200L) {
            val week = generate(seed).meals
            val days = week.filter { m -> m.type == MealType.SPUNTINO_POMERIGGIO && m.components.any { it.foodId == juiceId } }
                .map { it.dayIndex }
            for (day in days) {
                val breakfast = week.meal(day, MealSlot.COLAZIONE)
                if (breakfast.type != MealType.COLAZIONE_STANDARD) continue
                repeat(10) { r ->
                    val rebuilt = generator.generateMeal(
                        breakfast.type, day, week, profile, TestFoods.all, Random(seed * 17 + r), breakfast.slot,
                    ).meal
                    assertTrue("seed=$seed giorno $day", rebuilt.components.none { it.foodId == juiceId })
                    checked++
                }
            }
        }
        assertTrue("nessun caso verificato", checked > 0)
    }

    // ---- Determinism and variety ---------------------------------------------------------------------------------

    @Test
    fun sameInputGivesIdenticalOutput() {
        assertEquals(generate(42), generate(42))
        val profile = UserProfile(cheatMealsPerWeek = 2, dislikedFoodIds = setOf("bresaola", "orata"))
        assertEquals(generate(7, profile), generate(7, profile))
    }

    @Test
    fun differentSeedsProduceVariety() {
        val weeks = (1L..12L).map { generate(it).meals }
        assertTrue("12 seed → almeno 8 settimane diverse", weeks.toSet().size >= 8)
        // Le regole restano identiche ma il cibo cambia: pranzo del lunedì non sempre lo stesso.
        assertTrue(weeks.map { it.meal(0, MealSlot.PRANZO).components.map { c -> c.foodId } }.toSet().size > 1)
    }

    // ---- Exclusions ------------------------------------------------------------------------------------------------

    /** Random exclusions that leave at least one food for every role (otherwise a component is legitimately omitted). */
    private fun randomExclusions(rnd: Random, size: Int): Set<String> {
        val disliked = mutableSetOf<String>()
        for (food in TestFoods.all.filter { it.isExcludable }.shuffled(rnd)) {
            if (disliked.size >= size) break
            val stillCovered = food.roles.all { role ->
                TestFoods.all.any { it.id != food.id && it.id !in disliked && role in it.roles }
            }
            if (stillCovered) disliked += food.id
        }
        return disliked
    }

    @Test
    fun dislikedFoodsAreNeverPlannedAndNeverViolate() {
        val rnd = Random(99)
        repeat(40) { i ->
            val disliked = randomExclusions(rnd, 1 + rnd.nextInt(25))
            val profile = UserProfile(dislikedFoodIds = disliked, cheatMealsPerWeek = 1 + i % 2)
            val result = generate(i + 1L, profile)
            assertValidWeek(result.meals, profile, noWarnings = false, label = "esclusioni $disliked")
            assertTrue(
                "nessun ruolo senza alimenti: ${result.warnings}",
                result.warnings.none { it.code == WarningCode.NESSUN_ALIMENTO_PER_RUOLO },
            )
        }
    }

    @Test
    fun aRoleWithoutAnyFoodIsOmittedWithAWarning() {
        val profile = UserProfile(dislikedFoodIds = setOf("grana_padano", "parmigiano_reggiano"))
        val result = generate(1, profile)
        val primoVerdure = result.meals.filter { it.type == MealType.CENA_PRIMO_VERDURE }
        assertEquals(2, primoVerdure.size)
        assertTrue(primoVerdure.all { m -> m.components.none { it.role == MealRole.CENA_PRIMO_GRANA } })
        assertEquals(2, result.warnings.count {
            it.code == WarningCode.NESSUN_ALIMENTO_PER_RUOLO && it.messageIt.startsWith("Nessun alimento disponibile per «Formaggio grattugiato»")
        })
        assertFalse(engine.evaluate(result.meals, TestFoods.all, profile).hasViolations)
    }

    @Test
    fun allFishDislikedHasNoViolationAndReportsUnreachableGoal() {
        val fish = TestFoods.idsOf(FoodCategory.PESCE)
        val profile = UserProfile(dislikedFoodIds = fish)
        for (seed in 1L..30L) {
            val result = generate(seed, profile)
            assertValidWeek(result.meals, profile, noWarnings = false, label = "senza pesce seed=$seed")
            assertEquals(0, countsOf(result.meals).pesce)
            val warning = result.warnings.singleOrNull { it.code == WarningCode.OBIETTIVO_NON_RAGGIUNGIBILE }
            assertEquals("Pesce: obiettivo non raggiungibile con le esclusioni attuali.", warning?.messageIt)
            val check = engine.evaluate(result.meals, TestFoods.all, profile).checks.single { it.ruleId == RuleId.PESCE }
            assertTrue(check.limitedByExclusions)
            // Tutto il resto resta raggiungibile.
            assertEquals(1, result.warnings.count { it.code == WarningCode.OBIETTIVO_NON_RAGGIUNGIBILE })
        }
    }

    @Test
    fun dislikedEggMeansNoEggComponentsAtAll() {
        val profile = UserProfile(dislikedFoodIds = setOf("uovo"))
        for (seed in 1L..30L) {
            val result = generate(seed, profile)
            assertValidWeek(result.meals, profile, noWarnings = false, label = "senza uova seed=$seed")
            assertTrue(result.meals.flatMap { it.components }.none { it.foodId == "uovo" })
            val c = countsOf(result.meals)
            assertEquals(0, c.uovaOccasioni); assertEquals(0, c.uovaTotale)
            val unreachable = result.warnings.filter { it.code == WarningCode.OBIETTIVO_NON_RAGGIUNGIBILE }.map { it.messageIt }
            assertTrue(unreachable.any { it.startsWith("Uova (volte)") })
            assertTrue(unreachable.any { it.startsWith("Uova (totale)") })
        }
    }

    @Test
    fun allSweetBreakfastsDislikedGivesSevenStandardBreakfasts() {
        val profile = UserProfile(dislikedFoodIds = TestFoods.idsOf(FoodCategory.DOLCE_COLAZIONE))
        val result = generate(3, profile)
        assertValidWeek(result.meals, profile, noWarnings = false, label = "senza dolci")
        assertEquals(7, result.meals.count { it.type == MealType.COLAZIONE_STANDARD })
        assertEquals(0, result.meals.count { it.type == MealType.COLAZIONE_DOLCE })
        assertTrue(result.warnings.any { it.code == WarningCode.COLAZIONE_DOLCE_NON_DISPONIBILE })
        // Mercoledì e domenica usano LATTICINI come proteina di colazione.
        listOf(2, 6).forEach { day ->
            val g1 = result.meals.meal(day, MealSlot.COLAZIONE).component(MealRole.COLAZIONE_G1)!!
            assertEquals(FoodCategory.LATTICINI, byId.getValue(g1.foodId).category)
        }
    }

    @Test
    fun oneDislikedDessertAlternatesWithTheOther() {
        val profile = UserProfile(dislikedFoodIds = setOf("torta_casalinga"))
        val meals = generate(5, profile).meals
        val dolci = listOf(2, 6).map { day -> meals.meal(day, MealSlot.COLAZIONE).component(MealRole.COLAZIONE_DOLCE)!!.foodId }
        assertTrue("dolci inattesi: $dolci", dolci.all { it == "brioche" || it == "biscotti" })
        assertEquals("i due dolci sono diversi", 2, dolci.toSet().size)
        assertEquals(2, countsOf(meals).colazioniDolci)
    }

    @Test
    fun dislikedPotatoAndRedMeatFallBackWithoutBreakingCaps() {
        val profile = UserProfile(dislikedFoodIds = setOf("patate", "manzo_magro", "vitello_magro", "lonza_maiale"))
        for (seed in 1L..30L) {
            val result = generate(seed, profile)
            assertValidWeek(result.meals, profile, noWarnings = false, label = "senza patate/carne rossa seed=$seed")
            val c = countsOf(result.meals)
            assertEquals(0, c.patate); assertEquals(0, c.carneRossa)
        }
    }

    // ---- Turno lungo -----------------------------------------------------------------------------------------------

    private val turno = listOf(
        TurnoLungoDay(1, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_POMERIGGIO),
        TurnoLungoDay(3, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_MATTINA),
        TurnoLungoDay(4, TurnoLungoOption.BARRETTA, MealSlot.META_POMERIGGIO),
    )

    @Test
    fun turnoLungoMergesFruitAndNutsIntoOneSnack() {
        val meals = generate(profile = UserProfile(), turnoLungo = turno).meals
        assertValidWeek(meals, UserProfile(), noWarnings = true, label = "turno lungo")

        val day1Pm = meals.meal(1, MealSlot.META_POMERIGGIO)
        assertEquals(MealType.SPUNTINO_TURNO_LUNGO, day1Pm.type)
        assertEquals(listOf(MealRole.SPUNTINO_FRUTTA, MealRole.SPUNTINO_SECCA), day1Pm.components.map { it.role })
        assertEquals(150.0, day1Pm.component(MealRole.SPUNTINO_FRUTTA)!!.grams, 0.0)
        val nutsCategory = byId.getValue(day1Pm.component(MealRole.SPUNTINO_SECCA)!!.foodId).category
        assertTrue(nutsCategory == FoodCategory.FRUTTA_SECCA || nutsCategory == FoodCategory.MIX_FRUTTA)
        assertEquals(MealType.SPUNTINO_SALTATO, meals.meal(1, MealSlot.META_MATTINA).type)
        assertTrue(meals.meal(1, MealSlot.META_MATTINA).components.isEmpty())

        // Slot mattina: l'altro slot è saltato.
        assertEquals(MealType.SPUNTINO_TURNO_LUNGO, meals.meal(3, MealSlot.META_MATTINA).type)
        assertEquals(MealType.SPUNTINO_SALTATO, meals.meal(3, MealSlot.META_POMERIGGIO).type)

        // Nessun abbinamento (cioccolato/grana/avocado) nei giorni di turno lungo.
        listOf(1, 3, 4).forEach { day ->
            assertTrue(meals.filter { it.dayIndex == day }.flatMap { it.components }.none { it.role == MealRole.SPUNTINO_ABBINAMENTO })
        }
        // Giorno normale: i due spuntini ordinari.
        assertEquals(MealType.SPUNTINO_MATTINA, meals.meal(0, MealSlot.META_MATTINA).type)
        assertEquals(MealType.SPUNTINO_POMERIGGIO, meals.meal(0, MealSlot.META_POMERIGGIO).type)
    }

    @Test
    fun turnoLungoBarrettaReplacesTheSnack() {
        val meals = generate(turnoLungo = turno).meals
        val bar = meals.meal(4, MealSlot.META_POMERIGGIO)
        assertEquals(MealType.BARRETTA_TURNO_LUNGO, bar.type)
        assertEquals(listOf("barretta_proteica"), bar.foodIds())
        assertEquals(1.0, bar.components.single().quantity, 0.0)
        assertEquals(MealType.SPUNTINO_SALTATO, meals.meal(4, MealSlot.META_MATTINA).type)
    }

    private fun PlannedMeal.foodIds() = components.map { it.foodId }

    @Test
    fun nutsAlternateAcrossTheWeekAndBarrettaDaysAreSkipped() {
        val meals = generate(turnoLungo = turno).meals
        val categories = (0..6).mapNotNull { day ->
            meals.filter { it.dayIndex == day }.firstNotNullOfOrNull { it.component(MealRole.SPUNTINO_SECCA) }
                ?.let { byId.getValue(it.foodId).category }
        }
        assertEquals("giorno 4 è barretta: 6 spuntini con frutta secca", 6, categories.size)
        assertEquals(FoodCategory.FRUTTA_SECCA, categories.first())
        categories.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }

    @Test
    fun barrettaDislikedFallsBackToFruitAndNutsWithAWarning() {
        val profile = UserProfile(dislikedFoodIds = setOf("barretta_proteica"))
        val result = generate(profile = profile, turnoLungo = turno)
        assertEquals(MealType.SPUNTINO_TURNO_LUNGO, result.meals.meal(4, MealSlot.META_POMERIGGIO).type)
        assertTrue(result.warnings.any { it.code == WarningCode.CATEGORIA_SOSTITUITA && it.messageIt.contains("Barretta proteica") })
        assertValidWeek(result.meals, profile, noWarnings = false, label = "senza barretta")
    }

    @Test
    fun togglingTurnoLungoRewritesOnlyThatDaysSnacks() {
        val random = { Random(11) }
        val base = generate().meals
        val profile = UserProfile()

        // ON (frutta + secca, pomeriggio) su Mercoledì.
        val on = generator.generateDaySnacks(
            2, TurnoLungoDay(2, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_POMERIGGIO), base, profile, TestFoods.all, random(),
        )
        assertEquals(listOf(MealSlot.META_MATTINA, MealSlot.META_POMERIGGIO), on.meals.map { it.slot })
        assertEquals(listOf(MealType.SPUNTINO_SALTATO, MealType.SPUNTINO_TURNO_LUNGO), on.meals.map { it.type })
        val withTurno = base.map { m -> on.meals.firstOrNull { it.dayIndex == m.dayIndex && it.slot == m.slot } ?: m }
        assertEquals(35, withTurno.size)
        assertEquals("solo gli spuntini del giorno cambiano", base.filter { it.dayIndex != 2 || it.slot !in setOf(MealSlot.META_MATTINA, MealSlot.META_POMERIGGIO) },
            withTurno.filter { it.dayIndex != 2 || it.slot !in setOf(MealSlot.META_MATTINA, MealSlot.META_POMERIGGIO) })
        assertFalse(engine.evaluate(withTurno, TestFoods.all, profile).hasViolations)
        withTurno.forEach(::assertMealStructure)

        // → BARRETTA, mattina.
        val bar = generator.generateDaySnacks(
            2, TurnoLungoDay(2, TurnoLungoOption.BARRETTA, MealSlot.META_MATTINA), withTurno, profile, TestFoods.all, random(),
        )
        assertEquals(listOf(MealType.BARRETTA_TURNO_LUNGO, MealType.SPUNTINO_SALTATO), bar.meals.map { it.type })

        // OFF: tornano i due spuntini normali.
        val off = generator.generateDaySnacks(2, null, withTurno, profile, TestFoods.all, random())
        assertEquals(listOf(MealType.SPUNTINO_MATTINA, MealType.SPUNTINO_POMERIGGIO), off.meals.map { it.type })
        off.meals.forEach(::assertMealStructure)
        assertEquals(listOf(MealRole.SPUNTINO_FRUTTA, MealRole.SPUNTINO_ABBINAMENTO), off.meals[1].components.map { it.role })
    }

    @Test
    fun togglingKeepsIdsAndNeverBreaksCaps() {
        val base = generate().meals.mapIndexed { i, m -> m.copy(id = 100L + i, planId = 9) }
        val result = generator.generateDaySnacks(
            5, TurnoLungoDay(5, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_MATTINA), base, UserProfile(), TestFoods.all, Random(1),
        )
        result.meals.forEach { m ->
            val previous = base.meal(5, m.slot)
            assertEquals(previous.id, m.id); assertEquals(9L, m.planId)
        }
    }

    // ---- Single-meal generation (SPEC §7.7) -----------------------------------------------------------------------------

    @Test
    fun regeneratingAnyMealKeepsTheWeekWithinTheCaps() {
        val week = generate().meals
        val profile = UserProfile()
        for (meal in week) {
            for (seed in 1..3) {
                val rebuilt = generator.generateMeal(meal.type, meal.dayIndex, week, profile, TestFoods.all, Random(seed), meal.slot).meal
                assertEquals(meal.type, rebuilt.type)
                assertEquals(meal.slot, rebuilt.slot)
                assertEquals(meal.dayIndex, rebuilt.dayIndex)
                assertMealStructure(rebuilt)
                val newWeek = week.map { if (it.dayIndex == meal.dayIndex && it.slot == meal.slot) rebuilt else it }
                assertFalse("${meal.type} g${meal.dayIndex}", engine.evaluate(newWeek, TestFoods.all, profile).hasViolations)
            }
        }
    }

    @Test
    fun pastoLiberoCanBeUndoneAndRedone() {
        val week = generate().meals
        val profile = UserProfile()
        val standard = generator.generateMeal(MealType.PRANZO_STANDARD, 5, week, profile, TestFoods.all, Random(1)).meal
        assertMealStructure(standard)
        assertEquals(4, standard.components.size)
        val withStandard = week.map { if (it.dayIndex == 5 && it.slot == MealSlot.PRANZO) standard else it }
        assertEquals(0, countsOf(withStandard).pastiLiberi)
        assertFalse(engine.evaluate(withStandard, TestFoods.all, profile).hasViolations)

        val libero = generator.generateMeal(MealType.PRANZO_LIBERO, 5, withStandard, profile, TestFoods.all, Random(1)).meal
        assertEquals(listOf("pasto_libero"), libero.components.map { it.foodId })
        assertEquals(0.0, libero.components.single().grams, 0.0)
    }

    @Test
    fun regeneratedMealNeverUsesDislikedFoods() {
        val profile = UserProfile(dislikedFoodIds = setOf("petto_pollo", "petto_tacchino", "coniglio", "orata"))
        val week = generate(profile = profile).meals
        for (meal in week.filter { it.slot == MealSlot.PRANZO || it.slot == MealSlot.CENA }) {
            repeat(5) { seed ->
                val rebuilt = generator.generateMeal(meal.type, meal.dayIndex, week, profile, TestFoods.all, Random(seed), meal.slot).meal
                assertTrue(rebuilt.components.none { it.foodId in profile.dislikedFoodIds })
            }
        }
    }

    @Test
    fun regenerationIsDeterministicForTheSameRandom() {
        val week = generate().meals
        val a = generator.generateMeal(MealType.PRANZO_STANDARD, 0, week, UserProfile(), TestFoods.all, Random(5))
        val b = generator.generateMeal(MealType.PRANZO_STANDARD, 0, week, UserProfile(), TestFoods.all, Random(5))
        assertEquals(a, b)
        assertNull(a.meal.component(MealRole.PRANZO_LIBERO))
    }

    // ---- Facade -----------------------------------------------------------------------------------------------------------

    @Test
    fun ruleEngineGeneratesTheSameWeekAsTheGenerator() {
        val input = GenerationInput(MONDAY, UserProfile(), TestFoods.all, 5L, emptyList())
        assertEquals(generator.generateWeek(input), engine.generateWeek(input))
    }

    @Test
    fun sportyPortionsAreBiggerThanSedentaryOnes() {
        fun kcal(sport: SportLevel): Double {
            val meals = generate(5, UserProfile(sportLevel = sport)).meals
            return meals.flatMap { it.components }.sumOf { c -> byId.getValue(c.foodId).kcalPer100g * c.grams / 100 }
        }
        assertTrue(kcal(SportLevel.MOLTO_INTENSO) > kcal(SportLevel.SEDENTARIO))
    }
}
