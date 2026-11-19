package com.nutriforma.app.domain.swap

import com.nutriforma.app.domain.generator.WeeklyPlanGenerator
import com.nutriforma.app.domain.model.ComplianceStatus
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.nutrition.NutritionCalculator
import com.nutriforma.app.domain.nutrition.PortionCalculator
import com.nutriforma.app.domain.rules.DailyFoodCaps
import com.nutriforma.app.domain.rules.DietRuleEngine
import com.nutriforma.app.domain.rules.DietRules
import com.nutriforma.app.domain.rules.PortionTable
import com.nutriforma.app.domain.rules.WeeklyCounter
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/** One alternative food combination for a meal slot, with the weekly-cap verdict. */
data class MealAlternative(
    /** Full replacement component list, in meal-structure order. */
    val components: List<PlannedComponent>,
    /** Roles whose food differs from the current meal. */
    val changedRoles: List<MealRole>,
    /** Food names joined by " + " (olio and pasto libero omitted). */
    val descriptionIt: String,
    /** `false` when picking it would exceed (or worsen) a hard weekly cap. */
    val allowed: Boolean,
    val exceedsWeeklyCap: Boolean,
    /** SPEC §8.1 format: "Supereresti il limite settimanale: Patate 2/1". */
    val blockingReasonsIt: List<String>,
    /** Friendly one-line explanation when [exceedsWeeklyCap], e.g. "Patate già usate questa settimana (max 1)." */
    val warningMessageIt: String?,
    /** Soft rules that would go from OK to ATTENZIONE. */
    val softWarningsIt: List<String>,
    val kcalDelta: Int,
    /** Tipo del pasto dopo il cambio: diverso dall'attuale solo tra primo con condimento proteico e piatto unico. */
    val type: MealType,
    /** `true` quando il blocco riguarda un limite giornaliero (burro di arachidi, spremuta d'arancia). */
    val exceedsDailyCap: Boolean = false,
)

private data class Candidate(val type: MealType, val components: List<PlannedComponent>, val changedIdx: Set<Int>)

/** Regole giornaliere: `foodId` e nome mostrato nei messaggi. */
private val DAILY_RULES = mapOf(
    RuleId.BURRO_ARACHIDI_GIORNO to (DailyFoodCaps.BURRO_ARACHIDI to "Burro di arachidi"),
    RuleId.SPREMUTA_GIORNO to (DailyFoodCaps.SPREMUTA_ARANCIA to "Spremuta d'arancia"),
)

/**
 * Alternatives for one meal (day + slot): single-component substitutions plus whole-meal regenerations.
 * Disliked foods are never offered. Pure Kotlin: the data layer maps `PlannedMealEntity` ⇄ [PlannedMeal].
 */
class MealSwapUseCase(
    private val generator: WeeklyPlanGenerator = WeeklyPlanGenerator(),
    private val ruleEngine: DietRuleEngine = DietRuleEngine(),
    private val portionCalculator: PortionCalculator = PortionCalculator(),
    private val nutritionCalculator: NutritionCalculator = NutritionCalculator(),
) {

    fun alternativesFor(
        meal: PlannedMeal,
        week: List<PlannedMeal>,
        foods: List<FoodItem>,
        profile: UserProfile,
        maxAlternatives: Int = 40,
        regenerations: Int = 8,
    ): List<MealAlternative> {
        if (meal.type == MealType.PRANZO_LIBERO || meal.type == MealType.SPUNTINO_SALTATO || meal.components.isEmpty()) {
            return emptyList()
        }
        val byId = foods.associateBy { it.id }
        val candidates = linkedMapOf<String, Candidate>()

        fun signature(type: MealType, components: List<PlannedComponent>) =
            type.name + "|" + components.joinToString("|") { "${it.role}:${it.foodId}:${it.quantity}" }

        // 1. Single-component substitutions.
        meal.components.forEachIndexed { index, current ->
            if (current.role == MealRole.PRANZO_LIBERO) return@forEachIndexed
            foods.asSequence()
                .filter { current.role in it.roles && it.id !in profile.dislikedFoodIds && it.id != current.foodId }
                .filter { PortionTable.hasRow(current.role, it) }
                .forEach { food ->
                    val p = portionCalculator.portionFor(current.role, food, profile)
                    val replacement = current.copy(foodId = food.id, quantity = p.quantity, unit = p.unit, grams = p.grams)
                    val components = meal.components.toMutableList().also { it[index] = replacement }
                    candidates.putIfAbsent(signature(meal.type, components), Candidate(meal.type, components, setOf(index)))
                }
        }

        // 2. Whole-meal regenerations (always inside the caps: the generator counts the other meals).
        val currentRoles = meal.components.map { it.role }
        repeat(regenerations) { attempt ->
            val seed = meal.id * 7919L + meal.dayIndex * 131L + meal.slot.order * 17L + attempt + 1
            val generated = generator.generateMeal(
                meal.type, meal.dayIndex, week, profile, foods, Random(seed), meal.slot,
            ).meal.components
            if (generated.map { it.role } != currentRoles) return@repeat
            val changed = generated.indices.filter { generated[it].foodId != meal.components[it].foodId }.toSet()
            if (changed.isEmpty()) return@repeat
            candidates.putIfAbsent(signature(meal.type, generated), Candidate(meal.type, generated, changed))
        }

        // 3. Primo con condimento proteico <-> piatto completo (stesso limite di "pasta con proteine o carne").
        crossTypeCandidates(meal, week, foods, profile, regenerations).forEach { candidate ->
            candidates.putIfAbsent(signature(candidate.type, candidate.components), candidate)
        }

        val before = ruleEngine.evaluate(week, byId, profile)
        val beforeCounts = WeeklyCounter.count(week, byId)
        val oldKcal = nutritionCalculator.meal(meal, byId).kcal

        val collator = Collator.getInstance(Locale.ITALIAN)
        val alternatives = candidates.values.map { (type, components, changedIdx) ->
            val newMeal = meal.copy(type = type, components = components)
            val hypothetical = week.map { if (it.dayIndex == meal.dayIndex && it.slot == meal.slot) newMeal else it }
            val afterCounts = WeeklyCounter.count(hypothetical, byId)

            val blocking = DietRules.counterRules.mapNotNull { rule ->
                val max = rule.max(profile) ?: return@mapNotNull null
                val after = rule.value(afterCounts)
                if (after > max && after > rule.value(beforeCounts)) rule to after else null
            }
            val reasons = blocking.map { (rule, after) ->
                val daily = DAILY_RULES[rule.ruleId]
                if (daily != null) {
                    "Supereresti il limite giornaliero: ${daily.second} (max ${DailyFoodCaps.maxPerDay.getValue(daily.first)} al giorno)"
                } else {
                    "Supereresti il limite settimanale: ${rule.ruleId.labelIt} $after/${rule.max(profile)}"
                }
            }
            val friendly = blocking.firstOrNull()?.let { (rule, _) -> alreadyUsedMessage(rule.ruleId, rule.max(profile)!!) }
            val dailyBlocked = blocking.any { (rule, _) -> rule.ruleId in DAILY_RULES }

            val afterReport = ruleEngine.evaluate(hypothetical, byId, profile)
            val soft = afterReport.checks.filter { check ->
                check.status == ComplianceStatus.ATTENZIONE &&
                    before.checks.firstOrNull { it.ruleId == check.ruleId }?.status == ComplianceStatus.OK
            }.map { "${it.labelIt}: ${it.messageIt}" }

            MealAlternative(
                components = components,
                changedRoles = changedIdx.sorted().map { components[it].role },
                descriptionIt = components
                    .filter { it.role != MealRole.PRANZO_CONDIMENTO && it.role != MealRole.CENA_CONDIMENTO }
                    .joinToString(" + ") { byId[it.foodId]?.nameIt ?: "Alimento non disponibile" },
                allowed = blocking.isEmpty(),
                exceedsWeeklyCap = blocking.isNotEmpty(),
                blockingReasonsIt = reasons,
                warningMessageIt = friendly,
                softWarningsIt = soft,
                kcalDelta = (nutritionCalculator.meal(newMeal, byId).kcal - oldKcal).roundToInt(),
                type = type,
                exceedsDailyCap = dailyBlocked,
            )
        }

        fun sameCategory(a: MealAlternative): Boolean = a.type == meal.type && a.changedRoles.size == 1 && run {
            val role = a.changedRoles.first()
            val old = meal.components.firstOrNull { it.role == role } ?: return@run false
            val new = a.components.firstOrNull { it.role == role } ?: return@run false
            byId[old.foodId]?.category == byId[new.foodId]?.category
        }

        return alternatives
            .sortedWith(
                compareBy<MealAlternative>({ !it.allowed }, { !sameCategory(it) }, { it.changedRoles.size })
                    .thenComparator { x, y -> collator.compare(x.descriptionIt, y.descriptionIt) },
            )
            .take(maxAlternatives)
    }

    /** Alternative che cambiano il tipo di cena: primo proteico → piatto unico (4 piatti) e viceversa. */
    private fun crossTypeCandidates(
        meal: PlannedMeal,
        week: List<PlannedMeal>,
        foods: List<FoodItem>,
        profile: UserProfile,
        regenerations: Int,
    ): List<Candidate> {
        fun changedIndexes(components: List<PlannedComponent>) = components.indices.filter { i ->
            meal.components.none { it.role == components[i].role && it.foodId == components[i].foodId }
        }.toSet()

        return when (meal.type) {
            MealType.CENA_PRIMO_PROTEICO -> {
                val verdure = meal.components.firstOrNull { it.role == MealRole.CENA_VERDURE }
                foods.filter {
                    MealRole.CENA_PIATTO_UNICO in it.roles && it.id !in profile.dislikedFoodIds &&
                        PortionTable.hasRow(MealRole.CENA_PIATTO_UNICO, it)
                }.map { dish ->
                    val p = portionCalculator.portionFor(MealRole.CENA_PIATTO_UNICO, dish, profile)
                    val components = listOfNotNull(
                        verdure,
                        PlannedComponent(MealRole.CENA_PIATTO_UNICO, dish.id, p.quantity, p.unit, p.grams),
                    )
                    Candidate(MealType.CENA_PIATTO_UNICO, components, changedIndexes(components))
                }
            }
            MealType.CENA_PIATTO_UNICO -> (0 until regenerations).mapNotNull { attempt ->
                val seed = meal.id * 6151L + meal.dayIndex * 131L + attempt + 1
                val generated = generator.generateMeal(
                    MealType.CENA_PRIMO_PROTEICO, meal.dayIndex, week, profile, foods, Random(seed), meal.slot,
                ).meal
                if (generated.type != MealType.CENA_PRIMO_PROTEICO || generated.components.isEmpty()) {
                    null
                } else {
                    Candidate(MealType.CENA_PRIMO_PROTEICO, generated.components, changedIndexes(generated.components))
                }
            }
            else -> emptyList()
        }
    }

    private fun alreadyUsedMessage(ruleId: RuleId, max: Int): String = when (ruleId) {
        RuleId.BURRO_ARACHIDI_GIORNO -> "Burro di arachidi già presente oggi (max 1 al giorno)."
        RuleId.SPREMUTA_GIORNO -> "Spremuta d'arancia già presente oggi (max 1 al giorno)."
        RuleId.CENA_PRIMO_VERDURE -> "Hai già $max cene con pasta con verdure questa settimana (max $max)."
        RuleId.CENA_PRIMO_PROTEICO -> "Hai già $max cene con pasta con proteine o carne questa settimana (max $max)."
        RuleId.CENA_PRIMO -> "Hai già $max cene con pasta questa settimana (max $max)."
        RuleId.PATATE -> "Patate già usate questa settimana (max $max)."
        RuleId.CARNE_ROSSA -> "Carne rossa già presente questa settimana (max $max)."
        RuleId.TONNO -> "Tonno o sgombro già presenti questa settimana (max $max)."
        RuleId.AFFETTATO -> "Affettato già presente $max volte questa settimana."
        RuleId.CARNE -> "Hai già raggiunto il massimo di $max pasti con carne questa settimana."
        RuleId.UOVA_OCCASIONI -> "Hai già raggiunto il massimo di $max pasti con uova questa settimana."
        RuleId.UOVA_TOTALE -> "Hai già raggiunto il massimo di $max uova questa settimana."
        else -> "${ruleId.labelIt}: limite settimanale già raggiunto (max $max)."
    }
}
