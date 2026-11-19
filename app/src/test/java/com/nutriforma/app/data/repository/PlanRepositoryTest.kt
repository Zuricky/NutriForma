package com.nutriforma.app.data.repository

import com.nutriforma.app.data.CountingTransactionRunner
import com.nutriforma.app.data.FakeFoodDao
import com.nutriforma.app.data.FakePlannedMealDao
import com.nutriforma.app.data.FakeShoppingCheckDao
import com.nutriforma.app.data.FakeWeeklyPlanDao
import com.nutriforma.app.data.TestDataStore
import com.nutriforma.app.data.local.entity.ShoppingCheckEntity
import com.nutriforma.app.data.mapper.toDomain
import com.nutriforma.app.data.mapper.toEntity
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.assertMealStructure
import com.nutriforma.app.domain.generator.WeeklyPlanGenerator
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import com.nutriforma.app.domain.nutrition.PortionCalculator
import com.nutriforma.app.domain.rules.DietRuleEngine
import com.nutriforma.app.domain.swap.MealSwapUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class PlanRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: TestDataStore
    private lateinit var profileRepository: UserProfileRepository
    private lateinit var planDao: FakeWeeklyPlanDao
    private lateinit var mealDao: FakePlannedMealDao
    private lateinit var checkDao: FakeShoppingCheckDao
    private lateinit var foodDao: FakeFoodDao
    private lateinit var transactions: CountingTransactionRunner
    private lateinit var repository: PlanRepository

    private val monday = LocalDate.of(2026, 11, 16)
    private val clock = Clock.fixed(Instant.parse("2026-11-17T08:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runBlocking {
        store = TestDataStore(tmp.root)
        profileRepository = UserProfileRepository(store.dataStore)
        planDao = FakeWeeklyPlanDao()
        mealDao = FakePlannedMealDao()
        checkDao = FakeShoppingCheckDao()
        foodDao = FakeFoodDao()
        transactions = CountingTransactionRunner()
        foodDao.upsertAll(TestFoods.all.mapIndexed { i, f -> f.toEntity(i) })
        repository = newRepository()
    }

    @After
    fun tearDown() {
        store.scope.cancel()
    }

    private fun newRepository() = PlanRepository(
        planDao = planDao, mealDao = mealDao, shoppingCheckDao = checkDao, transactions = transactions,
        foodRepository = FoodRepository(foodDao), userProfileRepository = profileRepository,
        generator = WeeklyPlanGenerator(), mealSwapUseCase = MealSwapUseCase(),
        portionCalculator = PortionCalculator(), clock = clock, ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun getOrCreateWeekInsertsOnePlanAnd35MealsInOneTransaction() = runBlocking {
        val plan = repository.getOrCreateWeek(monday)
        assertEquals(1, planDao.plans.value.size)
        assertEquals(35, mealDao.meals.value.size)
        assertEquals("un'unica transazione", 1, transactions.count)
        assertEquals(35, plan.meals.size)
        assertEquals(monday, plan.weekStart)
        assertEquals(monday.toEpochDay(), plan.seed)
        assertEquals(clock.millis(), plan.createdAtMillis)
        assertTrue(mealDao.meals.value.all { it.planId == plan.id })
        plan.meals.forEach(::assertMealStructure)
    }

    @Test
    fun getOrCreateWeekIsIdempotent() = runBlocking {
        val first = repository.getOrCreateWeek(monday)
        val second = repository.getOrCreateWeek(monday)
        assertEquals(first, second)
        assertEquals(1, planDao.plans.value.size)
        assertEquals(35, mealDao.meals.value.size)
        assertEquals(1, transactions.count)
    }

    @Test
    fun anyDayOfTheWeekMapsToTheSameMonday() = runBlocking {
        val a = repository.getOrCreateWeek(monday.plusDays(3))
        val b = repository.getOrCreateWeek(monday)
        assertEquals(a.id, b.id)
        assertEquals(1, planDao.plans.value.size)
    }

    @Test
    fun mealsAreReturnedInDayAndSlotOrderNotAlphabetical() = runBlocking {
        val plan = repository.getOrCreateWeek(monday)
        assertEquals(plan.meals.sortedWith(compareBy({ it.dayIndex }, { it.slot.order })), plan.meals)
        assertEquals(
            listOf(MealSlot.COLAZIONE, MealSlot.META_MATTINA, MealSlot.PRANZO, MealSlot.META_POMERIGGIO, MealSlot.CENA),
            plan.meals.take(5).map { it.slot },
        )
        val observed = repository.observeWeek(monday).first()
        assertEquals(plan, observed)
    }

    @Test
    fun observeWeekIsNullWhenThereIsNoPlan() = runBlocking {
        assertNull(repository.observeWeek(monday).first())
    }

    @Test
    fun newPlanUsesTheProfileDefaults() = runBlocking {
        profileRepository.update {
            it.copy(
                goal = Goal.ENERGIA, cheatMealsPerWeek = 2, turnoLungoDefaultDays = setOf(3, 1),
                turnoLungoDefaultOption = TurnoLungoOption.BARRETTA, turnoLungoDefaultSlot = MealSlot.META_MATTINA,
            )
        }
        val plan = repository.getOrCreateWeek(monday)
        assertEquals(Goal.ENERGIA, plan.goal)
        assertEquals(2, plan.cheatMealsPerWeek)
        assertEquals(
            listOf(TurnoLungoDay(1, TurnoLungoOption.BARRETTA, MealSlot.META_MATTINA), TurnoLungoDay(3, TurnoLungoOption.BARRETTA, MealSlot.META_MATTINA)),
            plan.turnoLungoDays,
        )
        assertEquals(2, plan.meals.count { it.type == MealType.PRANZO_LIBERO })
        assertEquals(MealType.BARRETTA_TURNO_LUNGO, plan.meals.first { it.dayIndex == 1 && it.slot == MealSlot.META_MATTINA }.type)
        assertEquals(MealType.SPUNTINO_SALTATO, plan.meals.first { it.dayIndex == 1 && it.slot == MealSlot.META_POMERIGGIO }.type)
    }

    @Test
    fun exclusionsAreHonouredAtCreation() = runBlocking {
        profileRepository.update { it.copy(dislikedFoodIds = setOf("uovo", "salmone_affumicato")) }
        val plan = repository.getOrCreateWeek(monday)
        assertTrue(plan.meals.flatMap { it.components }.none { it.foodId == "uovo" || it.foodId == "salmone_affumicato" })
    }

    @Test
    fun emptyCatalogueFailsWithAClearError() = runBlocking {
        foodDao.clear()
        try {
            repository.getOrCreateWeek(monday)
            fail("atteso IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Catalogo alimenti"))
        }
        assertTrue(planDao.plans.value.isEmpty())
    }

    @Test
    fun regenerateWeekChangesTheSeedKeepsTurnoLungoAndClearsChecks() = runBlocking {
        profileRepository.update { it.copy(turnoLungoDefaultDays = setOf(2)) }
        val original = repository.getOrCreateWeek(monday)
        checkDao.upsert(ShoppingCheckEntity(original.id, "uovo", true, 1L))
        val txBefore = transactions.count

        repository.regenerateWeek(original.id, newSeed = 12345L)

        val regenerated = repository.getOrCreateWeek(monday)
        assertEquals(original.id, regenerated.id)
        assertEquals(12345L, regenerated.seed)
        assertEquals(original.turnoLungoDays, regenerated.turnoLungoDays)
        assertEquals(35, mealDao.meals.value.size)
        assertEquals(1, planDao.plans.value.size)
        assertNotEquals(original.meals, regenerated.meals)
        assertTrue(checkDao.checks.value.isEmpty())
        assertEquals(txBefore + 1, transactions.count)
        assertEquals(clock.millis(), regenerated.updatedAtMillis)
    }

    @Test
    fun recalculatePortionsKeepsFoodsAndChangesQuantities() = runBlocking {
        val original = repository.getOrCreateWeek(monday) // EQUILIBRIO, molto intenso
        profileRepository.update { it.copy(goal = Goal.DIMAGRIMENTO) }

        repository.recalculatePortions(original.id)

        val updated = repository.getOrCreateWeek(monday)
        assertEquals(Goal.DIMAGRIMENTO, updated.goal)
        assertEquals(original.meals.map { m -> m.components.map { it.foodId } }, updated.meals.map { m -> m.components.map { it.foodId } })
        assertEquals(original.meals.map { it.type }, updated.meals.map { it.type })
        val before = original.meals.flatMap { it.components }.sumOf { it.grams }
        val after = updated.meals.flatMap { it.components }.sumOf { it.grams }
        assertTrue("$after < $before", after < before)
        // Uova e porzioni a pezzi non cambiano.
        val eggBefore = original.meals.flatMap { it.components }.filter { it.foodId == "uovo" }.map { it.quantity }
        val eggAfter = updated.meals.flatMap { it.components }.filter { it.foodId == "uovo" }.map { it.quantity }
        assertEquals(eggBefore, eggAfter)
    }

    @Test
    fun applyAlternativeReplacesTheComponentsAndMarksTheMealModified() = runBlocking {
        val plan = repository.getOrCreateWeek(monday)
        val lunch = plan.meals.first { it.dayIndex == 0 && it.slot == MealSlot.PRANZO }
        val alternative = repository.swapAlternatives(lunch.id).first { it.allowed }

        repository.applyAlternative(lunch.id, alternative.components)

        val stored = mealDao.getById(lunch.id)!!
        assertEquals(alternative.components, stored.components)
        assertTrue(stored.isUserModified)
        assertEquals(35, mealDao.meals.value.size)
        val report = DietRuleEngine().evaluate(repository.getOrCreateWeek(monday).meals, TestFoods.byId, com.nutriforma.app.domain.model.UserProfile())
        assertTrue(!report.hasViolations)
    }

    @Test
    fun applyAlternativeCanTurnAProteinPrimoIntoACompleteDishAndBack() = runBlocking {
        val plan = repository.getOrCreateWeek(monday)
        val primo = plan.meals.first { it.type == MealType.CENA_PRIMO_PROTEICO }
        val toDish = repository.swapAlternatives(primo.id).first { it.type == MealType.CENA_PIATTO_UNICO && it.allowed }

        repository.applyAlternative(primo.id, toDish.components, toDish.type)

        val stored = mealDao.getById(primo.id)!!
        assertEquals(MealType.CENA_PIATTO_UNICO, stored.type)
        assertEquals(toDish.components, stored.components)
        assertTrue(stored.isUserModified)
        assertEquals(35, mealDao.meals.value.size)
        val week = repository.getOrCreateWeek(monday).meals
        assertMealStructure(week.first { it.id == primo.id })
        assertEquals(2, com.nutriforma.app.domain.rules.WeeklyCounter.count(week, TestFoods.byId).cenePrimoProteico)
        assertTrue(!DietRuleEngine().evaluate(week, TestFoods.byId, com.nutriforma.app.domain.model.UserProfile()).hasViolations)

        // Dal piatto completo si torna a un primo con condimento proteico.
        val back = repository.swapAlternatives(primo.id).first { it.type == MealType.CENA_PRIMO_PROTEICO && it.allowed }
        repository.applyAlternative(primo.id, back.components, back.type)
        assertEquals(MealType.CENA_PRIMO_PROTEICO, mealDao.getById(primo.id)!!.type)
        assertMealStructure(repository.getOrCreateWeek(monday).meals.first { it.id == primo.id })

        // Senza tipo (chiamanti esistenti) il tipo del pasto non cambia.
        val lunch = plan.meals.first { it.dayIndex == 0 && it.slot == MealSlot.PRANZO }
        repository.applyAlternative(lunch.id, repository.swapAlternatives(lunch.id).first { it.allowed }.components)
        assertEquals(MealType.PRANZO_STANDARD, mealDao.getById(lunch.id)!!.type)
    }

    @Test
    fun pastoLiberoToggleRespectsTheWeeklyQuota() = runBlocking {
        val plan = repository.getOrCreateWeek(monday)
        val monday0 = plan.meals.first { it.dayIndex == 0 && it.slot == MealSlot.PRANZO }
        val saturday = plan.meals.first { it.dayIndex == 5 && it.slot == MealSlot.PRANZO }
        assertEquals(MealType.PRANZO_LIBERO, saturday.type)

        try {
            repository.setPranzoLibero(monday0.id, true) // quota = 1, già usato il sabato
            fail("atteso IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("pasti liberi"))
        }

        repository.setPranzoLibero(saturday.id, false)
        val restored = mealDao.getById(saturday.id)!!
        assertEquals(MealType.PRANZO_STANDARD, restored.type)
        assertEquals(4, restored.components.size)
        assertTrue(restored.isUserModified)

        repository.setPranzoLibero(monday0.id, true)
        val free = mealDao.getById(monday0.id)!!
        assertEquals(MealType.PRANZO_LIBERO, free.type)
        assertEquals(listOf("pasto_libero"), free.components.map { it.foodId })
        assertEquals(1, mealDao.meals.value.count { it.type == MealType.PRANZO_LIBERO })
        val report = DietRuleEngine().evaluate(repository.getOrCreateWeek(monday).meals, TestFoods.byId, com.nutriforma.app.domain.model.UserProfile())
        assertTrue(!report.hasViolations)
    }

    @Test
    fun turnoLungoOnAndOffRewritesOnlyThatDaysSnacks() = runBlocking {
        val plan = repository.getOrCreateWeek(monday)
        val config = TurnoLungoDay(2, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_POMERIGGIO)
        val others = { meals: List<com.nutriforma.app.data.local.entity.PlannedMealEntity> ->
            meals.filterNot { it.dayIndex == 2 && it.slot in setOf(MealSlot.META_MATTINA, MealSlot.META_POMERIGGIO) }.sortedBy { it.id }
        }
        val before = others(mealDao.meals.value)

        repository.setTurnoLungo(plan.id, 2, config)

        val on = repository.getOrCreateWeek(monday)
        assertEquals(listOf(config), on.turnoLungoDays)
        assertEquals(MealType.SPUNTINO_SALTATO, on.meals.first { it.dayIndex == 2 && it.slot == MealSlot.META_MATTINA }.type)
        assertEquals(MealType.SPUNTINO_TURNO_LUNGO, on.meals.first { it.dayIndex == 2 && it.slot == MealSlot.META_POMERIGGIO }.type)
        assertEquals(before, others(mealDao.meals.value))
        assertEquals(35, mealDao.meals.value.size)

        repository.setTurnoLungo(plan.id, 2, null)

        val off = repository.getOrCreateWeek(monday)
        assertTrue(off.turnoLungoDays.isEmpty())
        assertEquals(MealType.SPUNTINO_MATTINA, off.meals.first { it.dayIndex == 2 && it.slot == MealSlot.META_MATTINA }.type)
        assertEquals(MealType.SPUNTINO_POMERIGGIO, off.meals.first { it.dayIndex == 2 && it.slot == MealSlot.META_POMERIGGIO }.type)
        assertEquals(before, others(mealDao.meals.value))
        off.meals.forEach(::assertMealStructure)
    }

    @Test
    fun swapAlternativesForAStoredMeal() = runBlocking {
        val plan = repository.getOrCreateWeek(monday)
        val lunch = mealDao.meals.value.first { it.planId == plan.id && it.dayIndex == 0 && it.slot == MealSlot.PRANZO }
        val alternatives = repository.swapAlternatives(lunch.id)
        assertTrue(alternatives.isNotEmpty())
        assertTrue(alternatives.any { it.exceedsWeeklyCap } || alternatives.all { it.allowed })
        assertTrue(alternatives.first().allowed)
        assertTrue(repository.swapAlternatives(9999L).isEmpty())
        assertNotNull(plan)
        assertTrue(alternatives.none { it.components == lunch.components })
        assertTrue(alternatives.all { it.changedRoles.isNotEmpty() })
    }

    @Test
    fun applyExclusionsRewritesOnlyMealsFromTheGivenDayThatContainTheFood() = runBlocking {
        val plan = repository.getOrCreateWeek(monday)
        // Alimento presente sia prima che dopo il giorno 3 (verdura di cena o antipasto: molto comune).
        val before = plan.meals
        val used = before.flatMap { m -> m.components.map { it.foodId } }.groupingBy { it }.eachCount()
        val target = used.filterKeys { TestFoods.byId.getValue(it).let { f -> f.isExcludable && f.category != com.nutriforma.app.domain.model.FoodCategory.OLIO } }.maxByOrNull { it.value }!!.key
        profileRepository.update { it.copy(dislikedFoodIds = setOf(target)) }

        repository.applyExclusions(plan.id, fromDayIndex = 3)

        val after = mealDao.getForPlan(plan.id).map { it.toDomain() }
        assertTrue("da giorno 3 l'alimento non c'è più", after.filter { it.dayIndex >= 3 }.none { m -> m.components.any { it.foodId == target } })
        val untouched = before.filter { it.dayIndex < 3 }.associateBy { it.id }
        after.filter { it.dayIndex < 3 }.forEach { assertEquals(untouched.getValue(it.id).components, it.components) }
        assertEquals(35, after.size)
        after.forEach(::assertMealStructure)
    }
}
