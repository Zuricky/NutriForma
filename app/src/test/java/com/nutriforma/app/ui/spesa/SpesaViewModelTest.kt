package com.nutriforma.app.ui.spesa

import com.nutriforma.app.data.AppFixture
import com.nutriforma.app.data.MutableClock
import com.nutriforma.app.domain.shopping.ShoppingListGenerator
import com.nutriforma.app.ui.MainDispatcherRule
import com.nutriforma.app.ui.awaitUntil
import com.nutriforma.app.ui.awaitValue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate

class SpesaViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val mainRule = MainDispatcherRule()

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

    private fun newViewModel() = SpesaViewModel(
        planRepository = f.planRepository,
        foodRepository = f.foodRepository,
        shoppingRepository = f.shoppingRepository,
        extrasRepository = f.extrasRepository,
        generator = ShoppingListGenerator(),
        clock = clock,
    )

    private suspend fun SpesaViewModel.ready(predicate: (SpesaUiState.Ready) -> Boolean = { true }): SpesaUiState.Ready =
        uiState.awaitValue { it is SpesaUiState.Ready && predicate(it) } as SpesaUiState.Ready

    private fun SpesaUiState.Ready.keys(purchased: Boolean = false): List<String> =
        (if (purchased) content.purchased else content.toBuy).flatMap { s -> s.rows.map { it.key } }

    @Test
    fun spesaOpenedBeforeDietaCreatesTheWeek() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()

        val state = vm.ready()

        assertEquals(monday.toEpochDay(), f.planDao.getAll().single().weekStartEpochDay)
        assertTrue(state.content.toBuyCount > 0)
        assertEquals(setOf(1), state.selectedDays)
    }

    @Test
    fun recoversWhenThePlanDisappears() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        vm.ready()
        val oldId = f.planDao.getAll().single().id

        f.planDao.deleteAll()
        f.mealDao.deleteForPlan(oldId)

        awaitUntil { f.planDao.getByWeekStart(monday.toEpochDay())?.id?.let { it != oldId } == true }
        assertTrue(vm.ready().content.toBuyCount > 0)
    }

    @Test
    fun staysLoadingOnlyUntilTheCatalogueIsSeeded() = runBlocking {
        val vm = newViewModel()
        assertTrue(vm.uiState.awaitValue { true } is SpesaUiState.Loading)

        f.seedFoods()

        assertTrue(vm.ready().content.toBuyCount > 0)
    }

    @Test
    fun newWeekAfterMidnightStartsFromACleanList() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        val thisWeek = vm.ready()
        val food = thisWeek.keys().first { it.startsWith(FOOD_KEY_PREFIX) }
        vm.setChecked(food, true)
        vm.ready { food in it.keys(purchased = true) }
        vm.addExtra("Detersivo")
        vm.ready { s -> (s.keys() + s.keys(purchased = true)).any { it.startsWith(EXTRA_KEY_PREFIX) } }

        clock.current = Instant.parse("2026-11-23T00:10:00Z")
        vm.refreshToday()

        val next = vm.ready { food !in it.keys(purchased = true) }
        assertNotNull(f.planDao.getByWeekStart(nextMonday.toEpochDay()))
        assertEquals("settimana nuova = nessuna spunta", emptyList<String>(), next.keys(purchased = true).filter { it.startsWith(FOOD_KEY_PREFIX) })
        assertEquals("il filtro giorni riparte da oggi (lunedì)", setOf(0), next.selectedDays)
        assertTrue("gli extra manuali non dipendono dalla settimana", (next.keys() + next.keys(purchased = true)).any { it.startsWith(EXTRA_KEY_PREFIX) })
    }

    @Test
    fun withoutRefreshTheOldWeekIsKept() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        vm.ready()
        clock.current = Instant.parse("2026-11-23T00:10:00Z")
        assertEquals(1, f.planDao.getAll().size)
        assertFalse(f.planDao.getAll().any { it.weekStartEpochDay == nextMonday.toEpochDay() })
        vm.refreshToday()
        awaitUntil { f.planDao.getByWeekStart(nextMonday.toEpochDay()) != null }
    }

    @Test
    fun shoppingMutationsTriggerTheBackupHook() = runBlocking {
        f.seedFoods()
        val vm = newViewModel()
        val state = vm.ready()
        val food = state.keys().first { it.startsWith(FOOD_KEY_PREFIX) }

        val c0 = f.changes
        vm.setChecked(food, true)
        vm.ready { food in it.keys(purchased = true) }
        awaitUntil { f.changes > c0 }

        val c1 = f.changes
        vm.addExtra("Sale")
        awaitUntil { f.extrasRepository.current().isNotEmpty() }
        awaitUntil { f.changes > c1 }

        val extraKey = EXTRA_KEY_PREFIX + f.extrasRepository.current().single().id
        val c2 = f.changes
        vm.setChecked(extraKey, true)
        awaitUntil { f.extrasRepository.current().single().isChecked }
        awaitUntil { f.changes > c2 }
    }
}
