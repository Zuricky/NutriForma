package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.blankWeek
import com.nutriforma.app.domain.generate
import com.nutriforma.app.domain.model.ComplianceStatus
import com.nutriforma.app.domain.model.ComplianceStatus.ATTENZIONE
import com.nutriforma.app.domain.model.ComplianceStatus.OK
import com.nutriforma.app.domain.model.ComplianceStatus.VIOLAZIONE
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealRole.*
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.RuleCheck
import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.with
import com.nutriforma.app.domain.withType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DietRuleEngineTest {

    private val engine = DietRuleEngine()
    private val foods = TestFoods.all
    private val profile = UserProfile()

    private fun check(week: List<PlannedMeal>, id: RuleId, p: UserProfile = profile): RuleCheck =
        engine.evaluate(week, foods, p).checks.single { it.ruleId == id }

    /** Places [foodId] in [role] of the [slot] of days `0 until n`. */
    private fun fill(n: Int, slot: MealSlot, role: MealRole, foodId: String, base: List<PlannedMeal> = blankWeek()) =
        (0 until n).fold(base) { w, day -> w.with(day, slot, role to foodId) }

    private fun assertStatuses(id: RuleId, build: (Int) -> List<PlannedMeal>, expected: Map<Int, ComplianceStatus>) {
        expected.forEach { (value, status) ->
            val c = check(build(value), id)
            assertEquals("$id valore $value", value, c.value)
            assertEquals("$id valore $value", status, c.status)
        }
    }

    // ---- Counter rules: below min / in range / above max -------------------------------------------------

    @Test
    fun colazioniDolci() = assertStatuses(
        RuleId.COLAZIONE_DOLCE,
        { n ->
            var w = blankWeek().withType(2, MealSlot.COLAZIONE, MealType.COLAZIONE_STANDARD)
                .withType(6, MealSlot.COLAZIONE, MealType.COLAZIONE_STANDARD)
            for (d in 0 until n) w = w.withType(d, MealSlot.COLAZIONE, MealType.COLAZIONE_DOLCE)
            w
        },
        mapOf(1 to ATTENZIONE, 2 to OK, 3 to VIOLAZIONE),
    )

    @Test
    fun pastiLiberiFollowTheProfile() {
        fun week(n: Int) = (0 until n).fold(
            blankWeek().withType(5, MealSlot.PRANZO, MealType.PRANZO_STANDARD),
        ) { w, d -> w.withType(d, MealSlot.PRANZO, MealType.PRANZO_LIBERO) }
        assertEquals(ATTENZIONE, check(week(0), RuleId.PASTO_LIBERO).status)
        assertEquals(OK, check(week(1), RuleId.PASTO_LIBERO).status)
        assertEquals(VIOLAZIONE, check(week(2), RuleId.PASTO_LIBERO).status)
        val two = profile.copy(cheatMealsPerWeek = 2)
        assertEquals(ATTENZIONE, check(week(1), RuleId.PASTO_LIBERO, two).status)
        assertEquals(OK, check(week(2), RuleId.PASTO_LIBERO, two).status)
        assertEquals(VIOLAZIONE, check(week(3), RuleId.PASTO_LIBERO, two).status)
    }

    @Test
    fun dinnerTypeRules() {
        val base = blankWeek() // 2 primi verdure, 2 primi proteici, 2 secondi, 1 minestrone
        assertEquals(OK, check(base, RuleId.CENA_PRIMO).status)
        assertEquals(OK, check(base, RuleId.CENA_PRIMO_VERDURE).status)
        assertEquals(OK, check(base, RuleId.CENA_PRIMO_PROTEICO).status)
        assertEquals(OK, check(base, RuleId.CENA_SECONDO).status)
        assertEquals(OK, check(base, RuleId.CENA_MINESTRONE).status)

        // Un secondo in meno: primi 5 (> 4), secondi 1 (ok), verdure 3 (> 2).
        val extraPrimo = base.withType(1, MealSlot.CENA, MealType.CENA_PRIMO_VERDURE)
        assertEquals(VIOLAZIONE, check(extraPrimo, RuleId.CENA_PRIMO).status)
        assertEquals(VIOLAZIONE, check(extraPrimo, RuleId.CENA_PRIMO_VERDURE).status)
        assertEquals(OK, check(extraPrimo, RuleId.CENA_SECONDO).status)

        // Due primi diventano secondi: primi 2 (< 3), secondi 4 (> 2).
        val fewPrimi = base.withType(0, MealSlot.CENA, MealType.CENA_SECONDO).withType(4, MealSlot.CENA, MealType.CENA_SECONDO)
        assertEquals(ATTENZIONE, check(fewPrimi, RuleId.CENA_PRIMO).status)
        assertEquals(ATTENZIONE, check(fewPrimi, RuleId.CENA_PRIMO_VERDURE).status)
        assertEquals(VIOLAZIONE, check(fewPrimi, RuleId.CENA_SECONDO).status)

        // Minestrone: 0 → attenzione, 2 → violazione.
        assertEquals(ATTENZIONE, check(base.withType(3, MealSlot.CENA, MealType.CENA_SECONDO), RuleId.CENA_MINESTRONE).status)
        assertEquals(VIOLAZIONE, check(base.withType(1, MealSlot.CENA, MealType.CENA_MINESTRONE), RuleId.CENA_MINESTRONE).status)
    }

    @Test
    fun carneTotale() = assertStatuses(
        RuleId.CARNE, { fill(it, MealSlot.PRANZO, PRANZO_SECONDO, "petto_pollo") },
        mapOf(0 to OK, 3 to OK, 4 to OK, 5 to VIOLAZIONE),
    )

    @Test
    fun carneRossa() = assertStatuses(
        RuleId.CARNE_ROSSA, { fill(it, MealSlot.PRANZO, PRANZO_SECONDO, "manzo_magro") },
        mapOf(0 to OK, 1 to OK, 2 to VIOLAZIONE),
    )

    @Test
    fun pesceHasNoUpperBound() = assertStatuses(
        RuleId.PESCE, { fill(it, MealSlot.PRANZO, PRANZO_SECONDO, "orata") },
        mapOf(0 to ATTENZIONE, 1 to ATTENZIONE, 2 to OK, 5 to OK),
    )

    @Test
    fun tonno() = assertStatuses(
        RuleId.TONNO, { fill(it, MealSlot.PRANZO, PRANZO_SECONDO, "tonno_sottolio") },
        mapOf(0 to OK, 1 to OK, 2 to VIOLAZIONE),
    )

    @Test
    fun affettato() = assertStatuses(
        RuleId.AFFETTATO, { fill(it, MealSlot.COLAZIONE, COLAZIONE_G1, "prosciutto_cotto") },
        mapOf(0 to OK, 2 to OK, 3 to VIOLAZIONE),
    )

    @Test
    fun patate() = assertStatuses(
        RuleId.PATATE, { fill(it, MealSlot.PRANZO, PRANZO_ACCOMPAGNAMENTO, "patate") },
        mapOf(0 to OK, 1 to OK, 2 to VIOLAZIONE),
    )

    @Test
    fun eggOccasionsWithOneEggMeals() = assertStatuses(
        RuleId.UOVA_OCCASIONI, { fill(it, MealSlot.COLAZIONE, COLAZIONE_G1, "uovo") },
        mapOf(1 to ATTENZIONE, 2 to OK, 3 to OK, 4 to VIOLAZIONE),
    )

    @Test
    fun eggTotalCountsOneEggAtBreakfastAndTwoAtLunch() {
        // 1 uovo per colazione: 4 colazioni = 4 uova (< 5), 5 = 5 (ok) ma 5 occasioni (> 3).
        val oneEgg = { n: Int -> fill(n, MealSlot.COLAZIONE, COLAZIONE_G1, "uovo") }
        assertEquals(ATTENZIONE, check(oneEgg(4), RuleId.UOVA_TOTALE).status)
        assertEquals(OK, check(oneEgg(5), RuleId.UOVA_TOTALE).status)
        assertEquals(VIOLAZIONE, check(oneEgg(5), RuleId.UOVA_OCCASIONI).status)

        // 2 uova a pranzo: 2 pasti = 4 uova (< 5); + 1 colazione = 5 (ok, 3 occasioni); + 2 colazioni = 6 ok; +3 = 7 violazione.
        val twoEggs = fill(2, MealSlot.PRANZO, PRANZO_SECONDO, "uovo")
        assertEquals(4, check(twoEggs, RuleId.UOVA_TOTALE).value)
        assertEquals(ATTENZIONE, check(twoEggs, RuleId.UOVA_TOTALE).status)
        val five = twoEggs.with(4, MealSlot.COLAZIONE, COLAZIONE_G1 to "uovo")
        assertEquals(5, check(five, RuleId.UOVA_TOTALE).value)
        assertEquals(OK, check(five, RuleId.UOVA_TOTALE).status)
        assertEquals(OK, check(five, RuleId.UOVA_OCCASIONI).status)
        val seven = twoEggs.with(2, MealSlot.PRANZO, PRANZO_SECONDO to "uovo").with(4, MealSlot.COLAZIONE, COLAZIONE_G1 to "uovo")
        assertEquals(7, check(seven, RuleId.UOVA_TOTALE).value)
        assertEquals(VIOLAZIONE, check(seven, RuleId.UOVA_TOTALE).status)
    }

    // ---- Soft quality rules ----------------------------------------------------------------------------------

    @Test
    fun whiteMeatPreferred() {
        val rossa = fill(1, MealSlot.PRANZO, PRANZO_SECONDO, "manzo_magro")
        assertEquals(ATTENZIONE, check(rossa, RuleId.CARNE_BIANCA_PREFERITA).status)
        val ok = rossa.with(1, MealSlot.PRANZO, PRANZO_SECONDO to "petto_pollo")
        assertEquals(OK, check(ok, RuleId.CARNE_BIANCA_PREFERITA).status)
    }

    @Test
    fun mezzaMattinaAlternation() {
        fun secca(vararg foodIds: String?): List<PlannedMeal> =
            foodIds.foldIndexed(blankWeek()) { day, w, id ->
                if (id == null) {
                    w.withType(day, MealSlot.META_MATTINA, MealType.SPUNTINO_SALTATO)
                } else w.with(day, MealSlot.META_MATTINA, SPUNTINO_SECCA to id)
            }
        assertEquals(OK, check(secca("mandorle", "mix_frutta_secca_uvetta", "noci"), RuleId.ALTERNANZA_META_MATTINA).status)
        assertEquals(ATTENZIONE, check(secca("mandorle", "noci"), RuleId.ALTERNANZA_META_MATTINA).status)
        // Un giorno senza SPUNTINO_SECCA (barretta) spezza la catena: si confronta con l'ultimo giorno che ce l'aveva.
        assertEquals(OK, check(secca("mandorle", null, "mix_frutta_secca_uvetta"), RuleId.ALTERNANZA_META_MATTINA).status)
        assertEquals(ATTENZIONE, check(secca("mandorle", null, "noci"), RuleId.ALTERNANZA_META_MATTINA).status)
    }

    @Test
    fun dinnerVegetablesDifferFromLunch() {
        val same = blankWeek()
            .with(0, MealSlot.PRANZO, PRANZO_ANTIPASTO to "zucchine")
            .with(0, MealSlot.CENA, CENA_VERDURE to "zucchine")
        assertEquals(ATTENZIONE, check(same, RuleId.VERDURE_CENA_DIVERSE).status)
        val different = same.with(0, MealSlot.CENA, CENA_VERDURE to "carote")
        assertEquals(OK, check(different, RuleId.VERDURE_CENA_DIVERSE).status)
    }

    // ---- Exclusions, messages, report shape ------------------------------------------------------------------

    @Test
    fun limitedByExclusionsWhenEveryFishIsDisliked() {
        val p = profile.copy(dislikedFoodIds = TestFoods.idsOf(FoodCategory.PESCE))
        val c = check(blankWeek(), RuleId.PESCE, p)
        assertEquals(ATTENZIONE, c.status)
        assertTrue(c.limitedByExclusions)
        assertEquals("Non raggiungibile con le esclusioni attuali", c.messageIt)
        // Senza esclusioni lo stesso valore è un semplice "sotto l'obiettivo".
        val plain = check(blankWeek(), RuleId.PESCE)
        assertEquals(ATTENZIONE, plain.status)
        assertFalse(plain.limitedByExclusions)
    }

    @Test
    fun limitedByExclusionsForEggsAndDolci() {
        val noEggs = profile.copy(dislikedFoodIds = setOf("uovo"))
        assertTrue(check(blankWeek(), RuleId.UOVA_OCCASIONI, noEggs).limitedByExclusions)
        assertTrue(check(blankWeek(), RuleId.UOVA_TOTALE, noEggs).limitedByExclusions)
        val noDolci = profile.copy(dislikedFoodIds = TestFoods.idsOf(FoodCategory.DOLCE_COLAZIONE))
        val standardOnly = blankWeek().withType(2, MealSlot.COLAZIONE, MealType.COLAZIONE_STANDARD)
            .withType(6, MealSlot.COLAZIONE, MealType.COLAZIONE_STANDARD)
        assertTrue(check(standardOnly, RuleId.COLAZIONE_DOLCE, noDolci).limitedByExclusions)
        // Un solo pesce escluso non rende irraggiungibile l'obiettivo.
        assertFalse(check(blankWeek(), RuleId.PESCE, profile.copy(dislikedFoodIds = setOf("orata"))).limitedByExclusions)
    }

    @Test
    fun displayStringFormat() {
        val c = check(blankWeek().with(0, MealSlot.COLAZIONE, COLAZIONE_G1 to "uovo"), RuleId.UOVA_TOTALE)
        assertTrue(c.messageIt.startsWith("Uova (totale): 1 (obiettivo 5-6)"))
        val ok = check(generate().meals, RuleId.UOVA_TOTALE)
        assertEquals("Uova (totale): 5 (obiettivo 5-6)", ok.messageIt)
        assertEquals("Carne: 3 (obiettivo max 4)", check(generate().meals, RuleId.CARNE).messageIt)
        assertEquals("Pesce: 3 (obiettivo almeno 2)", check(generate().meals, RuleId.PESCE).messageIt)
        assertEquals("Colazioni dolci: 2 (obiettivo 2)", check(generate().meals, RuleId.COLAZIONE_DOLCE).messageIt)
    }

    @Test
    fun reportContainsEveryRuleInDeclarationOrder() {
        val report = engine.evaluate(generate().meals, foods, profile)
        assertEquals(RuleId.entries.toList(), report.checks.map { it.ruleId })
        assertFalse(report.hasViolations)
        assertEquals(0, report.warningCount)
    }

    @Test
    fun exceededCapsListsOnlyHardViolations() {
        val week = fill(2, MealSlot.PRANZO, PRANZO_ACCOMPAGNAMENTO, "patate")
        val counts = engine.count(week, TestFoods.byId)
        assertEquals(listOf(RuleId.PATATE), engine.exceededCaps(counts, profile).filter { it == RuleId.PATATE })
        assertNull(engine.exceededCaps(engine.count(generate().meals, TestFoods.byId), profile).firstOrNull())
    }
}
