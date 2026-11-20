package com.nutriforma.app.ui.impostazioni

import com.nutriforma.app.data.AppFixture
import com.nutriforma.app.data.MutableClock
import com.nutriforma.app.data.backup.BackupException
import com.nutriforma.app.data.backup.BackupFile
import com.nutriforma.app.data.backup.BackupGateway
import com.nutriforma.app.data.backup.BackupWeeklyPlan
import com.nutriforma.app.data.backup.RestoreResult
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.SportLevel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

private class FakeBackupGateway : BackupGateway {
    override val autoBackupError: MutableStateFlow<String?> = MutableStateFlow(null)
    override val restoreCount: MutableStateFlow<Int> = MutableStateFlow(0)
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
        restoreCount.value += 1
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

class ImpostazioniViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-11-17T08:00:00Z"))
    private lateinit var f: AppFixture
    private lateinit var backup: FakeBackupGateway
    private lateinit var reminders: FakeReminderSync
    private lateinit var vm: ImpostazioniViewModel

    @Before
    fun setUp() {
        f = AppFixture(tmp.root, clock)
        backup = FakeBackupGateway()
        reminders = FakeReminderSync()
        vm = ImpostazioniViewModel(
            userProfileRepository = f.profileRepository, reminderSync = reminders, backup = backup, clock = clock,
        )
    }

    @After
    fun tearDown() = f.close()

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
        assertEquals("il ripristino viene segnalato a chi tiene bozze (Profilo)", 1, backup.restoreCount.value)
    }

    @Test
    fun restoreWarningsAreShownWithTheSuccessMessage() = runBlocking {
        backup.restoreResult = RestoreResult(listOf("Non sono riuscito ad aggiornare i promemoria: riattivali dalle Impostazioni."))
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
