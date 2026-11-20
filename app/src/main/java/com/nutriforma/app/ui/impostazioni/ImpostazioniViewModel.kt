package com.nutriforma.app.ui.impostazioni

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriforma.app.data.backup.BackupException
import com.nutriforma.app.data.backup.BackupFile
import com.nutriforma.app.data.backup.BackupGateway
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.notifications.ReminderSync
import com.nutriforma.app.ui.promemoria.ReminderKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

private fun countText(n: Int, singular: String, plural: String) = if (n == 1) "1 $singular" else "$n $plural"

private data class Overlay(
    val busy: Boolean = false,
    val pendingRestore: RestoreSummary? = null,
    val status: BackupStatus? = null,
)

/** Impostazioni dell'app: promemoria e notifiche, backup e ripristino. */
class ImpostazioniViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val reminderSync: ReminderSync,
    private val backup: BackupGateway,
    private val clock: Clock,
) : ViewModel() {

    private val overlay = MutableStateFlow(Overlay())
    private val messages = Channel<String>(Channel.BUFFERED)
    private var pendingFile: BackupFile? = null

    /** Messaggi (snackbar) one-off in italiano. */
    val events = messages.receiveAsFlow()

    val uiState: StateFlow<ImpostazioniUiState> = combine(
        userProfileRepository.profile,
        backup.autoBackupError,
        overlay,
    ) { profile, autoError, o ->
        ImpostazioniUiState(
            reminders = profile.reminders,
            backupFolderName = profile.backupFolderUri?.let { folderDisplayName(it) },
            autoBackupEnabled = profile.autoBackupEnabled,
            autoBackupError = autoError,
            lastBackupText = profile.lastBackupAtMillis?.let { formatDateTime(it, clock.zone) },
            busy = o.busy,
            pendingRestore = o.pendingRestore,
            backupStatus = o.status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImpostazioniUiState())

    // ---- Promemoria ----------------------------------------------------------------------------------------

    fun setReminder(kind: ReminderKind, enabled: Boolean) {
        launchSafely("Non riesco a salvare i promemoria.") {
            userProfileRepository.update {
                val r = it.reminders
                it.copy(
                    reminders = when (kind) {
                        ReminderKind.MEALS -> r.copy(mealRemindersEnabled = enabled)
                        ReminderKind.WATER -> r.copy(waterRemindersEnabled = enabled)
                        ReminderKind.TIP -> r.copy(dailyTipEnabled = enabled)
                        ReminderKind.WEIGH_IN -> r.copy(weighInReminderEnabled = enabled)
                    },
                )
            }
            reminderSync.syncWithProfile()
        }
    }

    fun notifyPermissionDenied() {
        messages.trySend("Per ricevere i promemoria consenti le notifiche dalle impostazioni del telefono.")
    }

    // ---- Backup --------------------------------------------------------------------------------------------

    fun suggestedBackupName(): String = backup.suggestedFileName()

    fun exportBackup(target: String?) {
        if (target == null) return
        runBackupAction("Esportazione non riuscita.") {
            backup.exportTo(target)
            "Backup esportato"
        }
    }

    /** Legge il file scelto e chiede conferma prima di sostituire i dati. */
    fun importPicked(source: String?) {
        if (source == null) return
        runBackupAction("Non riesco a leggere il backup.") {
            val file = backup.readBackup(source)
            pendingFile = file
            overlay.update {
                it.copy(
                    pendingRestore = RestoreSummary(
                        exportedAtText = if (file.exportedAtMillis > 0) formatDateTime(file.exportedAtMillis, clock.zone) else "data sconosciuta",
                        plans = file.weeklyPlans.size,
                        measurements = file.bodyMeasurements.size,
                    ),
                )
            }
            null
        }
    }

    fun cancelRestore() {
        pendingFile = null
        overlay.update { it.copy(pendingRestore = null) }
    }

    fun confirmRestore() {
        val file = pendingFile ?: return
        cancelRestore()
        runBackupAction("Ripristino non riuscito. Riprova.") {
            val result = backup.restore(file)
            val base = "Backup ripristinato: ${countText(file.weeklyPlans.size, "settimana", "settimane")}, " +
                "${countText(file.bodyMeasurements.size, "misura", "misure")}."
            if (result.warnings.isEmpty()) base else base + " " + result.warnings.joinToString(" ")
        }
    }

    fun chooseAutoBackupFolder(tree: String?) {
        if (tree == null) return
        runBackupAction("Non riesco a impostare la cartella.") {
            backup.setAutoBackupFolder(tree)
            "Backup automatico attivo"
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) =
        runBackupAction("Non riesco a modificare il backup automatico.") {
            backup.setAutoBackupEnabled(enabled)
            if (enabled) "Backup automatico attivato" else "Backup automatico disattivato"
        }

    /** Esegue un'azione di backup: ogni esito (riuscita o no) finisce nello stato visibile e in uno snackbar. */
    private fun runBackupAction(fallback: String, block: suspend () -> String?) {
        viewModelScope.launch {
            overlay.update { it.copy(busy = true, status = null) }
            try {
                val done = block()
                if (done != null) {
                    overlay.update { it.copy(status = BackupStatus(done, isError = false)) }
                    messages.trySend(done)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                reportBackupError(e.message?.takeIf { it.isNotBlank() } ?: fallback)
            } catch (e: Exception) {
                reportBackupError(fallback)
            } finally {
                overlay.update { it.copy(busy = false) }
            }
        }
    }

    private fun reportBackupError(message: String) {
        overlay.update { it.copy(status = BackupStatus(message, isError = true)) }
        messages.trySend(message)
    }

    private fun launchSafely(errorMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.trySend(errorMessage)
            }
        }
    }
}
