package com.nutriforma.app.ui.impostazioni

import com.nutriforma.app.data.AppFixture
import com.nutriforma.app.data.MutableClock
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
import com.nutriforma.app.ui.navigation.BottomBarItems
import com.nutriforma.app.ui.navigation.Dieta
import com.nutriforma.app.ui.navigation.Esclusioni
import com.nutriforma.app.ui.navigation.Impostazioni
import com.nutriforma.app.ui.navigation.Profilo
import com.nutriforma.app.ui.navigation.ProfiloSubRoutes
import com.nutriforma.app.ui.navigation.Progressi
import com.nutriforma.app.ui.navigation.Spesa
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

private class StubBackup : BackupGateway {
    override val autoBackupError = MutableStateFlow<String?>(null)
    override val restoreCount = MutableStateFlow(0)
    override fun suggestedFileName() = "backup.json"
    override suspend fun exportTo(target: String) = Unit
    override suspend fun readBackup(source: String) = BackupFile(
        exportedAtMillis = 1_794_000_000_000,
        weeklyPlans = listOf(BackupWeeklyPlan(1, 20773, 1, Goal.EQUILIBRIO, SportLevel.MODERATO, 1)),
    )
    override suspend fun restore(file: BackupFile): RestoreResult = RestoreResult()
    override suspend fun setAutoBackupFolder(tree: String) = Unit
    override suspend fun setAutoBackupEnabled(enabled: Boolean) = Unit
}

private class CountingReminderSync : ReminderSync {
    @Volatile
    var syncs = 0
    override suspend fun syncWithProfile() {
        syncs++
    }
}

/** Impostazioni è una sotto-schermata del Profilo: la barra inferiore resta di 4 schede e lo stato sopravvive all'andare e venire. */
class ImpostazioniNavigationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-11-17T08:00:00Z"))
    private lateinit var f: AppFixture
    private lateinit var vm: ImpostazioniViewModel

    @Before
    fun setUp() {
        f = AppFixture(tmp.root, clock)
        vm = ImpostazioniViewModel(f.profileRepository, CountingReminderSync(), StubBackup(), clock)
    }

    @After
    fun tearDown() = f.close()

    // ---- Navigazione -----------------------------------------------------------------------------------------

    @Test
    fun bottomBarKeepsTheFourCoreTabsOnly() {
        assertEquals(listOf<Any>(Dieta, Spesa, Progressi, Profilo), BottomBarItems.map { it.route })
        assertTrue("Impostazioni non è una scheda", BottomBarItems.none { it.route is Impostazioni })
    }

    @Test
    fun impostazioniBelongsToTheProfiloTab() {
        assertTrue(Impostazioni::class in ProfiloSubRoutes)
        assertTrue(Esclusioni::class in ProfiloSubRoutes)
        assertFalse("le schede principali non sono sotto-schermate", ProfiloSubRoutes.any { it == Dieta::class || it == Profilo::class })
    }

    // ---- Stato che sopravvive -------------------------------------------------------------------------------

    @Test
    fun reminderChoicesPersistWhenTheScreenIsLeftAndReopened() = runBlocking {
        vm.setReminder(ReminderKind.WATER, true)
        // Si aspetta che il salvataggio sia finito prima di collegare lo stato: un lettore DataStore agganciato durante la
        // prima scrittura di un file nuovo può restare indietro (succede solo nei test, dove il file nasce vuoto).
        awaitUntil { f.profileRepository.current().reminders.waterRemindersEnabled }
        vm.uiState.awaitValue { it.reminders.waterRemindersEnabled }

        // Si torna al Profilo (nessun collettore) e si riapre: stesso ViewModel, stesso stato.
        val reopened = vm.uiState.awaitValue { it.reminders.waterRemindersEnabled }
        assertTrue(reopened.reminders.waterRemindersEnabled)

        // E un nuovo ViewModel (app riavviata) legge le stesse scelte dal profilo salvato.
        val fresh = ImpostazioniViewModel(f.profileRepository, CountingReminderSync(), StubBackup(), clock)
        assertTrue(fresh.uiState.awaitValue { it.reminders.waterRemindersEnabled }.reminders.waterRemindersEnabled)
    }

    @Test
    fun aPendingRestoreStaysOpenWhileTheScreenIsAway() = runBlocking {
        vm.importPicked("content://doc/1")
        val summary = vm.uiState.awaitValue { it.pendingRestore != null }.pendingRestore!!
        assertEquals(1, summary.plans)

        val again = vm.uiState.awaitValue { it.pendingRestore != null }
        assertEquals(summary, again.pendingRestore)
        assertFalse(again.busy)

        vm.cancelRestore()
        assertNull(vm.uiState.awaitValue { it.pendingRestore == null }.pendingRestore)
    }

    @Test
    fun lastBackupAndFolderAreShownFromTheProfile() = runBlocking {
        assertNull(vm.uiState.awaitValue { true }.lastBackupText)
        f.profileRepository.updateSilently {
            it.copy(
                backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDietaBackup",
                autoBackupEnabled = true, lastBackupAtMillis = 1_794_000_000_000L,
            )
        }
        val state = vm.uiState.awaitValue { it.backupFolderName != null }
        assertEquals("Documents/DietaBackup", state.backupFolderName)
        assertTrue(state.autoBackupEnabled)
        assertNotNull(state.lastBackupText)
        assertTrue(state.lastBackupText!!.contains("alle"))
    }

    @Test
    fun folderNameIsReadableFromTreeUri() {
        assertEquals("Documents/DietaBackup", folderDisplayName("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDietaBackup"))
        assertEquals("content://altro", folderDisplayName("content://altro"))
    }
}
