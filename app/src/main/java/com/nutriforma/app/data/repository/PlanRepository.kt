package com.nutriforma.app.data.repository

import com.nutriforma.app.data.local.TransactionRunner
import com.nutriforma.app.data.local.dao.PlannedMealDao
import com.nutriforma.app.data.local.dao.ShoppingCheckDao
import com.nutriforma.app.data.local.dao.WeeklyPlanDao
import com.nutriforma.app.data.local.entity.PlannedMealEntity
import com.nutriforma.app.data.local.entity.WeeklyPlanEntity
import com.nutriforma.app.data.mapper.toDomain
import com.nutriforma.app.data.mapper.toDomainSorted
import com.nutriforma.app.data.mapper.toEntity
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.domain.generator.GenerationInput
import com.nutriforma.app.domain.generator.WeeklyPlanGenerator
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.GenerationWarning
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.model.WeekPlan
import com.nutriforma.app.domain.model.weekStart
import com.nutriforma.app.domain.nutrition.PortionCalculator
import com.nutriforma.app.domain.swap.MealAlternative
import com.nutriforma.app.domain.swap.MealSwapUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import kotlin.random.Random

/**
 * Persistence of weekly plans (including swap alternatives).
 * DECISION: takes the DAOs and a [TransactionRunner] instead of the whole database so it is unit-testable with fakes.
 */
class PlanRepository(
    private val planDao: WeeklyPlanDao,
    private val mealDao: PlannedMealDao,
    private val shoppingCheckDao: ShoppingCheckDao,
    private val transactions: TransactionRunner,
    private val foodRepository: FoodRepository,
    private val userProfileRepository: UserProfileRepository,
    private val generator: WeeklyPlanGenerator,
    private val mealSwapUseCase: MealSwapUseCase,
    private val portionCalculator: PortionCalculator,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    /** Chiamata dopo ogni modifica ai piani: creazione, rigenerazione, cambi pasto, pasto libero, turno lungo (es. backup automatico). */
    private val onDataChanged: () -> Unit = {},
) {
    private val createLock = Mutex()
    private val exclusionLock = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWeek(weekStart: LocalDate): Flow<WeekPlan?> =
        planDao.observeByWeekStart(weekStart.weekStart().toEpochDay()).flatMapLatest { plan ->
            if (plan == null) {
                flowOf(null)
            } else {
                mealDao.observeForPlan(plan.id).map { meals -> plan.toDomain(meals.toDomainSorted()) }
            }
        }

    /** Returns the plan of the week, generating it (seed = Monday's epoch day) when absent. Idempotent. */
    suspend fun getOrCreateWeek(weekStart: LocalDate): WeekPlan = withContext(ioDispatcher) {
        val monday = weekStart.weekStart()
        createLock.withLock {
            val existing = planDao.getByWeekStart(monday.toEpochDay())
            if (existing != null) {
                loadPlan(existing)
            } else {
                val profile = userProfileRepository.current()
                val foods = requireFoods()
                val turnoLungo = defaultTurnoLungo(profile)
                val seed = monday.toEpochDay()
                val result = generator.generateWeek(GenerationInput(monday, profile, foods, seed, turnoLungo))
                val now = clock.millis()
                val planId = transactions.run {
                    val id = planDao.insert(
                        WeeklyPlanEntity(
                            weekStartEpochDay = monday.toEpochDay(), seed = seed, goal = profile.goal,
                            sportLevel = profile.sportLevel, cheatMealsPerWeek = profile.cheatMealsPerWeek.coerceIn(1, 2),
                            turnoLungoDays = turnoLungo, createdAtMillis = now, updatedAtMillis = now,
                        ),
                    )
                    mealDao.insertAll(result.meals.map { it.toEntity().copy(planId = id) })
                    id
                }
                loadPlan(checkNotNull(planDao.getById(planId))).also { onDataChanged() }
            }
        }
    }

    /** New seed, same turno lungo configuration; shopping checks are cleared. */
    suspend fun regenerateWeek(planId: Long, newSeed: Long) = withContext(ioDispatcher) {
        val plan = planDao.getById(planId) ?: throw IllegalArgumentException("Piano non trovato: $planId")
        val profile = userProfileRepository.current()
        val foods = requireFoods()
        val result = generator.generateWeek(
            GenerationInput(LocalDate.ofEpochDay(plan.weekStartEpochDay), profile, foods, newSeed, plan.turnoLungoDays),
        )
        transactions.run {
            planDao.update(
                plan.copy(
                    seed = newSeed, goal = profile.goal, sportLevel = profile.sportLevel,
                    cheatMealsPerWeek = profile.cheatMealsPerWeek.coerceIn(1, 2), updatedAtMillis = clock.millis(),
                ),
            )
            mealDao.deleteForPlan(planId)
            mealDao.insertAll(result.meals.map { it.toEntity().copy(planId = planId) })
            shoppingCheckDao.clearForPlan(planId)
        }
        onDataChanged()
    }

    /** After a goal / sport change: same foods, new quantities. Unknown foods or missing rows are left untouched. */
    suspend fun recalculatePortions(planId: Long) = withContext(ioDispatcher) {
        val plan = planDao.getById(planId) ?: throw IllegalArgumentException("Piano non trovato: $planId")
        val profile = userProfileRepository.current()
        val byId = requireFoods().associateBy { it.id }
        val updated = mealDao.getForPlan(planId).map { meal ->
            meal.copy(
                components = meal.components.map { c ->
                    val food = byId[c.foodId]
                    val resolved = food?.let {
                        runCatching { portionCalculator.portionFor(c.role, it, profile) }.getOrNull()
                    }
                    if (resolved == null) c else c.copy(quantity = resolved.quantity, unit = resolved.unit, grams = resolved.grams)
                },
            )
        }
        transactions.run {
            mealDao.updateAll(updated)
            planDao.update(plan.copy(goal = profile.goal, sportLevel = profile.sportLevel, updatedAtMillis = clock.millis()))
        }
        onDataChanged()
    }

    /** Valid alternatives for a meal (day + slot), each flagged when it would exceed a weekly cap. */
    suspend fun swapAlternatives(mealId: Long): List<MealAlternative> = withContext(ioDispatcher) {
        val meal: PlannedMealEntity = mealDao.getById(mealId) ?: return@withContext emptyList()
        val week = mealDao.getForPlan(meal.planId).toDomainSorted()
        mealSwapUseCase.alternativesFor(meal.toDomain(), week, requireFoods(), userProfileRepository.current())
    }

    /**
     * Applies a [MealAlternative.components] list (from [swapAlternatives]) to the stored meal.
     * [type] cambia solo per primo con condimento proteico <-> piatto unico ([MealAlternative.type]).
     */
    suspend fun applyAlternative(
        mealId: Long,
        components: List<PlannedComponent>,
        type: MealType? = null,
    ) = withContext(ioDispatcher) {
        val meal = mealDao.getById(mealId) ?: throw IllegalArgumentException("Pasto non trovato: $mealId")
        transactions.run {
            mealDao.update(meal.copy(type = type ?: meal.type, components = components, isUserModified = true))
            touchPlan(meal.planId)
        }
        onDataChanged()
    }

    /**
     * Turns the lunch of the meal's day into a "pasto libero" ([enabled]) or back into a normal lunch (SPEC §8.2).
     * Enabling is refused once `cheatMealsPerWeek` free meals are already planned.
     */
    suspend fun setPranzoLibero(mealId: Long, enabled: Boolean) = withContext(ioDispatcher) {
        val meal = mealDao.getById(mealId) ?: throw IllegalArgumentException("Pasto non trovato: $mealId")
        check(meal.slot == MealSlot.PRANZO) { "Il pasto libero è previsto solo a pranzo" }
        val targetType = if (enabled) MealType.PRANZO_LIBERO else MealType.PRANZO_STANDARD
        if (meal.type == targetType) return@withContext
        val profile = userProfileRepository.current()
        val week = mealDao.getForPlan(meal.planId).toDomainSorted()
        if (enabled) {
            val used = week.count { it.type == MealType.PRANZO_LIBERO }
            check(used < profile.cheatMealsPerWeek.coerceIn(1, 2)) { "Hai già usato tutti i pasti liberi della settimana" }
        }
        val rebuilt = generator.generateMeal(
            targetType, meal.dayIndex, week, profile, requireFoods(), Random(clock.millis() + mealId), MealSlot.PRANZO,
        ).meal
        transactions.run {
            mealDao.update(rebuilt.copy(isUserModified = true).toEntity())
            touchPlan(meal.planId)
        }
        onDataChanged()
    }

    /** SPEC §5: rewrites only the two snack meals of [dayIndex]; [config] `null` = turno lungo off. */
    suspend fun setTurnoLungo(planId: Long, dayIndex: Int, config: TurnoLungoDay?) = withContext(ioDispatcher) {
        require(dayIndex in 0..6) { "Giorno non valido: $dayIndex" }
        val plan = planDao.getById(planId) ?: throw IllegalArgumentException("Piano non trovato: $planId")
        val profile = userProfileRepository.current()
        val week = mealDao.getForPlan(planId).toDomainSorted()
        val snacks = generator.generateDaySnacks(
            dayIndex, config, week, profile, requireFoods(), Random(clock.millis() + dayIndex),
        ).meals
        val days = (plan.turnoLungoDays.filter { it.dayIndex != dayIndex } + listOfNotNull(config)).sortedBy { it.dayIndex }
        transactions.run {
            mealDao.updateAll(snacks.map { it.copy(isUserModified = true).toEntity() })
            planDao.update(plan.copy(turnoLungoDays = days, updatedAtMillis = clock.millis()))
        }
        onDataChanged()
    }

    /**
     * Dopo una modifica di "Non mi piace": riscrive i pasti da [fromDayIndex] in poi che contengono alimenti esclusi,
     * rigenerandoli con le regole di sempre (le esclusioni valgono per generatore e alternative).
     */
    suspend fun applyExclusions(planId: Long, fromDayIndex: Int): List<GenerationWarning> = withContext(ioDispatcher) {
        exclusionLock.withLock {
            val plan = planDao.getById(planId) ?: return@withLock emptyList()
            val profile = userProfileRepository.current()
            val foods = requireFoods()
            val disliked = profile.dislikedFoodIds
            var week = mealDao.getForPlan(planId).toDomainSorted()
            val warnings = mutableListOf<GenerationWarning>()
            val changed = mutableMapOf<Long, PlannedMeal>()

            fun PlannedMeal.hasExcluded() = components.any { it.foodId in disliked }
            val snackSlots = setOf(MealSlot.META_MATTINA, MealSlot.META_POMERIGGIO)

            for (original in week.filter { it.dayIndex >= fromDayIndex }) {
                val meal = week.first { it.id == original.id }
                if (!meal.hasExcluded()) continue
                val turno = plan.turnoLungoDays.firstOrNull { it.dayIndex == meal.dayIndex }
                if (turno != null && meal.slot in snackSlots) {
                    // Turno lungo: i due spuntini del giorno si riscrivono insieme.
                    val result = generator.generateDaySnacks(meal.dayIndex, turno, week, profile, foods, Random(clock.millis() + meal.id))
                    warnings += result.warnings
                    result.meals.forEach { m -> changed[m.id] = m; week = week.map { if (it.id == m.id) m else it } }
                } else {
                    val result = generator.generateMeal(meal.type, meal.dayIndex, week, profile, foods, Random(clock.millis() + meal.id), meal.slot)
                    warnings += result.warnings
                    changed[result.meal.id] = result.meal
                    week = week.map { if (it.id == result.meal.id) result.meal else it }
                }
            }
            if (changed.isNotEmpty()) {
                transactions.run {
                    mealDao.updateAll(changed.values.map { it.toEntity() })
                    touchPlan(planId)
                }
                onDataChanged()
            }
            warnings.distinctBy { it.messageIt }
        }
    }

    private suspend fun touchPlan(planId: Long) {
        planDao.getById(planId)?.let { planDao.update(it.copy(updatedAtMillis = clock.millis())) }
    }

    private suspend fun loadPlan(plan: WeeklyPlanEntity): WeekPlan =
        plan.toDomain(mealDao.getForPlan(plan.id).toDomainSorted())

    private suspend fun requireFoods(): List<FoodItem> =
        foodRepository.getFoods().also { check(it.isNotEmpty()) { "Catalogo alimenti non ancora disponibile" } }

    private fun defaultTurnoLungo(profile: UserProfile): List<TurnoLungoDay> =
        profile.turnoLungoDefaultDays.filter { it in 0..6 }.sorted().map {
            TurnoLungoDay(it, profile.turnoLungoDefaultOption, profile.turnoLungoDefaultSlot)
        }
}
