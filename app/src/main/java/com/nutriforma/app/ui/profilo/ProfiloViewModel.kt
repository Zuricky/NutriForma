package com.nutriforma.app.ui.profilo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriforma.app.data.backup.BackupException
import com.nutriforma.app.data.backup.BackupFile
import com.nutriforma.app.data.backup.BackupManager
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.data.repository.PlanRepository
import com.nutriforma.app.data.repository.TrackingRepository
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.nutrition.EnergyTargetCalculator
import com.nutriforma.app.notifications.ReminderScheduler
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
import java.time.LocalDate

private data class Overlay(val busy: Boolean = false, val pendingRestore: RestoreSummary? = null)

class ProfiloViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val planRepository: PlanRepository,
    private val trackingRepository: TrackingRepository,
    private val energyTargetCalculator: EnergyTargetCalculator,
    private val reminderScheduler: ReminderScheduler,
    private val backupManager: BackupManager,
    private val clock: Clock,
) : ViewModel() {

    private val overlay = MutableStateFlow(Overlay())
    private val messages = Channel<String>(Channel.BUFFERED)
    private var pendingFile: BackupFile? = null

    /** Messaggi (snackbar) one-off in italiano. */
    val events = messages.receiveAsFlow()

    val uiState: StateFlow<ProfiloUiState> = combine(
        userProfileRepository.profile,
        trackingRepository.observeMeasurements(),
        backupManager.autoBackupError,
        overlay,
    ) { profile, measurements, autoError, o ->
        val weight = measurements.sortedByDescending { it.date }.firstNotNullOfOrNull { it.weightKg }
        ProfiloUiState(
            goal = profile.goal,
            sportLevel = profile.sportLevel,
            cheatMealsPerWeek = profile.cheatMealsPerWeek.coerceIn(1, 2),
            sesso = profile.sesso,
            birthYear = profile.birthYear,
            heightCm = profile.heightCm,
            energyTarget = energyTargetCalculator.calculate(profile, weight, LocalDate.now(clock).year),
            excludedCount = profile.dislikedFoodIds.size,
            reminders = profile.reminders,
            backupFolderName = profile.backupFolderUri?.let { folderDisplayName(it) },
            autoBackupEnabled = profile.autoBackupEnabled,
            autoBackupError = autoError,
            lastBackupText = profile.lastBackupAtMillis?.let { formatDateTime(it, clock.zone) },
            busy = o.busy,
            pendingRestore = o.pendingRestore,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfiloUiState())

    // ---- Obiettivo / livello sportivo / pasti liberi -------------------------------------------------------

    fun setGoal(goal: Goal) = changePortions("Non riesco a salvare l'obiettivo.") { it.copy(goal = goal) }

    fun setSportLevel(level: SportLevel) = changePortions("Non riesco a salvare il livello sportivo.") { it.copy(sportLevel = level) }

    /** Salva e poi ricalcola le quantità della settimana corrente (stessi alimenti, porzioni nuove). */
    private fun changePortions(error: String, transform: (com.nutriforma.app.domain.model.UserProfile) -> com.nutriforma.app.domain.model.UserProfile) {
        launchSafely(error) {
            userProfileRepository.update(transform)
            val plan = planRepository.getOrCreateWeek(LocalDate.now(clock))
            planRepository.recalculatePortions(plan.id)
        }
    }

    fun setCheatMeals(count: Int) {
        launchSafely("Non riesco a salvare i pasti liberi.") {
            userProfileRepository.update { it.copy(cheatMealsPerWeek = count.coerceIn(1, 2)) }
            messages.trySend("Pasti liberi a settimana: ${count.coerceIn(1, 2)}. Valgono dalla prossima settimana generata.")
        }
    }

    // ---- Dati personali (per la stima delle kcal) ----------------------------------------------------------

    fun setSesso(sesso: Sesso) = launchSafely("Non riesco a salvare il dato.") { userProfileRepository.update { it.copy(sesso = sesso) } }

    fun setBirthYear(text: String) {
        val year = text.trim().toIntOrNull()
        val max = LocalDate.now(clock).year - 10
        if (text.isBlank()) {
            launchSafely("Non riesco a salvare il dato.") { userProfileRepository.update { it.copy(birthYear = null) } }
        } else if (year == null || year !in 1920..max) {
            messages.trySend("Anno di nascita non valido (tra 1920 e $max).")
        } else {
            launchSafely("Non riesco a salvare il dato.") { userProfileRepository.update { it.copy(birthYear = year) } }
        }
    }

    fun setHeight(text: String) {
        val cm = text.trim().toIntOrNull()
        if (text.isBlank()) {
            launchSafely("Non riesco a salvare il dato.") { userProfileRepository.update { it.copy(heightCm = null) } }
        } else if (cm == null || cm !in 100..230) {
            messages.trySend("Altezza non valida (tra 100 e 230 cm).")
        } else {
            launchSafely("Non riesco a salvare il dato.") { userProfileRepository.update { it.copy(heightCm = cm) } }
        }
    }

    // ---- Promemoria ----------------------------------------------------------------------------------------

    fun setReminder(kind: ReminderKind, enabled: Boolean) {
        launchSafely("Non riesco a salvare i promemoria.") {
            userProfileRepository.update {
                val r = it.reminders
                it.copy(
                    reminders = when (kind) {
                        ReminderKind.MEALS -> r.copy(mealRemindersEnabled = enabled)
                        ReminderKind.WATER -> r.copy(waterRemindersEnabled = enabled)
                        ReminderKind.WEIGH_IN -> r.copy(weighInReminderEnabled = enabled)
                    },
                )
            }
            reminderScheduler.syncWithProfile()
        }
    }

    fun notifyPermissionDenied() {
        messages.trySend("Per ricevere i promemoria consenti le notifiche dalle impostazioni del telefono.")
    }

    // ---- Backup --------------------------------------------------------------------------------------------

    fun suggestedBackupName(): String = backupManager.suggestedFileName()

    fun exportBackup(uri: Uri?) {
        if (uri == null) return
        runBackupAction("Esportazione non riuscita.") {
            backupManager.exportTo(uri)
            messages.trySend("Backup esportato")
        }
    }

    /** Legge il file scelto e chiede conferma prima di sostituire i dati. */
    fun importPicked(uri: Uri?) {
        if (uri == null) return
        runBackupAction("Non riesco a leggere il backup.") {
            val file = backupManager.readBackup(uri)
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
        }
    }

    fun cancelRestore() {
        pendingFile = null
        overlay.update { it.copy(pendingRestore = null) }
    }

    fun confirmRestore() {
        val file = pendingFile ?: return
        cancelRestore()
        runBackupAction("Ripristino non riuscito.") {
            backupManager.restore(file)
            messages.trySend("Backup ripristinato")
        }
    }

    fun chooseAutoBackupFolder(uri: Uri?) {
        if (uri == null) return
        runBackupAction("Non riesco a impostare la cartella.") {
            backupManager.setAutoBackupFolder(uri)
            messages.trySend("Backup automatico attivo")
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) =
        runBackupAction("Non riesco a modificare il backup automatico.") { backupManager.setAutoBackupEnabled(enabled) }

    private fun runBackupAction(fallback: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            overlay.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                messages.trySend(e.message ?: fallback)
            } catch (e: Exception) {
                messages.trySend(fallback)
            } finally {
                overlay.update { it.copy(busy = false) }
            }
        }
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
