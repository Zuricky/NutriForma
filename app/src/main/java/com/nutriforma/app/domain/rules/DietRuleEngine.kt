package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.generator.GenerationInput
import com.nutriforma.app.domain.generator.GenerationResult
import com.nutriforma.app.domain.generator.WeeklyPlanGenerator
import com.nutriforma.app.domain.model.ComplianceReport
import com.nutriforma.app.domain.model.ComplianceStatus
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.RuleCheck
import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.UserProfile

/**
 * Evaluates a week against SPEC §4 (counters, hard caps, soft quality rules, exclusion-aware reachability)
 * and, as a convenience facade, generates a whole week via [WeeklyPlanGenerator] (SPEC §7).
 */
class DietRuleEngine {

    // ---- Generation facade --------------------------------------------------------------------------------

    /** Generates the 7-day plan (35 meals) that obeys every hard cap and honours the exclusions. */
    fun generateWeek(input: GenerationInput): GenerationResult = WeeklyPlanGenerator(ruleEngine = this).generateWeek(input)

    // ---- Counting / caps ----------------------------------------------------------------------------------

    fun count(meals: List<PlannedMeal>, foodsById: Map<String, FoodItem>): WeeklyCounts =
        WeeklyCounter.count(meals, foodsById)

    /** Hard-cap rules whose value is above `max` (SPEC §4.2). */
    fun exceededCaps(counts: WeeklyCounts, profile: UserProfile): List<RuleId> =
        DietRules.counterRules.filter { rule ->
            val max = rule.max(profile)
            max != null && rule.value(counts) > max
        }.map { it.ruleId }

    // ---- Evaluation ---------------------------------------------------------------------------------------

    fun evaluate(meals: List<PlannedMeal>, foods: List<FoodItem>, profile: UserProfile): ComplianceReport =
        evaluate(meals, foods.associateBy { it.id }, profile)

    fun evaluate(meals: List<PlannedMeal>, foodsById: Map<String, FoodItem>, profile: UserProfile): ComplianceReport {
        val counts = WeeklyCounter.count(meals, foodsById)
        val checks = DietRules.counterRules.map { rule -> counterCheck(rule, counts, foodsById, profile) } +
            qualityChecks(meals, foodsById, counts)
        return ComplianceReport(checks)
    }

    private fun counterCheck(
        rule: RuleDefinition,
        counts: WeeklyCounts,
        foodsById: Map<String, FoodItem>,
        profile: UserProfile,
    ): RuleCheck {
        val value = rule.value(counts)
        val min = rule.min(profile)
        val max = rule.max(profile)
        val label = rule.ruleId.labelIt
        val base = "$label: $value (obiettivo ${DietRules.rangeText(min, max)})"
        return when {
            max != null && value > max -> RuleCheck(
                rule.ruleId, label, value, min, max, ComplianceStatus.VIOLAZIONE, "$base — limite superato",
            )
            value < min -> {
                val limited = isLimitedByExclusions(rule.ruleId, foodsById.values, profile)
                RuleCheck(
                    rule.ruleId, label, value, min, max, ComplianceStatus.ATTENZIONE,
                    if (limited) "Non raggiungibile con le esclusioni attuali" else "$base — sotto l'obiettivo",
                    limitedByExclusions = limited,
                )
            }
            else -> RuleCheck(rule.ruleId, label, value, min, max, ComplianceStatus.OK, base)
        }
    }

    /** SPEC §4.4 reachability: every food able to raise the counter is disliked (or does not exist). */
    private fun isLimitedByExclusions(ruleId: RuleId, foods: Collection<FoodItem>, profile: UserProfile): Boolean {
        val available = foods.filter { it.id !in profile.dislikedFoodIds }
        fun roleHasFood(role: MealRole) = available.any { role in it.roles }
        fun typeReachable(type: MealType) = MealStructure.specFor(type).filter { it.required }.all { roleHasFood(it.role) }
        return when (ruleId) {
            RuleId.PESCE -> available.none { it.category == FoodCategory.PESCE }
            RuleId.UOVA_OCCASIONI, RuleId.UOVA_TOTALE -> "uovo" in profile.dislikedFoodIds || available.none { it.category == FoodCategory.UOVA }
            RuleId.COLAZIONE_DOLCE -> available.none { it.category == FoodCategory.DOLCE_COLAZIONE }
            RuleId.CENA_PRIMO -> !typeReachable(MealType.CENA_PRIMO_VERDURE) && !typeReachable(MealType.CENA_PRIMO_PROTEICO)
            RuleId.CENA_PRIMO_VERDURE -> !typeReachable(MealType.CENA_PRIMO_VERDURE)
            RuleId.CENA_PRIMO_PROTEICO -> !typeReachable(MealType.CENA_PRIMO_PROTEICO)
            RuleId.CENA_SECONDO -> !typeReachable(MealType.CENA_SECONDO)
            RuleId.CENA_MINESTRONE -> !typeReachable(MealType.CENA_MINESTRONE)
            else -> false
        }
    }

    // ---- Soft quality rules (SPEC §4.3) ---------------------------------------------------------------------

    private fun qualityChecks(
        meals: List<PlannedMeal>,
        foodsById: Map<String, FoodItem>,
        counts: WeeklyCounts,
    ): List<RuleCheck> {
        fun check(id: RuleId, offending: Int, okMessage: String, warnMessage: String) = RuleCheck(
            ruleId = id, labelIt = id.labelIt, value = offending, min = 0, max = 0,
            status = if (offending == 0) ComplianceStatus.OK else ComplianceStatus.ATTENZIONE,
            messageIt = if (offending == 0) okMessage else warnMessage,
        )

        val carnePreferita = if (counts.carneRossa > counts.carneBianca) 1 else 0

        // Alternanza: consecutive SPUNTINO_SECCA components with the same category (days without one are skipped).
        val seccaByDay = meals.filter { it.slot == MealSlot.META_MATTINA || it.slot == MealSlot.META_POMERIGGIO }
            .mapNotNull { meal ->
                meal.components.firstOrNull { it.role == MealRole.SPUNTINO_SECCA }
                    ?.let { foodsById[it.foodId]?.category }
                    ?.let { meal.dayIndex to it }
            }
            .sortedBy { it.first }
            .map { it.second }
        val repeated = seccaByDay.zipWithNext().count { (a, b) -> a == b }

        // Verdure di cena diverse dal pranzo.
        val sameVeg = (0..6).count { day ->
            val dayMeals = meals.filter { it.dayIndex == day }
            val antipasto = dayMeals.firstNotNullOfOrNull { m -> m.components.firstOrNull { it.role == MealRole.PRANZO_ANTIPASTO } }
            val cena = dayMeals.firstNotNullOfOrNull { m -> m.components.firstOrNull { it.role == MealRole.CENA_VERDURE } }
            antipasto != null && cena != null && antipasto.foodId == cena.foodId
        }

        return listOf(
            check(
                RuleId.CARNE_BIANCA_PREFERITA, carnePreferita,
                "Carni bianche preferite: ottimo.",
                "La carne rossa supera quella bianca: preferisci le carni bianche.",
            ),
            check(
                RuleId.ALTERNANZA_META_MATTINA, repeated,
                "Frutta secca e mix si alternano a metà mattina.",
                "Frutta secca e mix non si alternano: $repeated ripetizioni consecutive.",
            ),
            check(
                RuleId.VERDURE_CENA_DIVERSE, sameVeg,
                "Verdure di cena diverse da quelle di pranzo.",
                "In $sameVeg giorni la verdura di cena è uguale a quella di pranzo.",
            ),
        )
    }
}
