package com.nutriforma.app.ui.profilo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriforma.app.data.backup.BackupException
import com.nutriforma.app.data.backup.BackupFile
import com.nutriforma.app.data.backup.BackupGateway
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.data.repository.PlanRepository
import com.nutriforma.app.data.repository.TrackingRepository
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.nutrition.EnergyTargetCalculator
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate

private fun countText(n: Int, singular: String, plural: String) = if (n == 1) "1 $singular" else "$n $plural"

private data class Overlay(
    val busy: Boolean = false,
    val pendingRestore: RestoreSummary? = null,
    val status: BackupStatus? = null,
)

class ProfiloViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val planRepository: PlanRepository,
    private val trackingRepository: TrackingRepository,
    private val energyTargetCalculator: EnergyTargetCalculator,
    private val reminderSync: ReminderSync,
    private val backup: BackupGateway,
    private val clock: Clock,
) : ViewModel() {

    private val overlay = MutableStateFlow(Overlay())
    private val draft = MutableStateFlow(PersonalDraft())
    private val messages = Channel<String>(Channel.BUFFERED)
    private var pendingFile: BackupFile? = null

    // Cambi di obiettivo / livello sportivo in sequenza non devono sovrapporsi (salvataggio + ricalcolo + eventuale ripristino).
    private val portionLock = Mutex()

    /** Messaggi (snackbar) one-off in italiano. */
    val events = messages.receiveAsFlow()

    val uiState: StateFlow<ProfiloUiState> = combine(
        userProfileRepository.profile,
        trackingRepository.observeMeasurements(),
        backup.autoBackupError,
        overlay,
        draft,
    ) { profile, measurements, autoError, o, d ->
        val weight = measurements.sortedByDescending { it.date }.firstNotNullOfOrNull { it.weightKg }
        val year = LocalDate.now(clock).year
        val storedBirth = profile.birthYear?.toString().orEmpty()
        val storedHeight = profile.heightCm?.toString().orEmpty()
        val birthText = d.birthYearText ?: storedBirth
        val heightText = d.heightText ?: storedHeight
        val sesso = d.sesso ?: profile.sesso
        ProfiloUiState(
            goal = profile.goal,
            sportLevel = profile.sportLevel,
            cheatMealsPerWeek = profile.cheatMealsPerWeek.coerceIn(1, 2),
            sesso = sesso,
            birthYear = profile.birthYear,
            heightCm = profile.heightCm,
            birthYearText = birthText,
            heightText = heightText,
            birthYearError = if (d.showErrors) parseBirthYear(birthText, year).error else null,
            heightError = if (d.showErrors) parseHeight(heightText).error else null,
            personalDirty = sesso != profile.sesso || birthText.trim() != storedBirth || heightText.trim() != storedHeight,
            energyTarget = energyTargetCalculator.calculate(profile, weight, year),
            excludedCount = profile.dislikedFoodIds.size,
            reminders = profile.reminders,
            backupFolderName = profile.backupFolderUri?.let { folderDisplayName(it) },
            autoBackupEnabled = profile.autoBackupEnabled,
            autoBackupError = autoError,
            lastBackupText = profile.lastBackupAtMillis?.let { formatDateTime(it, clock.zone) },
            busy = o.busy,
            pendingRestore = o.pendingRestore,
            backupStatus = o.status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfiloUiState())

    // ---- Obiettivo / livello sportivo / pasti liberi -------------------------------------------------------

    fun setGoal(goal: Goal) = changePortions(
        isUnchanged = { it.goal == goal },
        change = { it.copy(goal = goal) },
        undo = { current, previous -> current.copy(goal = previous.goal) },
        success = "Obiettivo impostato su «${goal.labelIt}»: porzioni della settimana aggiornate.",
        failure = "Non riesco ad aggiornare le porzioni per il nuovo obiettivo.",
    )

    fun setSportLevel(level: SportLevel) = changePortions(
        isUnchanged = { it.sportLevel == level },
        change = { it.copy(sportLevel = level) },
        undo = { current, previous -> current.copy(sportLevel = previous.sportLevel) },
        success = "Livello sportivo impostato su «${level.labelIt}»: porzioni della settimana aggiornate.",
        failure = "Non riesco ad aggiornare le porzioni per il nuovo livello sportivo.",
    )

    /**
     * Salva la scelta e poi ricalcola le quantità della settimana corrente (stessi alimenti, porzioni nuove).
     * Se il ricalcolo fallisce la scelta torna quella di prima, così profilo e piano restano coerenti.
     */
    private fun changePortions(
        isUnchanged: (UserProfile) -> Boolean,
        change: (UserProfile) -> UserProfile,
        undo: (current: UserProfile, previous: UserProfile) -> UserProfile,
        success: String,
        failure: String,
    ) {
        viewModelScope.launch {
            portionLock.withLock {
                try {
                    val previous = userProfileRepository.current()
                    if (isUnchanged(previous)) return@withLock
                    userProfileRepository.update(change)
                    try {
                        val plan = planRepository.getOrCreateWeek(LocalDate.now(clock))
                        planRepository.recalculatePortions(plan.id)
                        messages.trySend(success)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val reverted = try {
                            userProfileRepository.update { undo(it, previous) }
                            true
                        } catch (r: CancellationException) {
                            throw r
                        } catch (r: Exception) {
                            false
                        }
                        messages.trySend(
                            if (reverted) "$failure Ho mantenuto la scelta precedente."
                            else "$failure Le porzioni potrebbero non essere allineate: riseleziona la scelta per riprovare.",
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    messages.trySend(failure)
                }
            }
        }
    }

    fun setCheatMeals(count: Int) {
        launchSafely("Non riesco a salvare i pasti liberi.") {
            userProfileRepository.update { it.copy(cheatMealsPerWeek = count.coerceIn(1, 2)) }
            messages.trySend("Pasti liberi a settimana: ${count.coerceIn(1, 2)}. Valgono dalla prossima settimana generata.")
        }
    }

    // ---- Dati personali (per la stima delle kcal): bozza + "Salva" ----------------------------------------

    fun onSessoChange(sesso: Sesso) = draft.update { it.copy(sesso = sesso) }

    fun onBirthYearChange(text: String) = draft.update { it.copy(birthYearText = text.filter(Char::isDigit).take(4)) }

    fun onHeightChange(text: String) = draft.update { it.copy(heightText = text.filter(Char::isDigit).take(3)) }

    /** Valida e salva insieme sesso, anno di nascita e altezza; se qualcosa non va non salva nulla e lo dice. */
    fun savePersonal() {
        val pending = draft.value
        launchSafely("Non riesco a salvare i dati personali.") {
            val stored = userProfileRepository.current()
            val birth = parseBirthYear(pending.birthYearText ?: stored.birthYear?.toString().orEmpty(), LocalDate.now(clock).year)
            val height = parseHeight(pending.heightText ?: stored.heightCm?.toString().orEmpty())
            val error = birth.error ?: height.error
            if (error != null) {
                draft.update { it.copy(showErrors = true) }
                messages.trySend("$error Correggi i campi evidenziati e premi Salva.")
                return@launchSafely
            }
            val sesso = pending.sesso ?: stored.sesso
            if (sesso == stored.sesso && birth.value == stored.birthYear && height.value == stored.heightCm) {
                draft.compareAndSet(pending, PersonalDraft())
                messages.trySend("Nessuna modifica da salvare.")
                return@launchSafely
            }
            userProfileRepository.update { it.copy(sesso = sesso, birthYear = birth.value, heightCm = height.value) }
            // Se nel frattempo l'utente ha continuato a scrivere, la nuova bozza resta.
            draft.compareAndSet(pending, PersonalDraft())
            messages.trySend("Dati personali salvati")
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
            // I dati personali mostrati ora sono quelli del backup: una bozza vecchia sarebbe fuorviante.
            draft.value = PersonalDraft()
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
