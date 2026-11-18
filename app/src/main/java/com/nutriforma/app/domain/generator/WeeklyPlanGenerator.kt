package com.nutriforma.app.domain.generator

import com.nutriforma.app.domain.model.DayNames
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodCategory.*
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.GenerationWarning
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealRole.*
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.model.WarningCode
import com.nutriforma.app.domain.nutrition.PortionCalculator
import com.nutriforma.app.domain.rules.DietRuleEngine
import com.nutriforma.app.domain.rules.DietRules
import com.nutriforma.app.domain.rules.MealStructure
import com.nutriforma.app.domain.rules.PortionTable
import com.nutriforma.app.domain.rules.WeeklyCounter
import com.nutriforma.app.domain.rules.WeeklyCounts
import java.time.LocalDate
import kotlin.random.Random

data class GenerationInput(
    /** A Monday. */
    val weekStart: LocalDate,
    val profile: UserProfile,
    /** Full catalogue; the generator filters the exclusions itself. */
    val foods: List<FoodItem>,
    val seed: Long = weekStart.toEpochDay(),
    val turnoLungoDays: List<TurnoLungoDay> = emptyList(),
)

/** `meals` = exactly 35 meals (7 days × 5 slots), id = 0, planId = 0, sorted by (dayIndex, slot.order). */
data class GenerationResult(val meals: List<PlannedMeal>, val warnings: List<GenerationWarning>)

data class MealGenerationResult(val meal: PlannedMeal, val warnings: List<GenerationWarning>)

data class SnackGenerationResult(val meals: List<PlannedMeal>, val warnings: List<GenerationWarning>)

/**
 * SPEC §7: deterministic weekly plan generator. Randomness only through `Random(seed)`, consumed in the step order
 * of §7.3, so the same [GenerationInput] always yields the same plan. Hard caps (SPEC §4.2) are enforced while
 * placing every component, so a generated week never contains a `VIOLAZIONE`.
 */
class WeeklyPlanGenerator(
    private val portionCalculator: PortionCalculator = PortionCalculator(),
    private val ruleEngine: DietRuleEngine = DietRuleEngine(),
) {

    // ---- Public API -----------------------------------------------------------------------------------------

    fun generateWeek(input: GenerationInput): GenerationResult {
        val session = Session(input.profile, input.foods, Random(input.seed))
        session.assignWeekTypes(input.turnoLungoDays)
        session.runAllSteps()
        val meals = session.toMeals()
        val report = ruleEngine.evaluate(meals, session.foodsById, input.profile)
        val unreachable = report.checks.filter { it.limitedByExclusions }.map {
            GenerationWarning(
                WarningCode.OBIETTIVO_NON_RAGGIUNGIBILE,
                "${it.ruleId.labelIt}: obiettivo non raggiungibile con le esclusioni attuali.",
            )
        }
        return GenerationResult(meals, session.warnings + unreachable)
    }

    /**
     * SPEC §7.7: rebuilds one meal of [type] (on [slot] of [dayIndex]) with the template of that day and the counters of
     * the other meals of [week].
     * DECISION: [slot] is an explicit parameter because the turno-lungo types do not imply one.
     */
    fun generateMeal(
        type: MealType,
        dayIndex: Int,
        week: List<PlannedMeal>,
        profile: UserProfile,
        foods: List<FoodItem>,
        random: Random,
        slot: MealSlot = type.slot ?: MealSlot.META_POMERIGGIO,
    ): MealGenerationResult {
        val session = Session(profile, foods, random)
        session.loadExisting(week.filterNot { it.dayIndex == dayIndex && it.slot == slot })
        val target = session.addDraft(dayIndex, slot, type)
        session.runStepsFor(listOf(target))
        val previous = week.firstOrNull { it.dayIndex == dayIndex && it.slot == slot }
        val meal = session.toMeal(target).copy(
            id = previous?.id ?: 0, planId = previous?.planId ?: 0, isUserModified = previous?.isUserModified ?: false,
        )
        return MealGenerationResult(meal, session.warnings)
    }

    /**
     * SPEC §5: rewrites only the two snack meals of [dayIndex]. [config] = turno lungo of that day, `null` = off
     * (normal mid-morning + afternoon snacks).
     */
    fun generateDaySnacks(
        dayIndex: Int,
        config: TurnoLungoDay?,
        week: List<PlannedMeal>,
        profile: UserProfile,
        foods: List<FoodItem>,
        random: Random,
    ): SnackGenerationResult {
        val snackSlots = setOf(MealSlot.META_MATTINA, MealSlot.META_POMERIGGIO)
        val session = Session(profile, foods, random)
        session.loadExisting(week.filterNot { it.dayIndex == dayIndex && it.slot in snackSlots })
        val drafts = session.addSnackDrafts(dayIndex, config)
        session.runStepsFor(drafts)
        val meals = drafts.map { draft ->
            val previous = week.firstOrNull { it.dayIndex == dayIndex && it.slot == draft.slot }
            session.toMeal(draft).copy(
                id = previous?.id ?: 0, planId = previous?.planId ?: 0, isUserModified = previous?.isUserModified ?: false,
            )
        }
        return SnackGenerationResult(meals, session.warnings)
    }

    // ---- Session --------------------------------------------------------------------------------------------

    private class Draft(val dayIndex: Int, val slot: MealSlot, val type: MealType) {
        val components = LinkedHashMap<MealRole, PlannedComponent>()
        var secondoTemplate: FoodCategory? = null
        var cached: PlannedMeal? = null
    }

    private inner class Session(val profile: UserProfile, foods: List<FoodItem>, val random: Random) {
        val foodsById: Map<String, FoodItem> = foods.associateBy { it.id }
        val available: List<FoodItem> = foods.filter { it.id !in profile.dislikedFoodIds }
        val picker = FoodPicker(random)
        val warnings = mutableListOf<GenerationWarning>()
        val drafts = mutableListOf<Draft>()
        private val cheat = profile.cheatMealsPerWeek.coerceIn(1, 2)
        private var countsCache: WeeklyCounts? = null

        // ---- Drafts ----

        fun addDraft(dayIndex: Int, slot: MealSlot, type: MealType): Draft =
            Draft(dayIndex, slot, type).also { drafts += it; invalidate() }

        fun loadExisting(meals: List<PlannedMeal>) {
            for (meal in meals) {
                val draft = Draft(meal.dayIndex, meal.slot, meal.type)
                for (c in meal.components) {
                    draft.components[c.role] = c
                    picker.record(c.role, c.foodId, meal.dayIndex)
                }
                draft.secondoTemplate = meal.components.firstOrNull { it.role == CENA_SECONDO }
                    ?.let { foodsById[it.foodId]?.category }
                drafts += draft
            }
            invalidate()
        }

        private fun invalidate() {
            countsCache = null
            drafts.forEach { it.cached = null }
        }

        private fun plannedOf(draft: Draft): PlannedMeal = draft.cached ?: toMeal(draft).also { draft.cached = it }

        fun toMeal(draft: Draft): PlannedMeal {
            val ordered = MealStructure.specFor(draft.type).mapNotNull { draft.components[it.role] }
            return PlannedMeal(0, 0, draft.dayIndex, draft.slot, draft.type, ordered, isUserModified = false)
        }

        fun toMeals(): List<PlannedMeal> =
            drafts.map { toMeal(it) }.sortedWith(compareBy({ it.dayIndex }, { it.slot.order }))

        private fun currentCounts(): WeeklyCounts =
            countsCache ?: WeeklyCounter.count(drafts.map { plannedOf(it) }, foodsById).also { countsCache = it }

        // ---- Types per day (SPEC §7.2) ----

        fun assignWeekTypes(turnoLungoDays: List<TurnoLungoDay>) {
            val turnoByDay = turnoLungoDays.filter { it.dayIndex in 0..6 }.associateBy { it.dayIndex }
            val dolceAvailable = available.any { it.category == DOLCE_COLAZIONE && COLAZIONE_DOLCE in it.roles }
            if (!dolceAvailable) {
                warnings += GenerationWarning(
                    WarningCode.COLAZIONE_DOLCE_NON_DISPONIBILE,
                    "Nessun dolce disponibile: le colazioni dolci sono state sostituite da colazioni classiche.",
                )
            }
            val liberoDays = CategoryTemplates.liberoDays(cheat)
            for (day in 0..6) {
                val colazione = if (dolceAvailable && day in CategoryTemplates.DOLCE_DAYS) {
                    MealType.COLAZIONE_DOLCE
                } else {
                    MealType.COLAZIONE_STANDARD
                }
                addDraft(day, MealSlot.COLAZIONE, colazione)
                addSnackDrafts(day, turnoByDay[day])
                addDraft(day, MealSlot.PRANZO, if (day in liberoDays) MealType.PRANZO_LIBERO else MealType.PRANZO_STANDARD)
                addDraft(day, MealSlot.CENA, CategoryTemplates.CENA_TYPES[day])
            }
        }

        /** Creates the two snack drafts of a day (normal or turno lungo, SPEC §5). */
        fun addSnackDrafts(dayIndex: Int, config: TurnoLungoDay?): List<Draft> {
            if (config == null) {
                return listOf(
                    addDraft(dayIndex, MealSlot.META_MATTINA, MealType.SPUNTINO_MATTINA),
                    addDraft(dayIndex, MealSlot.META_POMERIGGIO, MealType.SPUNTINO_POMERIGGIO),
                )
            }
            val mainSlot = if (config.slot == MealSlot.META_MATTINA) MealSlot.META_MATTINA else MealSlot.META_POMERIGGIO
            var option = config.option
            if (option == TurnoLungoOption.BARRETTA && available.none { SPUNTINO_BARRETTA in it.roles }) {
                // DECISION: barretta excluded → the turno lungo day falls back to fruit + nuts, with a warning.
                option = TurnoLungoOption.FRUTTA_E_SECCA
                warnings += GenerationWarning(
                    WarningCode.CATEGORIA_SOSTITUITA,
                    "${DayNames.it[dayIndex]}, ${mainSlot.labelIt}: «${BARRETTA_PROTEICA.labelIt}» sostituito con " +
                        "«${TurnoLungoOption.FRUTTA_E_SECCA.labelIt}» per rispettare regole o esclusioni.",
                )
            }
            val mainType = if (option == TurnoLungoOption.BARRETTA) {
                MealType.BARRETTA_TURNO_LUNGO
            } else {
                MealType.SPUNTINO_TURNO_LUNGO
            }
            val drafts = MealSlot.entries.filter { it == MealSlot.META_MATTINA || it == MealSlot.META_POMERIGGIO }
                .map { slot -> addDraft(dayIndex, slot, if (slot == mainSlot) mainType else MealType.SPUNTINO_SALTATO) }
            return drafts
        }

        // ---- Availability and caps ----

        private fun candidates(role: MealRole, category: FoodCategory): List<FoodItem> =
            available.filter { role in it.roles && it.category == category && PortionTable.hasRow(role, it) }

        private fun componentFor(role: MealRole, food: FoodItem, optional: Boolean = false): PlannedComponent {
            val p = portionCalculator.portionFor(role, food, profile)
            return PlannedComponent(role, food.id, p.quantity, p.unit, p.grams, isOptional = optional)
        }

        /** True when adding [component] to [draft] raises no hard-cap counter beyond its maximum. */
        private fun capsAllow(draft: Draft, component: PlannedComponent): Boolean {
            val hypothetical = drafts.map { d ->
                val m = plannedOf(d)
                if (d === draft) m.copy(components = m.components + component) else m
            }
            val before = currentCounts()
            val after = WeeklyCounter.count(hypothetical, foodsById)
            return DietRules.ingredientCapRules.all { rule ->
                val max = rule.max(profile)
                max == null || rule.value(after) <= max || rule.value(after) <= rule.value(before)
            }
        }

        private fun isAvailable(draft: Draft, role: MealRole, category: FoodCategory): Boolean {
            val first = candidates(role, category).firstOrNull() ?: return false
            return capsAllow(draft, componentFor(role, first))
        }

        private fun categoriesWithRows(role: MealRole): List<FoodCategory> =
            PortionTable.rows.filter { it.role == role }
                .mapNotNull { row -> row.category ?: row.foodId?.let { foodsById[it]?.category } }
                .distinct()

        private fun substituted(draft: Draft, from: FoodCategory, to: FoodCategory) {
            warnings += GenerationWarning(
                WarningCode.CATEGORIA_SOSTITUITA,
                "${DayNames.it[draft.dayIndex]}, ${draft.slot.labelIt}: «${from.labelIt}» sostituito con " +
                    "«${to.labelIt}» per rispettare regole o esclusioni.",
            )
        }

        private fun noFood(draft: Draft, role: MealRole) {
            warnings += GenerationWarning(
                WarningCode.NESSUN_ALIMENTO_PER_RUOLO,
                "Nessun alimento disponibile per «${role.labelIt}» (${DayNames.it[draft.dayIndex]}, " +
                    "${draft.slot.labelIt}): rivedi le esclusioni.",
            )
        }

        /** Template → fallback chain → any available category with a portion row (SPEC §7.4). */
        private fun resolveCategory(draft: Draft, role: MealRole, ctx: ChainContext, template: FoodCategory): FoodCategory? {
            if (isAvailable(draft, role, template)) return template
            for (c in CategoryTemplates.fallbackChain(ctx, template)) {
                if (isAvailable(draft, role, c)) { substituted(draft, template, c); return c }
            }
            for (c in categoriesWithRows(role)) {
                if (c != template && isAvailable(draft, role, c)) { substituted(draft, template, c); return c }
            }
            return null
        }

        private fun add(draft: Draft, role: MealRole, food: FoodItem) {
            val optional = MealStructure.specFor(draft.type).firstOrNull { it.role == role }?.required == false
            draft.components[role] = componentFor(role, food, optional)
            invalidate()
        }

        /** Resolves the category and picks the food with [FoodPicker]; `false` (+ warning) when nothing fits. */
        private fun place(
            draft: Draft, role: MealRole, ctx: ChainContext, template: FoodCategory, avoidFoodId: String? = null,
        ): FoodCategory? {
            val category = resolveCategory(draft, role, ctx, template)
            if (category == null) { noFood(draft, role); return null }
            val food = picker.pick(role, candidates(role, category), draft.dayIndex, avoidFoodId) ?: run {
                noFood(draft, role); return null
            }
            add(draft, role, food)
            return category
        }

        private fun placeFixed(draft: Draft, role: MealRole, foodId: String, warnIfMissing: Boolean = true) {
            val food = available.firstOrNull { it.id == foodId && role in it.roles && PortionTable.hasRow(role, it) }
            if (food == null) { if (warnIfMissing) noFood(draft, role); return }
            add(draft, role, food)
        }

        /** First available id of [orderedIds] for [role]; else any available food of the role. */
        private fun placePreferred(draft: Draft, role: MealRole, orderedIds: List<String>, warnIfMissing: Boolean) {
            val food = orderedIds.firstNotNullOfOrNull { id ->
                available.firstOrNull { it.id == id && role in it.roles && PortionTable.hasRow(role, it) }
            } ?: available.filter { role in it.roles && PortionTable.hasRow(role, it) }.minByOrNull { it.id }
            if (food == null) { if (warnIfMissing) noFood(draft, role); return }
            add(draft, role, food)
        }

        private fun randomAvailable(draft: Draft, role: MealRole, set: List<FoodCategory>): FoodCategory? {
            val ok = set.filter { isAvailable(draft, role, it) }
            return if (ok.isEmpty()) null else ok[random.nextInt(ok.size)]
        }

        private fun rank(draft: Draft, types: Set<MealType>) =
            drafts.count { it.type in types && it.dayIndex < draft.dayIndex }

        private fun categoryOf(component: PlannedComponent?): FoodCategory? = component?.let { foodsById[it.foodId]?.category }

        // ---- Steps (SPEC §7.3) ----

        private val primoTypes = setOf(MealType.CENA_PRIMO_VERDURE, MealType.CENA_PRIMO_PROTEICO)

        private val steps: List<(Draft) -> Unit> = listOf(
            // 1. Pranzo secondi
            { d -> if (d.type == MealType.PRANZO_STANDARD) {
                val list = CategoryTemplates.pranzoSecondi(cheat)
                place(d, PRANZO_SECONDO, ChainContext.PRANZO_SECONDO, list[rank(d, setOf(MealType.PRANZO_STANDARD)) % list.size])
            } },
            // 2. Cena secondi
            { d -> if (d.type == MealType.CENA_SECONDO) {
                val base = if (d.dayIndex == 6) LEGUMI else UOVA
                val conflict = drafts.any { it !== d && it.type == MealType.CENA_SECONDO && categoryOf(it.components[CENA_SECONDO]) == base }
                val template = if (conflict) (if (base == UOVA) LEGUMI else UOVA) else base
                d.secondoTemplate = template
                place(d, CENA_SECONDO, ChainContext.CENA_SECONDO, template)
            } },
            // 3. Cena primo condimento proteico (1st occurrence PESCE, then one of pesto/ragù/tonno at random)
            { d -> if (d.type == MealType.CENA_PRIMO_PROTEICO) {
                val first = drafts.none { it.type == MealType.CENA_PRIMO_PROTEICO && it.dayIndex < d.dayIndex }
                val template = if (first) PESCE
                else randomAvailable(d, CENA_PRIMO_CONDIMENTO_PROTEICO, CategoryTemplates.PRIMO_PROTEICO_SET)
                    ?: CategoryTemplates.PRIMO_PROTEICO_SET.first()
                place(d, CENA_PRIMO_CONDIMENTO_PROTEICO, ChainContext.PRIMO_PROTEICO, template)
            } },
            // 4. Minestrone aggiunta
            { d -> if (d.type == MealType.CENA_MINESTRONE) {
                val template = randomAvailable(d, CENA_MINESTRONE_AGGIUNTA, CategoryTemplates.MINESTRONE_AGGIUNTA_SET) ?: LATTICINI
                place(d, CENA_MINESTRONE_AGGIUNTA, ChainContext.MINESTRONE_AGGIUNTA, template)
            } },
            // 5. Colazione G1
            { d -> if (d.type == MealType.COLAZIONE_STANDARD) {
                place(d, COLAZIONE_G1, ChainContext.COLAZIONE_G1, CategoryTemplates.COLAZIONE_G1_BY_DAY[d.dayIndex] ?: LATTICINI)
            } },
            // 6. Colazione G2
            { d -> if (d.type == MealType.COLAZIONE_STANDARD) {
                val k = rank(d, setOf(MealType.COLAZIONE_STANDARD))
                place(d, COLAZIONE_G2, ChainContext.COLAZIONE_G2, CategoryTemplates.COLAZIONE_G2[k % CategoryTemplates.COLAZIONE_G2.size])
            } },
            // 7. Pranzo accompagnamenti
            { d -> if (d.type == MealType.PRANZO_STANDARD) {
                val list = CategoryTemplates.PRANZO_ACCOMPAGNAMENTI
                place(d, PRANZO_ACCOMPAGNAMENTO, ChainContext.PRANZO_ACC, list[rank(d, setOf(MealType.PRANZO_STANDARD)) % list.size])
            } },
            // 8. Cena secondo accompagnamenti
            { d -> if (d.type == MealType.CENA_SECONDO) {
                val secondo = d.secondoTemplate ?: categoryOf(d.components[CENA_SECONDO])
                place(d, CENA_SECONDO_ACCOMPAGNAMENTO, ChainContext.CENA_ACC, if (secondo == LEGUMI) CEREALE_CHICCO else PANE)
            } },
            // 9. Cena primo base (+ minestrone base)
            { d ->
                if (d.type in primoTypes) {
                    val list = CategoryTemplates.PRIMO_BASE
                    place(d, CENA_PRIMO_BASE, ChainContext.PRIMO_BASE, list[rank(d, primoTypes) % list.size])
                }
                if (d.type == MealType.CENA_MINESTRONE) place(d, CENA_MINESTRONE, ChainContext.GENERIC, MINESTRONE)
            },
            // 10. Cena primo condimento verdure
            { d -> if (d.type == MealType.CENA_PRIMO_VERDURE) {
                val list = CategoryTemplates.PRIMO_COND_VERDURE
                place(d, CENA_PRIMO_CONDIMENTO_VERDURE, ChainContext.PRIMO_COND_VERDURE,
                    list[rank(d, setOf(MealType.CENA_PRIMO_VERDURE)) % list.size])
            } },
            // 11. Cena primo grana
            { d -> if (d.type == MealType.CENA_PRIMO_VERDURE) {
                place(d, CENA_PRIMO_GRANA, ChainContext.GENERIC, FORMAGGIO_STAGIONATO)
            } },
            // 12. Pranzo antipasto
            { d -> if (d.type == MealType.PRANZO_STANDARD) place(d, PRANZO_ANTIPASTO, ChainContext.GENERIC, VERDURE) },
            // 13. Cena verdure (different from the same day's antipasto when possible)
            { d -> if (d.slot == MealSlot.CENA) {
                val avoid = drafts.firstOrNull { it.dayIndex == d.dayIndex && it.slot == MealSlot.PRANZO }
                    ?.components?.get(PRANZO_ANTIPASTO)?.foodId
                place(d, CENA_VERDURE, ChainContext.GENERIC, VERDURE, avoid)
            } },
            // 14. Colazione bevanda / dolce / dolce bevanda
            { d ->
                if (d.type == MealType.COLAZIONE_STANDARD) {
                    placePreferred(d, COLAZIONE_BEVANDA, listOf("caffe_espresso", "caffe_ginseng"), warnIfMissing = false)
                }
                if (d.type == MealType.COLAZIONE_DOLCE) {
                    val k = rank(d, setOf(MealType.COLAZIONE_DOLCE)) % 2
                    placePreferred(d, COLAZIONE_DOLCE,
                        if (k == 0) listOf("torta_casalinga", "brioche") else listOf("brioche", "torta_casalinga"), true)
                    placePreferred(d, COLAZIONE_DOLCE_BEVANDA,
                        if (k == 0) listOf("cappuccino", "caffe_espresso") else listOf("caffe_espresso", "cappuccino"), true)
                }
            },
            // 15. Snacks
            { d -> when (d.type) {
                MealType.SPUNTINO_MATTINA -> placeSecca(d)
                MealType.SPUNTINO_POMERIGGIO -> {
                    place(d, SPUNTINO_FRUTTA, ChainContext.GENERIC, FRUTTA_FRESCA)
                    place(d, SPUNTINO_ABBINAMENTO, ChainContext.ABBINAMENTO, CategoryTemplates.abbinamentoFor(d.dayIndex))
                }
                MealType.SPUNTINO_TURNO_LUNGO -> {
                    place(d, SPUNTINO_FRUTTA, ChainContext.GENERIC, FRUTTA_FRESCA)
                    placeSecca(d)
                }
                MealType.BARRETTA_TURNO_LUNGO -> place(d, SPUNTINO_BARRETTA, ChainContext.GENERIC, BARRETTA_PROTEICA)
                else -> Unit
            } },
            // 16a. Olio pranzo
            { d -> if (d.type == MealType.PRANZO_STANDARD) placeFixed(d, PRANZO_CONDIMENTO, "olio_evo") },
            // 16b. Olio cena
            { d -> if (d.slot == MealSlot.CENA) placeFixed(d, CENA_CONDIMENTO, "olio_evo") },
            // 17. Pasto libero
            { d -> if (d.type == MealType.PRANZO_LIBERO) placeFixed(d, MealRole.PRANZO_LIBERO, "pasto_libero") },
        )

        /** Chain over the week: first FRUTTA_SECCA, then the opposite of the previous placed SPUNTINO_SECCA. */
        private fun placeSecca(d: Draft) {
            val previous = drafts.filter { it.dayIndex < d.dayIndex && SPUNTINO_SECCA in it.components }
                .maxByOrNull { it.dayIndex }
                ?.let { categoryOf(it.components[SPUNTINO_SECCA]) }
            place(d, SPUNTINO_SECCA, ChainContext.SECCA, CategoryTemplates.oppositeSecca(previous))
        }

        fun runAllSteps() {
            val ordered = drafts.sortedWith(compareBy({ it.dayIndex }, { it.slot.order }))
            for (step in steps) for (draft in ordered) step(draft)
        }

        fun runStepsFor(targets: List<Draft>) {
            val ordered = targets.sortedWith(compareBy({ it.dayIndex }, { it.slot.order }))
            for (step in steps) for (draft in ordered) step(draft)
        }
    }
}
