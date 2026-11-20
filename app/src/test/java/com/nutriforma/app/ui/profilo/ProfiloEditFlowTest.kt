package com.nutriforma.app.ui.profilo

import com.nutriforma.app.data.AppFixture
import com.nutriforma.app.data.MutableClock
import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import com.nutriforma.app.domain.model.BodyMeasurement
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.nutrition.BmiCalculator
import com.nutriforma.app.domain.nutrition.BmiCategory
import com.nutriforma.app.domain.nutrition.EnergyTargetCalculator
import com.nutriforma.app.ui.MainDispatcherRule
import com.nutriforma.app.ui.awaitValue
import com.nutriforma.app.ui.nextEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate

/** Scheda "Dati personali": bloccata dopo il salvataggio, "Modifica" → "Salva" / "Annulla", peso condiviso con Progressi. */
class ProfiloEditFlowTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-11-17T08:00:00Z"))
    private val today = LocalDate.of(2026, 11, 17)
    private val restoreCount = MutableStateFlow(0)
    private lateinit var f: AppFixture
    private lateinit var vm: ProfiloViewModel

    @Before
    fun setUp() {
        f = AppFixture(tmp.root, clock)
        vm = ProfiloViewModel(
            userProfileRepository = f.profileRepository, planRepository = f.planRepository,
            trackingRepository = f.trackingRepository, energyTargetCalculator = EnergyTargetCalculator(),
            restoreCount = restoreCount, clock = clock,
        )
    }

    @After
    fun tearDown() = f.close()

    private fun fillAll() {
        vm.onFirstNameChange("Giulia")
        vm.onLastNameChange("Rossi")
        vm.onSessoChange(Sesso.FEMMINA)
        vm.onBirthYearChange("1990")
        vm.onHeightChange("168")
        vm.onWeightChange("62,5")
    }

    private suspend fun saveEverything() {
        fillAll()
        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())
    }

    private suspend fun storedMeasurements() = f.trackingRepository.observeMeasurements().first()

    // ---- Primo avvio: subito in modifica ---------------------------------------------------------------------

    @Test
    fun withNothingSavedTheCardOpensInEditMode() = runBlocking {
        val state = vm.uiState.awaitValue { true }
        assertFalse(state.hasSavedPersonalData)
        assertTrue("senza dati salvati si parte in modifica", state.isEditing)
        assertNull(state.bmi)
    }

    @Test
    fun cancelWithNothingSavedJustClearsTheDraft() = runBlocking {
        vm.onFirstNameChange("Giulia")
        assertTrue(vm.uiState.awaitValue { it.firstNameText == "Giulia" }.personalDirty)

        vm.cancelEdit()

        val state = vm.uiState.awaitValue { it.firstNameText.isEmpty() }
        assertTrue("non c'è nulla a cui tornare: resta in modifica", state.isEditing)
        assertFalse(state.personalDirty)
    }

    // ---- Salva → bloccata ------------------------------------------------------------------------------------

    @Test
    fun savingLocksTheCardAndShowsTheSavedValues() = runBlocking {
        saveEverything()

        val state = vm.uiState.awaitValue { it.hasSavedPersonalData && !it.isEditing }
        assertEquals("Giulia", state.firstName)
        assertEquals("Rossi", state.lastName)
        assertEquals(Sesso.FEMMINA, state.sesso)
        assertEquals(1990, state.birthYear)
        assertEquals(2026 - 1990, state.ageYears)
        assertEquals(168, state.heightCm)
        assertEquals(62.5, state.weightKg!!, 0.0001)
        assertFalse(state.personalDirty)

        val profile = f.profileRepository.current()
        assertEquals("Giulia", profile.firstName)
        assertEquals("Rossi", profile.lastName)
        assertEquals(168, profile.heightCm)
    }

    @Test
    fun namesAreTrimmedWhenSaved() = runBlocking {
        vm.onFirstNameChange("  Anna ")
        vm.onLastNameChange("De Luca  ")
        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())
        assertEquals("Anna", f.profileRepository.current().firstName)
        assertEquals("De Luca", f.profileRepository.current().lastName)
    }

    @Test
    fun nameLengthIsBoundedAndControlCharactersAreDropped() = runBlocking {
        vm.onFirstNameChange("A".repeat(MAX_NAME_LENGTH + 20))
        assertEquals(MAX_NAME_LENGTH, vm.uiState.awaitValue { it.firstNameText.isNotEmpty() }.firstNameText.length)
        vm.onLastNameChange("Ro\nssi\t")
        assertEquals("Rossi", vm.uiState.awaitValue { it.lastNameText.isNotEmpty() }.lastNameText)
    }

    // ---- Modifica → Annulla / Salva --------------------------------------------------------------------------

    @Test
    fun modificaOpensEditingFromTheSavedValuesAndAnnullaDiscardsTheDraft() = runBlocking {
        saveEverything()
        assertFalse(vm.uiState.awaitValue { it.hasSavedPersonalData && !it.isEditing }.isEditing)

        vm.startEdit()
        val editing = vm.uiState.awaitValue { it.isEditing }
        assertEquals("i campi partono dai valori salvati", "168", editing.heightText)
        assertEquals("62,5", editing.weightText)
        assertFalse(editing.personalDirty)

        vm.onHeightChange("175")
        vm.onWeightChange("70")
        vm.onFirstNameChange("Altro")
        assertTrue(vm.uiState.awaitValue { it.heightText == "175" && it.weightText == "70" && it.firstNameText == "Altro" }.personalDirty)

        vm.cancelEdit()

        val locked = vm.uiState.awaitValue { !it.isEditing }
        assertEquals("168", locked.heightText)
        assertEquals("62,5", locked.weightText)
        assertEquals("Giulia", locked.firstNameText)
        assertFalse(locked.personalDirty)
        assertEquals("niente è stato salvato", 168, f.profileRepository.current().heightCm)
        assertEquals(62.5, storedMeasurements().single().weightKg!!, 0.0001)
    }

    @Test
    fun editingTwiceStartsFromAFreshDraft() = runBlocking {
        saveEverything()
        vm.startEdit()
        vm.onHeightChange("175")
        vm.uiState.awaitValue { it.heightText == "175" }

        vm.startEdit() // di nuovo "Modifica": la bozza precedente non resta appesa

        assertEquals("168", vm.uiState.awaitValue { it.heightText == "168" }.heightText)
    }

    @Test
    fun savingAnEditLocksTheCardAgainAndConfirms() = runBlocking {
        saveEverything()
        vm.startEdit()
        vm.onHeightChange("170")

        vm.savePersonal()

        assertEquals("Dati personali salvati", vm.events.nextEvent())
        val state = vm.uiState.awaitValue { !it.isEditing && it.heightCm == 170 }
        assertEquals("170", state.heightText)
        assertFalse(state.personalDirty)
        assertEquals(170, f.profileRepository.current().heightCm)
    }

    @Test
    fun savingWithoutChangesSaysSoAndLocksTheCard() = runBlocking {
        saveEverything()
        vm.startEdit()
        vm.uiState.awaitValue { it.isEditing }

        vm.savePersonal()

        assertEquals("Nessuna modifica da salvare.", vm.events.nextEvent())
        assertFalse(vm.uiState.awaitValue { !it.isEditing }.isEditing)
    }

    @Test
    fun editModeAndDraftSurviveLeavingTheScreen() = runBlocking {
        saveEverything()
        vm.startEdit()
        vm.onHeightChange("171")
        vm.uiState.awaitValue { it.isEditing && it.heightText == "171" }

        // Nessuno collega la schermata (si è andati in Impostazioni): lo stato vive nel ViewModel.
        val back = vm.uiState.awaitValue { it.isEditing && it.heightText == "171" }
        assertTrue(back.personalDirty)

        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())
        assertEquals(171, f.profileRepository.current().heightCm)
    }

    @Test
    fun aRestoreDiscardsTheOpenDraftAndLocksTheCard() = runBlocking {
        saveEverything()
        vm.startEdit()
        vm.onHeightChange("199")
        vm.uiState.awaitValue { it.isEditing && it.heightText == "199" }

        restoreCount.value = 1 // un ripristino da Impostazioni sostituisce i dati salvati

        val state = vm.uiState.awaitValue { !it.isEditing }
        assertEquals("168", state.heightText)
        assertFalse(state.personalDirty)
    }

    // ---- Peso: stessa fonte di Progressi ---------------------------------------------------------------------

    @Test
    fun savedWeightBecomesTodaysMeasurementKeepingWaistThighAndNote() = runBlocking {
        f.measurementDao.upsert(BodyMeasurementEntity(today.toEpochDay(), 64.0, 80.0, 55.5, "dopo l'allenamento", 1))

        vm.onWeightChange("63,4")
        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())

        val row = storedMeasurements().single { it.date == today }
        assertEquals(63.4, row.weightKg!!, 0.0001)
        assertEquals(80.0, row.waistCm!!, 0.0001)
        assertEquals(55.5, row.thighCm!!, 0.0001)
        assertEquals("dopo l'allenamento", row.note)
        assertEquals(1, storedMeasurements().size)
    }

    @Test
    fun savedWeightFeedsEnergyTargetAndBmi() = runBlocking {
        f.profileRepository.update { it.copy(sesso = Sesso.MASCHIO, birthYear = 1990, heightCm = 180) }
        val missing = vm.uiState.awaitValue { it.heightCm == 180 }
        assertNull("senza peso niente kcal e niente IMC", missing.energyTarget)
        assertNull(missing.bmi)

        vm.startEdit()
        vm.onWeightChange("78")
        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())

        val state = vm.uiState.awaitValue { it.weightKg == 78.0 && it.energyTarget != null }
        assertEquals(EnergyTargetCalculator().calculate(f.profileRepository.current(), 78.0, 2026), state.energyTarget)
        assertEquals(BmiCalculator.calculate(78.0, 180), state.bmi)
        assertEquals(24.1, state.bmi!!.value, 0.0001)
        assertEquals(BmiCategory.NORMOPESO, state.bmi!!.category)
    }

    @Test
    fun aWeightLoggedInProgressiShowsUpInProfiloAndUpdatesTheBmi() = runBlocking {
        f.profileRepository.update { it.copy(heightCm = 180, sesso = Sesso.MASCHIO) }
        f.trackingRepository.saveMeasurement(BodyMeasurement(today, 90.0, null, null, null))

        val first = vm.uiState.awaitValue { it.weightKg == 90.0 }
        assertEquals(BmiCategory.SOVRAPPESO, first.bmi!!.category)

        f.trackingRepository.saveMeasurement(BodyMeasurement(today, 70.0, null, null, null))
        val second = vm.uiState.awaitValue { it.weightKg == 70.0 }
        assertEquals(BmiCategory.NORMOPESO, second.bmi!!.category)
    }

    @Test
    fun theLatestWeightByDateIsTheOneShown() = runBlocking {
        f.measurementDao.upsert(BodyMeasurementEntity(today.minusDays(10).toEpochDay(), 80.0, null, null, null, 1))
        f.measurementDao.upsert(BodyMeasurementEntity(today.minusDays(3).toEpochDay(), 78.0, 85.0, null, null, 1))
        f.measurementDao.upsert(BodyMeasurementEntity(today.minusDays(1).toEpochDay(), null, 84.0, null, null, 1))

        assertEquals("l'ultima riga senza peso non conta", 78.0, vm.uiState.awaitValue { it.weightKg != null }.weightKg!!, 0.0001)
    }

    @Test
    fun leavingWeightBlankKeepsTheStoredWeight() = runBlocking {
        f.measurementDao.upsert(BodyMeasurementEntity(today.minusDays(2).toEpochDay(), 78.0, null, null, null, 1))
        vm.onWeightChange("")
        vm.onHeightChange("180")
        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())

        val rows = storedMeasurements()
        assertEquals("nessuna misura nuova", 1, rows.size)
        assertEquals(78.0, rows.single().weightKg!!, 0.0001)
    }

    @Test
    fun anUnchangedWeightDoesNotWriteANewMeasurement() = runBlocking {
        f.measurementDao.upsert(BodyMeasurementEntity(today.minusDays(2).toEpochDay(), 78.0, null, null, null, 1))
        vm.uiState.awaitValue { it.weightKg == 78.0 }
        val before = f.changes

        vm.onHeightChange("180")
        vm.onWeightChange("78,0") // stesso valore scritto diversamente
        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())

        assertEquals(1, storedMeasurements().size)
        assertNull(storedMeasurements().firstOrNull { it.date == today })
        assertEquals("solo il profilo è cambiato", before + 1, f.changes)
    }

    @Test
    fun invalidWeightBlocksTheWholeSave() = runBlocking {
        vm.onFirstNameChange("Giulia")
        vm.onHeightChange("168")
        vm.onWeightChange("20")

        vm.savePersonal()

        val message = vm.events.nextEvent()
        assertTrue(message, message.startsWith("Peso non valido (tra 30 e 250 kg)."))
        val state = vm.uiState.awaitValue { it.weightError != null }
        assertNotNull(state.weightError)
        assertTrue(state.personalDirty)
        assertEquals("niente salvataggio parziale", "", f.profileRepository.current().firstName)
        assertNull(f.profileRepository.current().heightCm)
        assertTrue(storedMeasurements().isEmpty())

        vm.onWeightChange("62")
        vm.uiState.awaitValue { it.weightError == null && it.weightText == "62" }
        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())
        assertEquals(62.0, storedMeasurements().single().weightKg!!, 0.0001)
    }

    @Test
    fun weightInputAcceptsCommaOrDotAndSingleSeparator() = runBlocking {
        vm.onWeightChange("7a8,5,5x")
        assertEquals("78,55", vm.uiState.awaitValue { it.weightText.isNotEmpty() }.weightText)
        vm.onWeightChange("78.5")
        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())
        assertEquals(78.5, storedMeasurements().single().weightKg!!, 0.0001)
    }
}
