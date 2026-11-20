package com.nutriforma.app.ui.progressi

import com.nutriforma.app.data.AppFixture
import com.nutriforma.app.data.MutableClock
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.tips.DailyTips
import com.nutriforma.app.domain.tips.MotivationTrigger
import com.nutriforma.app.domain.tracking.MeasurementMetric
import com.nutriforma.app.ui.MainDispatcherRule
import com.nutriforma.app.ui.awaitUntil
import com.nutriforma.app.ui.awaitValue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate

class ProgressiViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-11-17T20:00:00Z"))
    private lateinit var f: AppFixture

    @Before
    fun setUp() {
        f = AppFixture(tmp.root, clock)
    }

    @After
    fun tearDown() = f.close()

    private fun newViewModel() = ProgressiViewModel(f.trackingRepository, f.profileRepository, clock)

    @Test
    fun waterAndTipRestartFromTheNewDayAfterRefresh() = runBlocking {
        val vm = newViewModel()
        vm.addWater(500)
        val day1 = vm.uiState.awaitValue { it.waterMl == 500 }
        assertEquals(LocalDate.of(2026, 11, 17), day1.today)
        assertEquals(DailyTips.tipFor(LocalDate.of(2026, 11, 17)), day1.tip)

        // Passa la mezzanotte: senza refresh si continuerebbe a leggere (e scrivere) sul giorno vecchio.
        clock.current = Instant.parse("2026-11-18T00:30:00Z")
        vm.refreshToday()

        val day2 = vm.uiState.awaitValue { it.today == LocalDate.of(2026, 11, 18) }
        assertEquals("acqua di oggi ripartita da zero", 0, day2.waterMl)
        assertEquals(DailyTips.tipFor(LocalDate.of(2026, 11, 18)), day2.tip)
        assertNotEquals(day1.tip, day2.tip)

        vm.addWater(250)
        assertEquals(250, vm.uiState.awaitValue { it.waterMl == 250 }.waterMl)
        assertEquals("il giorno prima non cambia", 500, f.waterDao.getAll().single { it.dateEpochDay == LocalDate.of(2026, 11, 17).toEpochDay() }.amountMl)
    }

    @Test
    fun refreshWithoutDayChangeKeepsTheState() = runBlocking {
        val vm = newViewModel()
        vm.addWater(250)
        vm.uiState.awaitValue { it.waterMl == 250 }
        vm.refreshToday()
        val state = vm.uiState.awaitValue { it.waterMl == 250 }
        assertEquals(LocalDate.of(2026, 11, 17), state.today)
    }

    @Test
    fun waterComplimentAppearsOnlyOnceTheGoalIsReachedAndIsStable() = runBlocking {
        val vm = newViewModel()
        vm.addWater(500)
        assertNull(vm.uiState.awaitValue { it.waterMl == 500 }.waterCompliment)

        vm.addWater(1000)
        vm.addWater(250)
        val reached = vm.uiState.awaitValue { it.waterMl == 1750 }
        assertEquals(MotivationTrigger.WATER_GOAL, reached.waterCompliment!!.trigger)

        // Nuove letture dello stato non cambiano la frase (niente sfarfallio).
        vm.addWater(250)
        assertEquals(reached.waterCompliment, vm.uiState.awaitValue { it.waterMl == 2000 }.waterCompliment)

        vm.subtractWater(250)
        vm.subtractWater(250)
        assertNull("sotto l'obiettivo il complimento sparisce", vm.uiState.awaitValue { it.waterMl == 1500 }.waterCompliment)
    }

    @Test
    fun goalProgressComplimentFollowsTheProfileGoalAndTheMeasurements() = runBlocking {
        val vm = newViewModel()
        val today = LocalDate.of(2026, 11, 17)
        f.addWeight(today.minusDays(20).toEpochDay(), 82.0)
        f.addWeight(today.toEpochDay(), 80.0)

        // Equilibrio: ventidue giorni, due chili in meno = non stabile e solo 2 misure → nessun complimento.
        assertNull(vm.uiState.awaitValue { it.series[MeasurementMetric.PESO]?.size == 2 }.progressCompliment)

        f.profileRepository.update { it.copy(goal = Goal.DIMAGRIMENTO) }
        val loss = vm.uiState.awaitValue { it.progressCompliment != null }.progressCompliment!!
        assertEquals(MotivationTrigger.GOAL_PROGRESS, loss.trigger)
        assertEquals("−2,0 kg dall'inizio", loss.detail)
    }

    @Test
    fun waterLogsTriggerTheBackupHook() = runBlocking {
        val vm = newViewModel()
        val before = f.changes
        vm.addWater(250)
        awaitUntil { f.changes > before }
        vm.subtractWater(250)
        vm.uiState.awaitValue { it.waterMl == 0 }
        awaitUntil { f.changes >= before + 2 }
    }
}
