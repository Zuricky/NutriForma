package com.nutriforma.app.data.repository

import com.nutriforma.app.data.AppFixture
import com.nutriforma.app.data.MutableClock
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate

/** Ogni modifica dell'utente deve far scattare la notifica che avvia il backup automatico (con debounce a valle). */
class AutoBackupTriggersTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = MutableClock(Instant.parse("2026-11-17T08:00:00Z"))
    private val monday = LocalDate.of(2026, 11, 16)
    private lateinit var f: AppFixture

    @Before
    fun setUp() = runBlocking {
        f = AppFixture(tmp.root, clock)
        f.seedFoods()
    }

    @After
    fun tearDown() = f.close()

    /** Esegue [action] e verifica che la notifica sia scattata almeno una volta. */
    private suspend fun assertTriggers(what: String, action: suspend () -> Unit) {
        val before = f.changes
        action()
        assertTrue("$what deve avviare il backup automatico", f.changes > before)
    }

    private suspend fun assertSilent(what: String, action: suspend () -> Unit) {
        val before = f.changes
        action()
        assertEquals("$what non deve avviare il backup automatico", before, f.changes)
    }

    @Test
    fun weekCreationTriggersOnlyTheFirstTime() = runBlocking {
        assertTriggers("creare la settimana") { f.planRepository.getOrCreateWeek(monday) }
        assertSilent("rileggere la settimana") { f.planRepository.getOrCreateWeek(monday) }
    }

    @Test
    fun planMutationsTrigger() = runBlocking {
        val plan = f.planRepository.getOrCreateWeek(monday)
        val lunch = plan.meals.first { it.dayIndex == 0 && it.slot == MealSlot.PRANZO }
        val alternative = f.planRepository.swapAlternatives(lunch.id).first { it.allowed }

        assertSilent("cercare alternative") { f.planRepository.swapAlternatives(lunch.id) }
        assertTriggers("cambiare un pasto") { f.planRepository.applyAlternative(lunch.id, alternative.components) }

        val saturdayLunch = plan.meals.first { it.dayIndex == 5 && it.slot == MealSlot.PRANZO }
        assertTriggers("togliere il pasto libero") { f.planRepository.setPranzoLibero(saturdayLunch.id, false) }

        assertTriggers("attivare il turno lungo") {
            f.planRepository.setTurnoLungo(plan.id, 2, TurnoLungoDay(2, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_POMERIGGIO))
        }
        assertTriggers("ricalcolare le porzioni") { f.planRepository.recalculatePortions(plan.id) }
        assertTriggers("rigenerare la settimana") { f.planRepository.regenerateWeek(plan.id, newSeed = 99L) }
    }

    @Test
    fun profileChangesTriggerButDeviceBookkeepingDoesNot() = runBlocking {
        assertTriggers("cambiare obiettivo") { f.profileRepository.update { it.copy(goal = Goal.ENERGIA) } }
        assertSilent("segnare l'ultimo backup") { f.profileRepository.updateSilently { it.copy(lastBackupAtMillis = 5L) } }
        assertSilent("aggiornare la versione del catalogo") { f.profileRepository.updateSilently { it.copy(foodSeedVersion = 3) } }
    }

    @Test
    fun waterLogsTrigger() = runBlocking {
        assertTriggers("aggiungere acqua") { f.trackingRepository.addWater(250) }
        assertTriggers("togliere acqua") { f.trackingRepository.subtractWater(250) }
        assertSilent("togliere acqua da zero") { f.trackingRepository.subtractWater(250) }
    }

    @Test
    fun shoppingChecksTrigger() = runBlocking {
        val plan = f.planRepository.getOrCreateWeek(monday)
        assertTriggers("spuntare un alimento") { f.shoppingRepository.setChecked(plan.id, "uovo", true) }
        assertTriggers("azzerare le spunte") { f.shoppingRepository.clearChecks(plan.id) }
    }

    @Test
    fun mealCompletionsTriggerButRestoreWritesDoNot() = runBlocking {
        assertTriggers("completare un pasto") { f.completionRepository.setCompleted(3, true) }
        assertSilent("ripristinare le spunte") { f.completionRepository.replaceAll(setOf(1L, 2L)) }
        assertEquals(setOf(1L, 2L), f.completionRepository.current())
    }

    @Test
    fun shoppingExtrasTriggerButRestoreWritesDoNot() = runBlocking {
        assertTriggers("aggiungere un extra") { f.extrasRepository.add("Sale grosso") }
        val id = f.extrasRepository.current().single().id
        assertTriggers("spuntare un extra") { f.extrasRepository.setChecked(id, true) }
        assertTriggers("rimuovere un extra") { f.extrasRepository.remove(id) }
        f.extrasRepository.add("Lievito")
        assertTriggers("svuotare gli extra") { f.extrasRepository.clear() }
        assertSilent("aggiungere un extra vuoto") { f.extrasRepository.add("   ") }
        assertSilent("ripristinare gli extra") { f.extrasRepository.replaceAll(listOf(ShoppingExtra("x", "Altro"))) }
    }

    @Test
    fun measurementsStillTrigger() = runBlocking {
        assertTriggers("salvare una misura") {
            f.trackingRepository.saveMeasurement(
                com.nutriforma.app.domain.model.BodyMeasurement(LocalDate.of(2026, 11, 17), 78.0, null, null, null),
            )
        }
        assertTriggers("eliminare una misura") { f.trackingRepository.deleteMeasurement(LocalDate.of(2026, 11, 17)) }
    }
}
