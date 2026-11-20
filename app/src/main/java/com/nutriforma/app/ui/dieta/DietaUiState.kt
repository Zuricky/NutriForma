package com.nutriforma.app.ui.dieta

import com.nutriforma.app.domain.model.ComplianceStatus
import com.nutriforma.app.domain.model.DayNames
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.NutritionTotals
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.model.WeekPlan
import com.nutriforma.app.domain.nutrition.NutritionCalculator
import com.nutriforma.app.domain.nutrition.PortionFormatter
import com.nutriforma.app.domain.rules.DietRules
import com.nutriforma.app.domain.rules.WeeklyComplianceCalculator
import com.nutriforma.app.domain.swap.MealAlternative
import com.nutriforma.app.domain.tips.MotivationalMessage
import com.nutriforma.app.domain.tips.MotivationalMessages
import com.nutriforma.app.ui.food.FoodIcon
import com.nutriforma.app.ui.food.FoodIcons
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// ---- UI models -----------------------------------------------------------------------------------------------

data class DayChipUi(val dayIndex: Int, val shortName: String, val dayOfMonth: Int, val isToday: Boolean)

data class CompliancePillUi(val ruleId: RuleId, val text: String, val status: ComplianceStatus)

/** Una riga della scheda pasto: icona dell'alimento + testo ("Petto di pollo (180 g)"). */
data class MealComponentUi(val icon: FoodIcon, val text: String)

data class MealCardUi(
    val mealId: Long,
    val slot: MealSlot,
    val title: String,
    val timeLabel: String,
    /** e.g. "Petto di pollo (180 g) + Patate (150 g) + Verdure + Olio EVO (3 cucchiaini)". */
    val summary: String,
    /** Le stesse voci di [summary], una per riga, con l'icona dell'alimento. */
    val components: List<MealComponentUi>,
    /** "540 kcal"; `null` for free / merged meals. */
    val kcalBadge: String?,
    /** "P 32 g", "C 60 g", "G 14 g". */
    val macroBadges: List<String>,
    val isCompleted: Boolean,
    val canSwap: Boolean,
    val isFree: Boolean,
    val isMerged: Boolean,
)

data class DietaContent(
    val planId: Long,
    val selectedDay: Int,
    val days: List<DayChipUi>,
    val dayName: String,
    val dayKcalText: String,
    val dayMacrosText: String,
    val weekAverageText: String,
    val pills: List<CompliancePillUi>,
    val hasViolations: Boolean,
    val isTurnoLungo: Boolean,
    val isPranzoLibero: Boolean,
    /** `false` when the lunch is not free and the weekly free-meal quota is already used. */
    val canTogglePranzoLibero: Boolean,
    val pranzoLiberoHint: String?,
    val pranzoMealId: Long?,
    val meals: List<MealCardUi>,
    /** Complimento di oggi (giornata o settimana completata); `null` se non c'è un traguardo da celebrare. */
    val motivation: MotivationalMessage? = null,
)

sealed interface DietaUiState {
    data object Loading : DietaUiState
    data class Error(val message: String) : DietaUiState
    data class Ready(val content: DietaContent) : DietaUiState
}

data class SwapOptionUi(
    val index: Int,
    val description: String,
    val kcalDeltaText: String,
    val allowed: Boolean,
    /** Badge shown when picking it would exceed a weekly cap. */
    val capBadge: String?,
    val capMessage: String?,
    val softWarnings: List<String>,
    /** Icone degli alimenti dell'alternativa (senza condimento), per il riconoscimento a colpo d'occhio. */
    val icons: List<FoodIcon> = emptyList(),
)

sealed interface SwapSheetState {
    val mealTitle: String

    data class Loading(override val mealTitle: String) : SwapSheetState
    data class Loaded(override val mealTitle: String, val options: List<SwapOptionUi>) : SwapSheetState
}

// ---- Mapping -------------------------------------------------------------------------------------------------

private val itNumbers: NumberFormat get() = NumberFormat.getIntegerInstance(Locale.ITALY)

/** Short pill names of the "Riepilogo Regole Settimanali" card. */
private val PILL_NAMES = mapOf(
    RuleId.CARNE to "Carne",
    RuleId.CARNE_ROSSA to "Carne rossa",
    RuleId.PESCE to "Pesce",
    RuleId.UOVA_TOTALE to "Uova",
    RuleId.TONNO to "Tonno",
    RuleId.AFFETTATO to "Affettato",
    RuleId.PATATE to "Patate",
    RuleId.CENA_PRIMO_VERDURE to "Pasta con verdure",
    RuleId.CENA_PRIMO_PROTEICO to "Pasta con proteine",
    RuleId.COLAZIONE_DOLCE to "Colazione dolce",
    RuleId.PASTO_LIBERO to "Pasti liberi",
)

private val VEGETABLE_ROLES = setOf(MealRole.PRANZO_ANTIPASTO, MealRole.CENA_VERDURE)

fun formatKcal(kcal: Double): String = "${itNumbers.format(kcal.roundToInt())} kcal"

fun formatKcalDelta(delta: Int): String = when {
    delta > 0 -> "+${itNumbers.format(delta)} kcal"
    delta < 0 -> "−${itNumbers.format(-delta)} kcal"
    else -> "Stesse kcal"
}

fun timeLabel(slot: MealSlot): String = when (slot) {
    MealSlot.COLAZIONE -> "07:30"
    MealSlot.PRANZO -> "14:00–16:00"
    else -> "%02d:%02d".format(slot.defaultTimeMinutes / 60, slot.defaultTimeMinutes % 60)
}

/** One component as shown in the meal summary: "Petto di pollo (180 g)", "Verdure", "2 uova". */
fun componentText(component: PlannedComponent, foodsById: Map<String, FoodItem>): String {
    val food = foodsById[component.foodId] ?: return "Alimento non disponibile"
    val name = if (food.id == "olio_evo") "Olio EVO" else food.nameIt
    if (component.role in VEGETABLE_ROLES) return name // "a volontà": no grams shown
    val quantity = PortionFormatter.format(component)
    return when {
        quantity.isEmpty() -> name
        food.id == "uovo" -> quantity // "2 uova" already names the food
        else -> "$name ($quantity)"
    }
}

fun mealSummary(meal: PlannedMeal, foodsById: Map<String, FoodItem>): String = when (meal.type) {
    MealType.SPUNTINO_SALTATO -> "Accorpato nello spuntino unico del turno lungo"
    MealType.PRANZO_LIBERO -> "Pasto libero, goditelo!"
    else -> meal.components.joinToString(" + ") { componentText(it, foodsById) }
}

/** Voci con icona della scheda pasto; vuote per pasto libero e spuntino accorpato (hanno solo il testo di [mealSummary]). */
fun mealComponents(meal: PlannedMeal, foodsById: Map<String, FoodItem>): List<MealComponentUi> = when (meal.type) {
    MealType.SPUNTINO_SALTATO, MealType.PRANZO_LIBERO -> emptyList()
    else -> meal.components.map { c ->
        MealComponentUi(
            icon = foodsById[c.foodId]?.let(FoodIcons::forFood) ?: FoodIcon.DISH,
            text = componentText(c, foodsById),
        )
    }
}

private fun mealTitle(meal: PlannedMeal): String = when (meal.type) {
    MealType.COLAZIONE_DOLCE -> "Colazione dolce"
    MealType.CENA_PIATTO_UNICO -> "Cena · Piatto unico"
    MealType.PRANZO_LIBERO -> "Pranzo · Pasto libero"
    MealType.SPUNTINO_TURNO_LUNGO, MealType.BARRETTA_TURNO_LUNGO -> "${meal.slot.labelIt} · Turno lungo"
    else -> meal.slot.labelIt
}

private fun macroBadges(t: NutritionTotals): List<String> =
    listOf("P ${t.proteinG.roundToInt()} g", "C ${t.carbsG.roundToInt()} g", "G ${t.fatG.roundToInt()} g")

fun swapSheetTitle(meal: PlannedMeal): String = "${mealTitle(meal)} · ${DayNames.it[meal.dayIndex]}"

fun swapOptionUi(index: Int, alt: MealAlternative, foodsById: Map<String, FoodItem> = emptyMap()) = SwapOptionUi(
    index = index,
    description = alt.descriptionIt,
    kcalDeltaText = formatKcalDelta(alt.kcalDelta),
    allowed = alt.allowed,
    capBadge = when {
        !alt.exceedsWeeklyCap -> null
        alt.exceedsDailyCap -> "Supera il limite giornaliero"
        else -> "Supera il limite settimanale"
    },
    capMessage = alt.warningMessageIt,
    softWarnings = alt.softWarningsIt,
    icons = alt.components
        .filter { it.role != MealRole.PRANZO_CONDIMENTO && it.role != MealRole.CENA_CONDIMENTO }
        .mapNotNull { c -> foodsById[c.foodId]?.let(FoodIcons::forFood) },
)

/** Builds everything the Dieta screen shows. Pure: no Android, no clock (callers pass [today]). */
fun buildDietaContent(
    plan: WeekPlan,
    foods: List<FoodItem>,
    profile: UserProfile,
    completedMealIds: Set<Long>,
    selectedDay: Int,
    today: LocalDate,
    calculator: WeeklyComplianceCalculator = WeeklyComplianceCalculator(),
    nutrition: NutritionCalculator = NutritionCalculator(),
): DietaContent {
    val day = selectedDay.coerceIn(0, 6)
    val foodsById = foods.associateBy { it.id }
    val snapshot = calculator.calculate(plan.meals, foods, profile)
    val dayMeals = plan.meals.filter { it.dayIndex == day }.sortedBy { it.slot.order }
    val dayTotals = snapshot.dailyNutrition.first { it.dayIndex == day }

    val pills = snapshot.liveCounters.map { check ->
        val range = DietRules.rangeText(check.min, check.max)
        CompliancePillUi(check.ruleId, "${PILL_NAMES[check.ruleId] ?: check.labelIt} ${check.value} ($range)", check.status)
    }

    val pranzo = dayMeals.firstOrNull { it.slot == MealSlot.PRANZO }
    val isLibero = pranzo?.type == MealType.PRANZO_LIBERO
    val quota = profile.cheatMealsPerWeek.coerceIn(1, 2)
    val used = snapshot.counts.pastiLiberi
    val canToggle = pranzo != null && (isLibero || used < quota)

    // Complimenti solo per la settimana in corso: prima quella (traguardo più grande), poi la giornata di oggi.
    val todayIndex = ChronoUnit.DAYS.between(plan.weekStart, today).toInt().takeIf { it in 0..6 }
    val completable = plan.meals.filter { it.type != MealType.SPUNTINO_SALTATO }
    val motivation = if (todayIndex == null) {
        null
    } else {
        val todayMeals = completable.filter { it.dayIndex == todayIndex }
        MotivationalMessages.forWeek(
            completable = completable.size,
            completed = completable.count { it.id in completedMealIds },
            hasViolations = snapshot.report.hasViolations,
            date = today,
        ) ?: MotivationalMessages.forDay(todayMeals.size, todayMeals.count { it.id in completedMealIds }, today)
    }

    val freeNote = if (dayTotals.includesPastoLibero) " (pasto libero escluso)" else ""
    val macros = dayTotals.totals
    val average = snapshot.weekAverage

    return DietaContent(
        planId = plan.id,
        selectedDay = day,
        days = (0..6).map { i ->
            val date = plan.weekStart.plusDays(i.toLong())
            DayChipUi(i, DayNames.short[i], date.dayOfMonth, date == today)
        },
        dayName = DayNames.it[day],
        dayKcalText = formatKcal(macros.kcal) + freeNote,
        dayMacrosText = macroBadges(macros).joinToString(" · "),
        weekAverageText = "Media settimanale: ${formatKcal(average.kcal)} al giorno",
        pills = pills,
        hasViolations = snapshot.report.hasViolations,
        isTurnoLungo = plan.turnoLungoDays.any { it.dayIndex == day },
        isPranzoLibero = isLibero,
        canTogglePranzoLibero = canToggle,
        pranzoLiberoHint = when {
            canToggle -> null
            else -> "Hai già usato ${if (quota == 1) "il pasto libero" else "i $quota pasti liberi"} di questa settimana"
        },
        pranzoMealId = pranzo?.id,
        meals = dayMeals.map { meal ->
            val totals = nutrition.meal(meal, foodsById)
            val noKcal = meal.type == MealType.PRANZO_LIBERO || meal.type == MealType.SPUNTINO_SALTATO
            MealCardUi(
                mealId = meal.id,
                slot = meal.slot,
                title = mealTitle(meal),
                timeLabel = timeLabel(meal.slot),
                summary = mealSummary(meal, foodsById),
                components = mealComponents(meal, foodsById),
                kcalBadge = if (noKcal) null else formatKcal(totals.kcal),
                macroBadges = if (noKcal) emptyList() else macroBadges(totals),
                isCompleted = meal.id in completedMealIds,
                canSwap = !noKcal && meal.components.isNotEmpty(),
                isFree = meal.type == MealType.PRANZO_LIBERO,
                isMerged = meal.type == MealType.SPUNTINO_SALTATO,
            )
        },
        motivation = motivation,
    )
}
