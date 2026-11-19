package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.model.ComplianceReport
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.NutritionTotals
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.RuleCheck
import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.nutrition.DayNutrition
import com.nutriforma.app.domain.nutrition.NutritionCalculator

/** Live weekly view: counters, per-rule status and daily/weekly nutrition. */
data class WeeklyComplianceSnapshot(
    val counts: WeeklyCounts,
    val report: ComplianceReport,
    /** Carne totale, Carne rossa, Pesce, Uova totale, Tonno, Affettato, Patate, Pasta con verdure, Pasta con proteine, Colazioni dolci, Pasti liberi. */
    val liveCounters: List<RuleCheck>,
    /** Daily totals Lunedì..Domenica. */
    val dailyNutrition: List<DayNutrition>,
    val weekAverage: NutritionTotals,
)

/**
 * Computes the live counters shown while the user edits the week. Nutrition uses the grams stored in the
 * components, which [com.nutriforma.app.domain.nutrition.PortionCalculator] already scaled for the user's
 * sport level, so the daily totals are the real "sporty" portions.
 */
class WeeklyComplianceCalculator(
    private val ruleEngine: DietRuleEngine = DietRuleEngine(),
    private val nutritionCalculator: NutritionCalculator = NutritionCalculator(),
) {

    fun calculate(meals: List<PlannedMeal>, foods: List<FoodItem>, profile: UserProfile): WeeklyComplianceSnapshot {
        val byId = foods.associateBy { it.id }
        val counts = WeeklyCounter.count(meals, byId)
        val report = ruleEngine.evaluate(meals, byId, profile)
        val live = LIVE_ORDER.mapNotNull { id -> report.checks.firstOrNull { it.ruleId == id } }
        return WeeklyComplianceSnapshot(
            counts = counts,
            report = report,
            liveCounters = live,
            dailyNutrition = nutritionCalculator.week(meals, byId),
            weekAverage = nutritionCalculator.weekAverage(meals, byId),
        )
    }

    private companion object {
        val LIVE_ORDER = listOf(
            RuleId.CARNE, RuleId.CARNE_ROSSA, RuleId.PESCE, RuleId.UOVA_TOTALE, RuleId.TONNO,
            RuleId.AFFETTATO, RuleId.PATATE, RuleId.CENA_PRIMO_VERDURE, RuleId.CENA_PRIMO_PROTEICO,
            RuleId.COLAZIONE_DOLCE, RuleId.PASTO_LIBERO,
        )
    }
}
