package com.nutriforma.app.ui.profilo

import com.nutriforma.app.data.AppFixture
import com.nutriforma.app.data.MutableClock
import com.nutriforma.app.data.backup.BackupException
import com.nutriforma.app.data.backup.BackupFile
import com.nutriforma.app.data.backup.BackupGateway
import com.nutriforma.app.data.backup.BackupWeeklyPlan
import com.nutriforma.app.data.backup.RestoreResult
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.nutrition.EnergyTargetCalculator
import com.nutriforma.app.notifications.ReminderSync
import com.nutriforma.app.ui.MainDispatcherRule
import com.nutriforma.app.ui.awaitUntil
import com.nutriforma.app.ui.awaitValue
import com.nutriforma.app.ui.nextEvent
import com.nutriforma.app.ui.promemoria.ReminderKind
import kotlinx.coroutines.flow.MutableStateFlow
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

private class FakeBackupGateway : BackupGateway {
    override val autoBackupError: MutableStateFlow<String?> = MutableStateFlow(null)
    var fileToRead: BackupFile = BackupFile()
    var readError: Exception? = null
    var restoreError: Exception? = null
    var restoreResult = RestoreResult()
    var exportError: Exception? = null
    var restored: BackupFile? = null
    var exportedTo: String? = null
    var folder: String? = null
    var folderError: Exception? = null
    var autoEnabled: Boolean? = null

    override fun suggestedFileName() = "NutriForma_backup_test.json"

    override suspend fun exportTo(target: String) {
        exportError?.let { throw it }
        exportedTo = target
    }

    override suspend fun readBackup(source: String): BackupFile {
        readError?.let { throw it }
        return fileToRead
    }

    override suspend fun restore(file: BackupFile): RestoreResult {
        restoreError?.let { throw it }
        restored = file
        return restoreResult
    }

    override suspend fun setAutoBackupFolder(tree: String) {
        folderError?.let { throw it }
        folder = tree
    }

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        autoEnabled = enabled
    }
}

private class FakeReminderSync : ReminderSync {
    @Volatile
    var syncs = 0
    override suspend fun syncWithProfile() {
        syncs++
    }
}

class ProfiloViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-11-17T08:00:00Z"))
    private val monday = LocalDate.of(2026, 11, 16)
    private lateinit var f: AppFixture
    private lateinit var backup: FakeBackupGateway
    private lateinit var reminders: FakeReminderSync
    private lateinit var vm: ProfiloViewModel

    @Before
    fun setUp() = runBlocking {
        f = AppFixture(tmp.root, clock)
        f.seedFoods()
        backup = FakeBackupGateway()
        reminders = FakeReminderSync()
        vm = ProfiloViewModel(
            userProfileRepository = f.profileRepository, planRepository = f.planRepository,
            trackingRepository = f.trackingRepository, energyTargetCalculator = EnergyTargetCalculator(),
            reminderSync = reminders, backup = backup, clock = clock,
        )
    }

    @After
    fun tearDown() = f.close()

    // ---- Dati personali: bozza + "Salva" --------------------------------------------------------------------

    @Test
    fun typingOnlyEditsTheDraftUntilSaveIsPressed() = runBlocking {
        vm.onBirthYearChange("1990")
        vm.onHeightChange("178")
        vm.onSessoChange(Sesso.MASCHIO)

        val state = vm.uiState.awaitValue { it.birthYearText == "1990" && it.heightText == "178" }
        assertTrue("senza Salva le modifiche risultano pendenti", state.personalDirty)
        assertEquals(Sesso.MASCHIO, state.sesso)
        assertNull("il valore salvato non cambia da solo", state.birthYear)
        assertNull(f.profileRepository.current().birthYear)
        assertNull(f.profileRepository.current().heightCm)
        assertNull(f.profileRepository.current().sesso)
    }

    @Test
    fun saveWritesAllPersonalFieldsAndConfirms() = runBlocking {
        vm.onBirthYearChange("1990")
        vm.onHeightChange("178")
        vm.onSessoChange(Sesso.FEMMINA)

        vm.savePersonal()

        assertEquals("Dati personali salvati", vm.events.nextEvent())
        val profile = f.profileRepository.current()
        assertEquals(1990, profile.birthYear)
        assertEquals(178, profile.heightCm)
        assertEquals(Sesso.FEMMINA, profile.sesso)
        val state = vm.uiState.awaitValue { !it.personalDirty && it.birthYear == 1990 }
        assertEquals("1990", state.birthYearText)
        assertEquals("178", state.heightText)
        assertNull(state.birthYearError)
    }

    @Test
    fun draftSurvivesWhileTheScreenIsAwayAndIsStillSavable() = runBlocking {
        vm.onHeightChange("181")
        // Nessuno collega la schermata (cambio scheda): la bozza vive nel ViewModel.
        vm.onBirthYearChange("1985")
        val back = vm.uiState.awaitValue { it.heightText == "181" && it.birthYearText == "1985" }
        assertTrue(back.personalDirty)

        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())
        assertEquals(181, f.profileRepository.current().heightCm)
    }

    @Test
    fun invalidYearBlocksTheWholeSaveAndShowsClearErrors() = runBlocking {
        vm.onBirthYearChange("1800")
        vm.onHeightChange("178")
        vm.onSessoChange(Sesso.MASCHIO)

        vm.savePersonal()

        val message = vm.events.nextEvent()
        assertTrue(message, message.startsWith("Anno di nascita non valido (tra 1920 e 2016)."))
        val state = vm.uiState.awaitValue { it.birthYearError != null }
        assertNull(state.heightError)
        assertTrue(state.personalDirty)
        assertNull("niente salvataggio parziale", f.profileRepository.current().heightCm)
        assertNull(f.profileRepository.current().sesso)

        // Correggendo il campo l'errore sparisce e il salvataggio riesce.
        vm.onBirthYearChange("1990")
        vm.uiState.awaitValue { it.birthYearError == null && it.birthYearText == "1990" }
        vm.savePersonal()
        assertEquals("Dati personali salvati", vm.events.nextEvent())
        assertEquals(1990, f.profileRepository.current().birthYear)
        assertEquals(178, f.profileRepository.current().heightCm)
    }

    @Test
    fun invalidHeightIsReported() = runBlocking {
        vm.onHeightChange("20")
        vm.savePersonal()
        val message = vm.events.nextEvent()
        assertTrue(message, message.startsWith("Altezza non valida (tra 100 e 230 cm)."))
        assertNotNull(vm.uiState.awaitValue { it.heightError != null }.heightError)
        assertNull(f.profileRepository.current().heightCm)
    }

    @Test
    fun blankFieldsClearTheStoredValues() = runBlocking {
        f.profileRepository.update { it.copy(birthYear = 1990, heightCm = 178) }
        vm.onBirthYearChange("")
        vm.onHeightChange("")
        assertTrue(vm.uiState.awaitValue { it.birthYearText.isEmpty() && it.heightText.isEmpty() && it.personalDirty }.personalDirty)

        vm.savePersonal()

        assertEquals("Dati personali salvati", vm.events.nextEvent())
        assertNull(f.profileRepository.current().birthYear)
        assertNull(f.profileRepository.current().heightCm)
    }

    @Test
    fun saveWithNothingChangedSaysSo() = runBlocking {
        vm.savePersonal()
        assertEquals("Nessuna modifica da salvare.", vm.events.nextEvent())
        assertFalse(vm.uiState.awaitValue { true }.personalDirty)
    }

    @Test
    fun typingBackTheStoredValueIsNotDirty() = runBlocking {
        f.profileRepository.update { it.copy(birthYear = 1990) }
        vm.onBirthYearChange("1991")
        assertTrue(vm.uiState.awaitValue { it.birthYearText == "1991" }.personalDirty)
        vm.onBirthYearChange("1990")
        assertFalse(vm.uiState.awaitValue { it.birthYearText == "1990" }.personalDirty)
    }

    @Test
    fun nonDigitsAreIgnoredAndLengthIsBounded() = runBlocking {
        vm.onBirthYearChange("19a9-0x55")
        vm.onHeightChange("1 7 8 9 9")
        val state = vm.uiState.awaitValue { it.birthYearText.isNotEmpty() }
        assertEquals("1990", state.birthYearText)
        assertEquals("178", vm.uiState.awaitValue { it.heightText.isNotEmpty() }.heightText)
    }

    // ---- Livello sportivo / obiettivo ------------------------------------------------------------------------

    private suspend fun prepareProfileWithKcal() {
        f.profileRepository.update { it.copy(sesso = Sesso.MASCHIO, birthYear = 1990, heightCm = 180) }
        f.addWeight(monday.toEpochDay(), 78.0)
    }

    private fun gramsOf(plan: com.nutriforma.app.domain.model.WeekPlan) = plan.meals.flatMap { it.components }.sumOf { it.grams }

    @Test
    fun changingSportLevelRescalesPortionsAndEnergyTarget() = runBlocking {
        prepareProfileWithKcal()
        val before = f.planRepository.getOrCreateWeek(monday) // MOLTO_INTENSO (x1,40)
        val kcalBefore = vm.uiState.awaitValue { it.energyTarget != null }.energyTarget!!

        vm.setSportLevel(SportLevel.SEDENTARIO)

        val message = vm.events.nextEvent()
        assertTrue(message, message.contains("Livello sportivo") && message.contains("Sedentario"))

        val after = f.planRepository.getOrCreateWeek(monday)
        assertEquals(SportLevel.SEDENTARIO, after.sportLevel)
        assertEquals(SportLevel.SEDENTARIO, f.profileRepository.current().sportLevel)
        assertTrue("le porzioni in grammi calano", gramsOf(after) < gramsOf(before))
        // stesso piano, stessi alimenti
        assertEquals(before.meals.map { m -> m.components.map { it.foodId } }, after.meals.map { m -> m.components.map { it.foodId } })

        // I grammi scalabili passano da x1,40 a x1,00 rispetto alla base: rapporto atteso sulle sole voci scalabili in grammi.
        val ratios = before.meals.flatMap { it.components }.zip(after.meals.flatMap { it.components })
            .filter { (b, a) -> b.unit == a.unit && b.unit == com.nutriforma.app.domain.model.PortionUnit.GRAMMI && b.grams != a.grams }
            .map { (b, a) -> a.grams / b.grams }
        assertTrue("almeno qualche voce è stata riscalata", ratios.isNotEmpty())
        assertTrue("nessuna voce cresce", ratios.all { it < 1.0 })
        assertEquals(1.0 / 1.4, ratios.average(), 0.03)

        val kcalAfter = vm.uiState.awaitValue { it.sportLevel == SportLevel.SEDENTARIO && it.energyTarget != null && it.energyTarget != kcalBefore }
            .energyTarget!!
        assertTrue("fabbisogno più basso", kcalAfter.kcal < kcalBefore.kcal)
        val expected = EnergyTargetCalculator().calculate(f.profileRepository.current(), 78.0, 2026)!!
        assertEquals(expected, kcalAfter)
        assertTrue("proteine per kg più basse", kcalAfter.proteinG < kcalBefore.proteinG)
    }

    @Test
    fun raisingTheSportLevelIncreasesPortionsAndKcal() = runBlocking {
        prepareProfileWithKcal()
        f.profileRepository.update { it.copy(sportLevel = SportLevel.SEDENTARIO) }
        val before = f.planRepository.getOrCreateWeek(monday)
        val kcalBefore = vm.uiState.awaitValue { it.energyTarget != null }.energyTarget!!

        vm.setSportLevel(SportLevel.INTENSO)
        vm.events.nextEvent()

        val after = f.planRepository.getOrCreateWeek(monday)
        assertTrue(gramsOf(after) > gramsOf(before))
        assertTrue(vm.uiState.awaitValue { it.sportLevel == SportLevel.INTENSO && it.energyTarget != kcalBefore }.energyTarget!!.kcal > kcalBefore.kcal)
    }

    @Test
    fun changingGoalRecalculatesAndConfirms() = runBlocking {
        val before = f.planRepository.getOrCreateWeek(monday)

        vm.setGoal(Goal.DIMAGRIMENTO)

        val message = vm.events.nextEvent()
        assertTrue(message, message.contains("Obiettivo") && message.contains("Dimagrimento"))
        val after = f.planRepository.getOrCreateWeek(monday)
        assertEquals(Goal.DIMAGRIMENTO, after.goal)
        assertTrue(gramsOf(after) < gramsOf(before))
    }

    @Test
    fun changeCreatesTheCurrentWeekWhenItIsMissing() = runBlocking {
        assertTrue(f.planDao.getAll().isEmpty())

        vm.setSportLevel(SportLevel.LEGGERO)
        vm.events.nextEvent()

        val plan = f.planDao.getByWeekStart(monday.toEpochDay())
        assertNotNull(plan)
        assertEquals(SportLevel.LEGGERO, plan!!.sportLevel)
    }

    @Test
    fun failedRecalculationRestoresTheChoiceAndReportsTheError() = runBlocking {
        val before = f.planRepository.getOrCreateWeek(monday)
        f.foodDao.clear() // il ricalcolo richiede il catalogo alimenti

        vm.setSportLevel(SportLevel.SEDENTARIO)

        val message = vm.events.nextEvent()
        assertTrue(message, message.contains("Non riesco ad aggiornare le porzioni"))
        assertTrue(message, message.contains("mantenuto la scelta precedente"))
        assertEquals("profilo e piano restano coerenti", SportLevel.MOLTO_INTENSO, f.profileRepository.current().sportLevel)
        val after = f.planRepository.getOrCreateWeek(monday)
        assertEquals(before, after)
        assertEquals(SportLevel.MOLTO_INTENSO, vm.uiState.awaitValue { true }.sportLevel)
    }

    @Test
    fun failedGoalChangeAlsoRestoresOnlyTheGoal() = runBlocking {
        f.planRepository.getOrCreateWeek(monday)
        f.profileRepository.update { it.copy(sportLevel = SportLevel.MODERATO) }
        f.foodDao.clear()

        vm.setGoal(Goal.ENERGIA)

        assertTrue(vm.events.nextEvent().contains("mantenuto la scelta precedente"))
        val profile = f.profileRepository.current()
        assertEquals(Goal.EQUILIBRIO, profile.goal)
        assertEquals(SportLevel.MODERATO, profile.sportLevel)
    }

    @Test
    fun selectingTheCurrentLevelAgainDoesNothing() = runBlocking {
        f.planRepository.getOrCreateWeek(monday)
        val changes = f.changes
        vm.setSportLevel(SportLevel.MOLTO_INTENSO)
        vm.setGoal(Goal.EQUILIBRIO)
        // Le modifiche sono in coda una dopo l'altra: quando arriva questo messaggio le due precedenti sono già state esaminate.
        vm.setSportLevel(SportLevel.LEGGERO)
        vm.events.nextEvent()
        assertEquals("solo l'ultima richiesta cambia davvero qualcosa (profilo + piano)", changes + 2, f.changes)
    }

    @Test
    fun consecutiveChangesAreAppliedInOrder() = runBlocking {
        f.planRepository.getOrCreateWeek(monday)
        vm.setSportLevel(SportLevel.LEGGERO)
        vm.setSportLevel(SportLevel.MODERATO)
        vm.events.nextEvent()
        vm.events.nextEvent()
        assertEquals(SportLevel.MODERATO, f.profileRepository.current().sportLevel)
        assertEquals(SportLevel.MODERATO, f.planRepository.getOrCreateWeek(monday).sportLevel)
    }

    @Test
    fun cheatMealsMessageExplainsWhenItApplies() = runBlocking {
        vm.setCheatMeals(2)
        val message = vm.events.nextEvent()
        assertTrue(message, message.contains("prossima settimana"))
        assertEquals(2, f.profileRepository.current().cheatMealsPerWeek)
    }

    // ---- Promemoria ------------------------------------------------------------------------------------------

    @Test
    fun dailyTipSwitchSavesAndResyncsTheReminders() = runBlocking {
        vm.setReminder(ReminderKind.TIP, false)
        awaitUntil { reminders.syncs == 1 }
        assertFalse(f.profileRepository.current().reminders.dailyTipEnabled)

        vm.setReminder(ReminderKind.TIP, true)
        awaitUntil { reminders.syncs == 2 }
        assertTrue(f.profileRepository.current().reminders.dailyTipEnabled)
        assertFalse("gli altri promemoria restano spenti", f.profileRepository.current().reminders.waterRemindersEnabled)
    }

    // ---- Backup / ripristino ---------------------------------------------------------------------------------

    @Test
    fun exportShowsSuccessInSnackbarAndStatus() = runBlocking {
        vm.exportBackup("content://doc/1")
        assertEquals("Backup esportato", vm.events.nextEvent())
        assertEquals("content://doc/1", backup.exportedTo)
        val status = vm.uiState.awaitValue { it.backupStatus != null && !it.busy }.backupStatus!!
        assertEquals(BackupStatus("Backup esportato", isError = false), status)
    }

    @Test
    fun exportFailureIsAlwaysReported() = runBlocking {
        backup.exportError = BackupException("Non riesco a scrivere il file di backup.")
        vm.exportBackup("content://doc/1")
        assertEquals("Non riesco a scrivere il file di backup.", vm.events.nextEvent())
        assertTrue(vm.uiState.awaitValue { it.backupStatus?.isError == true }.backupStatus!!.message.contains("scrivere"))

        backup.exportError = IllegalStateException("boom")
        vm.exportBackup("content://doc/1")
        assertEquals("Esportazione non riuscita.", vm.events.nextEvent())
    }

    @Test
    fun cancelledPickersDoNothing() = runBlocking {
        vm.exportBackup(null)
        vm.importPicked(null)
        vm.chooseAutoBackupFolder(null)
        val state = vm.uiState.awaitValue { true }
        assertNull(state.backupStatus)
        assertFalse(state.busy)
        assertNull(backup.exportedTo)
    }

    @Test
    fun pickedFileAsksForConfirmationThenRestoresAndReports() = runBlocking {
        backup.fileToRead = BackupFile(
            exportedAtMillis = 1_794_000_000_000,
            weeklyPlans = listOf(BackupWeeklyPlan(1, 20773, 1, Goal.EQUILIBRIO, SportLevel.MODERATO, 1)),
        )
        f.profileRepository.update { it.copy(birthYear = 1990) }
        vm.onBirthYearChange("1980") // bozza vecchia

        vm.importPicked("content://doc/2")
        val summary = vm.uiState.awaitValue { it.pendingRestore != null }.pendingRestore!!
        assertEquals(1, summary.plans)
        assertNull("non si ripristina senza conferma", backup.restored)

        vm.confirmRestore()

        val message = vm.events.nextEvent()
        assertEquals("Backup ripristinato: 1 settimana, 0 misure.", message)
        assertEquals(backup.fileToRead, backup.restored)
        val state = vm.uiState.awaitValue { it.backupStatus != null && !it.busy && it.pendingRestore == null }
        assertEquals(BackupStatus(message, isError = false), state.backupStatus)
        assertFalse("la bozza dei dati personali non sopravvive al ripristino", state.personalDirty)
    }

    @Test
    fun restoreWarningsAreShownWithTheSuccessMessage() = runBlocking {
        backup.restoreResult = RestoreResult(listOf("Non sono riuscito ad aggiornare i promemoria: riattivali dal Profilo."))
        vm.importPicked("content://doc/2")
        vm.uiState.awaitValue { it.pendingRestore != null }
        vm.confirmRestore()
        val message = vm.events.nextEvent()
        assertTrue(message, message.startsWith("Backup ripristinato"))
        assertTrue(message, message.contains("promemoria"))
        assertFalse(vm.uiState.awaitValue { it.backupStatus != null }.backupStatus!!.isError)
    }

    @Test
    fun restoreFailureNeverStaysSilent() = runBlocking {
        backup.restoreError = BackupException("Ripristino non riuscito: i dati attuali non sono stati modificati.")
        vm.importPicked("content://doc/2")
        vm.uiState.awaitValue { it.pendingRestore != null }

        vm.confirmRestore()

        assertEquals("Ripristino non riuscito: i dati attuali non sono stati modificati.", vm.events.nextEvent())
        val state = vm.uiState.awaitValue { it.backupStatus?.isError == true && !it.busy }
        assertNull(state.pendingRestore)

        // Errore imprevisto: messaggio generico in italiano.
        backup.restoreError = IllegalStateException("disco")
        vm.importPicked("content://doc/2")
        vm.uiState.awaitValue { it.pendingRestore != null }
        vm.confirmRestore()
        assertEquals("Ripristino non riuscito. Riprova.", vm.events.nextEvent())
    }

    @Test
    fun unreadableOrInvalidFileIsReportedAndNothingIsPending() = runBlocking {
        backup.readError = BackupException("Il file selezionato non è un backup di NutriForma valido.")
        vm.importPicked("content://doc/3")
        assertEquals("Il file selezionato non è un backup di NutriForma valido.", vm.events.nextEvent())
        assertNull(vm.uiState.awaitValue { it.backupStatus?.isError == true }.pendingRestore)

        backup.readError = RuntimeException("io")
        vm.importPicked("content://doc/3")
        assertEquals("Non riesco a leggere il backup.", vm.events.nextEvent())
    }

    @Test
    fun cancelledRestoreKeepsTheCurrentData() = runBlocking {
        vm.importPicked("content://doc/2")
        vm.uiState.awaitValue { it.pendingRestore != null }
        vm.cancelRestore()
        assertNull(vm.uiState.awaitValue { it.pendingRestore == null }.pendingRestore)
        vm.confirmRestore() // niente da confermare: non fa nulla
        assertNull(backup.restored)
    }

    @Test
    fun autoBackupFolderErrorsAreReported() = runBlocking {
        backup.folderError = BackupException("Cartella scelta, ma non riesco a scrivere il backup: scegline un'altra.")
        vm.chooseAutoBackupFolder("content://tree/x")
        assertTrue(vm.events.nextEvent().contains("scegline un'altra"))
        assertTrue(vm.uiState.awaitValue { it.backupStatus?.isError == true }.backupStatus!!.isError)

        backup.folderError = null
        vm.chooseAutoBackupFolder("content://tree/y")
        assertEquals("Backup automatico attivo", vm.events.nextEvent())
        assertEquals("content://tree/y", backup.folder)

        vm.setAutoBackupEnabled(false)
        assertEquals("Backup automatico disattivato", vm.events.nextEvent())
        assertEquals(false, backup.autoEnabled)
    }

    @Test
    fun autoBackupErrorFromTheManagerIsExposed() = runBlocking {
        backup.autoBackupError.value = "Non riesco a scrivere nella cartella di backup automatico: scegli di nuovo la cartella."
        assertTrue(vm.uiState.awaitValue { it.autoBackupError != null }.autoBackupError!!.contains("scegli di nuovo"))
    }
}
