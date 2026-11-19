package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.generate
import com.nutriforma.app.domain.meal
import com.nutriforma.app.domain.model.ComplianceStatus
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyComplianceCalculatorTest {

    private val calculator = WeeklyComplianceCalculator()
    private val foods = TestFoods.all
    private val profile = UserProfile()

    @Test
    fun liveCountersAreTheElevenRequestedOnesInOrder() {
        val snapshot = calculator.calculate(generate().meals, foods, profile)
        assertEquals(
            listOf(
                RuleId.CARNE, RuleId.CARNE_ROSSA, RuleId.PESCE, RuleId.UOVA_TOTALE, RuleId.TONNO,
                RuleId.AFFETTATO, RuleId.PATATE, RuleId.CENA_PRIMO_VERDURE, RuleId.CENA_PRIMO_PROTEICO,
                RuleId.COLAZIONE_DOLCE, RuleId.PASTO_LIBERO,
            ),
            snapshot.liveCounters.map { it.ruleId },
        )
        val c = snapshot.counts
        assertEquals(
            listOf(
                c.carne, c.carneRossa, c.pesce, c.uovaTotale, c.tonno, c.affettato, c.patate,
                c.cenePrimoVerdure, c.cenePrimoProteico, c.colazioniDolci, c.pastiLiberi,
            ),
            snapshot.liveCounters.map { it.value },
        )
        assertEquals(
            listOf("Carne", "Carne rossa", "Pesce", "Uova (totale)", "Tonno o sgombro sott'olio", "Affettato", "Patate",
                "Pasta con verdure", "Pasta con proteine o carne", "Colazioni dolci", "Pasti liberi",
            ),
            snapshot.liveCounters.map { it.labelIt },
        )
        assertFalse(snapshot.report.hasViolations)
    }

    @Test
    fun countersUpdateLiveWhenTheWeekIsEdited() {
        val week = generate().meals
        val before = calculator.calculate(week, foods, profile)
        // Tonno su un secondo, patate in un altro pranzo, un uovo in più.
        val edited = week
            .with(0, MealSlot.PRANZO, MealRole.PRANZO_SECONDO to "tonno_sottolio", MealRole.PRANZO_ACCOMPAGNAMENTO to "patate")
        val after = calculator.calculate(edited, foods, profile)
        assertEquals(before.counts.tonno + 1, after.counts.tonno)
        assertEquals(before.counts.patate + 1, after.counts.patate)
        assertEquals(ComplianceStatus.VIOLAZIONE, after.liveCounters.first { it.ruleId == RuleId.PATATE }.status)
        assertEquals(ComplianceStatus.OK, before.liveCounters.first { it.ruleId == RuleId.PATATE }.status)
        assertTrue(after.report.hasViolations)
    }

    @Test
    fun dailyTotalsCoverSevenDaysAndFlagTheFreeMeal() {
        val snapshot = calculator.calculate(generate().meals, foods, profile)
        assertEquals((0..6).toList(), snapshot.dailyNutrition.map { it.dayIndex })
        assertTrue(snapshot.dailyNutrition.all { it.totals.kcal > 1000 })
        assertEquals(listOf(5), snapshot.dailyNutrition.filter { it.includesPastoLibero }.map { it.dayIndex })
        val average = snapshot.dailyNutrition.sumOf { it.totals.kcal } / 7
        assertEquals(average, snapshot.weekAverage.kcal, 1e-9)
        val macros = snapshot.dailyNutrition.first().totals
        assertTrue(macros.proteinG > 0 && macros.carbsG > 0 && macros.fatG > 0 && macros.fiberG > 0)
    }

    @Test
    fun dailyKcalMatchesTheHandComputedSumOfTheDay() {
        val week = generate().meals
        val snapshot = calculator.calculate(week, foods, profile)
        val expected = week.filter { it.dayIndex == 0 }.flatMap { it.components }
            .sumOf { c -> TestFoods.byId.getValue(c.foodId).kcalPer100g * c.grams / 100.0 }
        assertEquals(expected, snapshot.dailyNutrition[0].totals.kcal, 1e-6)
        assertEquals(
            week.meal(0, MealSlot.PRANZO).components.size, 4,
        )
    }

    @Test
    fun sportyPortionsGiveMoreKcalThanSedentaryOnes() {
        fun avg(level: SportLevel): Double {
            val p = UserProfile(sportLevel = level)
            return calculator.calculate(generate(profile = p).meals, foods, p).weekAverage.kcal
        }
        val sporty = avg(SportLevel.MOLTO_INTENSO)
        val sedentary = avg(SportLevel.SEDENTARIO)
        assertTrue("$sporty > $sedentary", sporty > sedentary)
        // Il fattore di porzione 1,4 incide sui soli cibi scalabili: l'aumento è solo parziale.
        assertTrue(sporty < sedentary * 1.4)
    }

    @Test
    fun pastoLiberoIsExcludedFromKcalTotals() {
        val withLibero = generate().meals
        val saturday = calculator.calculate(withLibero, foods, profile).dailyNutrition[5]
        val lunchKcal = withLibero.meal(5, MealSlot.PRANZO).components.sumOf { it.grams }
        assertEquals(0.0, lunchKcal, 0.0)
        assertTrue(saturday.includesPastoLibero)
    }
}
