package com.nutriforma.app.ui.dieta

import androidx.lifecycle.SavedStateHandle
import com.nutriforma.app.data.AppFixture
import com.nutriforma.app.data.MutableClock
import com.nutriforma.app.data.backup.BackupFile
import com.nutriforma.app.data.backup.BackupService
import com.nutriforma.app.domain.nutrition.NutritionCalculator
import com.nutriforma.app.domain.rules.DietRuleEngine
import com.nutriforma.app.domain.rules.WeeklyComplianceCalculator
import com.nutriforma.app.ui.MainDispatcherRule
import com.nutriforma.app.ui.awaitUntil
import com.nutriforma.app.ui.awaitValue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate

class DietaViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val mainRule = MainDispatcherRule()

    // Martedì 17 novembre 2026, 08:00 (UTC): la settimana inizia lunedì 16.
    private val clock = MutableClock(Instant.parse("2026-11-17T08:00:00Z"))
    private val monday = LocalDate.of(2026, 11, 16)
    private val nextMonday = LocalDate.of(2026, 11, 23)
    private lateinit var f: AppFixture

    @Before
    fun setUp() {
        f = AppFixture(tmp.root, clock)
    }

    @After
    fun tearDown() = f.close()

    private fun newViewModel() = DietaViewModel(
        planRepository = f.planRepository,
        foodRepository = f.foodRepository,
        userProfileRepository = f.profileRepository,
        completionRepository = f.completionRepository,
        complianceCalculator = WeeklyComplianceCalculator(DietRuleEngine(), NutritionCalculator()),
        nutritionCalculator = NutritionCalculator(),
        clock = clock,
        savedStateHandle = SavedStateHandle(),
    )

    private suspend fun DietaViewModel.ready(predicate: (DietaContent) -> Boolean = { true }): DietaContent =
        (uiState.awaitValue { it is DietaUiState.Ready && predicate(it.content) } as DietaUiState.Ready).content

    @Test
    fun createsTheCurrentWeekWhenItIsMissing() = runBlocking {
        f.seedFoods()
        assertTrue(f.planDao.getAll().isEmpty())

        val vm = newViewModel()
        val content = vm.ready()

        assertEquals(monday.toEpochDay(), f.planDao.getAll().single().weekStartEpochDay)
        assertEquals(f.planDao.getAll().single().id, content.planId)
        assertEquals("oggi è martedì", 1, content.selectedDay)
        assertEquals(1, content.days.single { it.isToday }.dayIndex)
    }

    @Test
    fun waitsForTheFoodCatalogueThenRecovers() = runBlocking {
        val vm = newViewModel()
        assertTrue(vm.uiState.awaitValue { true } is DietaUiState.Loading)
        assertTrue(f.planDao.getAll().isEmpty())

        f.seedFoods()

        vm.ready()
        assertEquals(1, f.planDao.getAll().size)
    }

    @Test
    fun recoversWhenThePlanDisappearsAfterStartup() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        val first = vm.ready()

        // Il piano sparisce (es. ripristino di un backup senza la settimana corrente).
        f.planDao.deleteAll()
        f.mealDao.deleteForPlan(first.planId)

        val recovered = vm.ready { it.planId != first.planId }
        assertNotEquals(first.planId, recovered.planId)
        assertEquals(35, f.mealDao.meals.value.size)
    }

    @Test
    fun restoringABackupWithoutTheCurrentWeekDoesNotLeaveDietaLoading() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        val before = vm.ready()

        // Backup contenente solo una settimana passata.
        val old = f.planRepository.getOrCreateWeek(monday.minusWeeks(3))
        val service = BackupService(
            f.planDao, f.mealDao, f.measurementDao, f.waterDao, f.checkDao, f.transactions, f.profileRepository,
            f.completionRepository, f.extrasRepository, clock, "1.0.1",
        )
        val file: BackupFile = service.buildBackup().let { b -> b.copy(weeklyPlans = b.weeklyPlans.filter { it.id == old.id }) }
        assertTrue(file.weeklyPlans.none { it.weekStartEpochDay == monday.toEpochDay() })

        service.restore(file) // nessun hook: ci pensa il ViewModel

        val recovered = vm.ready { it.planId != before.planId }
        assertNotNull(f.planDao.getByWeekStart(monday.toEpochDay()))
        assertEquals(f.planDao.getByWeekStart(monday.toEpochDay())!!.id, recovered.planId)
    }

    @Test
    fun newWeekAfterMidnightIsLoadedOnRefresh() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        val thisWeek = vm.ready()

        // Domenica sera → lunedì mattina: senza refresh la schermata resta sulla settimana vecchia.
        clock.current = Instant.parse("2026-11-23T00:30:00Z")
        assertEquals(thisWeek.planId, vm.ready().planId)

        vm.refreshToday()

        val next = vm.ready { it.planId != thisWeek.planId }
        assertEquals(nextMonday.toEpochDay(), f.planDao.getAll().single { it.id == next.planId }.weekStartEpochDay)
        assertEquals("il giorno selezionato torna a oggi (lunedì)", 0, next.selectedDay)
        assertEquals(0, next.days.single { it.isToday }.dayIndex)
        assertEquals("la settimana vecchia resta nel database", 2, f.planDao.getAll().size)
    }

    @Test
    fun dayChangeInsideTheSameWeekMovesTodayWithoutANewPlan() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        val tuesday = vm.ready()
        assertEquals(1, tuesday.days.single { it.isToday }.dayIndex)

        clock.current = Instant.parse("2026-11-18T00:05:00Z")
        vm.refreshToday()

        val wednesday = vm.ready { c -> c.days.single { it.isToday }.dayIndex == 2 }
        assertEquals(tuesday.planId, wednesday.planId)
        assertEquals(2, wednesday.selectedDay)
        assertEquals(1, f.planDao.getAll().size)
    }

    @Test
    fun refreshWithoutDayChangeKeepsTheSelectedDay() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        vm.ready()
        vm.selectDay(5)
        assertEquals(5, vm.ready { it.selectedDay == 5 }.selectedDay)

        vm.refreshToday()

        assertEquals(5, vm.ready().selectedDay)
    }

    @Test
    fun retryIsIdempotentWhenTheWeekAlreadyExists() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        val first = vm.ready()

        vm.ensurePlan()

        assertEquals(first.planId, vm.ready().planId)
        awaitUntil { f.planDao.getAll().size == 1 }
        assertEquals(35, f.mealDao.meals.value.size)
    }

    @Test
    fun completionTicksAreKeptPerMealAndTriggerTheBackupHook() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        val content = vm.ready()
        val meal = content.meals.first()
        val before = f.changes

        vm.setCompleted(meal.mealId, true)

        assertTrue(vm.ready { c -> c.meals.first { it.mealId == meal.mealId }.isCompleted }.meals.first { it.mealId == meal.mealId }.isCompleted)
        awaitUntil { f.changes > before }
    }
}
