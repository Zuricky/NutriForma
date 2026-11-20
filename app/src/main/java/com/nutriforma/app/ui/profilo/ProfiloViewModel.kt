package com.nutriforma.app.ui.profilo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.data.repository.PlanRepository
import com.nutriforma.app.data.repository.TrackingRepository
import com.nutriforma.app.domain.model.BodyMeasurement
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.nutrition.BmiCalculator
import com.nutriforma.app.domain.nutrition.EnergyTargetCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate

class ProfiloViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val planRepository: PlanRepository,
    private val trackingRepository: TrackingRepository,
    private val energyTargetCalculator: EnergyTargetCalculator,
    /** Cresce a ogni ripristino di un backup: i valori salvati sono cambiati, quindi si scartano modifiche e bozza. */
    private val restoreCount: StateFlow<Int>,
    private val clock: Clock,
) : ViewModel() {

    private val draft = MutableStateFlow(PersonalDraft())

    /** `true` dopo "Modifica" finché non si salva o si annulla (vive nel ViewModel: resta anche andando in Impostazioni e tornando). */
    private val editRequested = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.BUFFERED)

    // Cambi di obiettivo / livello sportivo in sequenza non devono sovrapporsi (salvataggio + ricalcolo + eventuale ripristino).
    private val portionLock = Mutex()

    /** Messaggi (snackbar) one-off in italiano. */
    val events = messages.receiveAsFlow()

    val uiState: StateFlow<ProfiloUiState> = combine(
        userProfileRepository.profile,
        trackingRepository.observeMeasurements(),
        draft,
        editRequested,
    ) { profile, measurements, d, edit ->
        val weight = measurements.sortedByDescending { it.date }.firstNotNullOfOrNull { it.weightKg }
        val year = LocalDate.now(clock).year
        val storedBirth = profile.birthYear?.toString().orEmpty()
        val storedHeight = profile.heightCm?.toString().orEmpty()
        val storedWeightText = weight?.let(::formatWeightInput).orEmpty()
        val firstNameText = d.firstName ?: profile.firstName
        val lastNameText = d.lastName ?: profile.lastName
        val birthText = d.birthYearText ?: storedBirth
        val heightText = d.heightText ?: storedHeight
        val weightText = d.weightText ?: storedWeightText
        val sesso = d.sesso ?: profile.sesso
        val weightResult = parseWeight(weightText)
        val hasSaved = hasSavedPersonalData(profile)
        ProfiloUiState(
            goal = profile.goal,
            sportLevel = profile.sportLevel,
            cheatMealsPerWeek = profile.cheatMealsPerWeek.coerceIn(1, 2),
            firstName = profile.firstName,
            lastName = profile.lastName,
            birthYear = profile.birthYear,
            ageYears = profile.birthYear?.let { year - it },
            heightCm = profile.heightCm,
            weightKg = weight,
            bmi = BmiCalculator.calculate(weight, profile.heightCm),
            hasSavedPersonalData = hasSaved,
            isEditing = edit || !hasSaved,
            sesso = sesso,
            firstNameText = firstNameText,
            lastNameText = lastNameText,
            birthYearText = birthText,
            heightText = heightText,
            weightText = weightText,
            birthYearError = if (d.showErrors) parseBirthYear(birthText, year).error else null,
            heightError = if (d.showErrors) parseHeight(heightText).error else null,
            weightError = if (d.showErrors) weightResult.error else null,
            personalDirty = firstNameText.trim() != profile.firstName ||
                lastNameText.trim() != profile.lastName ||
                sesso != profile.sesso ||
                birthText.trim() != storedBirth ||
                heightText.trim() != storedHeight ||
                weightChanged(weightResult, weightText, weight),
            energyTarget = energyTargetCalculator.calculate(profile, weight, year),
            excludedCount = profile.dislikedFoodIds.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfiloUiState())

    init {
        // Dopo un ripristino i dati personali mostrati sono quelli del backup: una bozza vecchia sarebbe fuorviante.
        viewModelScope.launch {
            restoreCount.drop(1).collect { discardEdits() }
        }
    }

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

    // ---- Dati personali: scheda bloccata, "Modifica" → bozza → "Salva" / "Annulla" ----------------------------

    /** Apre la scheda in modifica partendo dai valori salvati. */
    fun startEdit() {
        draft.value = PersonalDraft()
        editRequested.value = true
    }

    /** Scarta la bozza e torna ai valori salvati (la scheda si blocca di nuovo se ci sono dati salvati). */
    fun cancelEdit() = discardEdits()

    private fun discardEdits() {
        draft.value = PersonalDraft()
        editRequested.value = false
    }

    fun onFirstNameChange(text: String) = draft.update { it.copy(firstName = sanitizeName(text)) }

    fun onLastNameChange(text: String) = draft.update { it.copy(lastName = sanitizeName(text)) }

    fun onSessoChange(sesso: Sesso) = draft.update { it.copy(sesso = sesso) }

    fun onBirthYearChange(text: String) = draft.update { it.copy(birthYearText = text.filter(Char::isDigit).take(4)) }

    fun onHeightChange(text: String) = draft.update { it.copy(heightText = text.filter(Char::isDigit).take(3)) }

    fun onWeightChange(text: String) = draft.update { it.copy(weightText = sanitizeWeightInput(text)) }

    /**
     * Valida e salva insieme nome, sesso, anno di nascita, altezza e peso; se qualcosa non va non salva nulla e lo dice.
     * Il peso diventa la misura di oggi (vita, cosce e nota già registrate oggi restano): Profilo, Progressi, fabbisogno
     * stimato e IMC leggono tutti lo stesso dato, quindi si aggiornano da soli.
     */
    fun savePersonal() {
        val pending = draft.value
        launchSafely("Non riesco a salvare i dati personali.") {
            val stored = userProfileRepository.current()
            val storedWeight = latestWeight()
            val today = LocalDate.now(clock)
            val firstName = (pending.firstName ?: stored.firstName).trim()
            val lastName = (pending.lastName ?: stored.lastName).trim()
            val birth = parseBirthYear(pending.birthYearText ?: stored.birthYear?.toString().orEmpty(), today.year)
            val height = parseHeight(pending.heightText ?: stored.heightCm?.toString().orEmpty())
            val weightText = pending.weightText ?: storedWeight?.let(::formatWeightInput).orEmpty()
            val weight = parseWeight(weightText)
            val error = birth.error ?: height.error ?: weight.error
            if (error != null) {
                draft.update { it.copy(showErrors = true) }
                messages.trySend("$error Correggi i campi evidenziati e premi Salva.")
                return@launchSafely
            }
            val sesso = pending.sesso ?: stored.sesso
            val profileChanged = firstName != stored.firstName || lastName != stored.lastName || sesso != stored.sesso ||
                birth.value != stored.birthYear || height.value != stored.heightCm
            val weightChanged = weightChanged(weight, weightText, storedWeight)
            if (!profileChanged && !weightChanged) {
                if (draft.compareAndSet(pending, PersonalDraft())) editRequested.value = false
                messages.trySend("Nessuna modifica da salvare.")
                return@launchSafely
            }
            // Il peso per primo: se fallisce non è stato salvato nulla e la bozza resta lì.
            if (weightChanged) {
                trackingRepository.saveMeasurement(BodyMeasurement(today, weight.kg, null, null, null))
            }
            if (profileChanged) {
                userProfileRepository.update {
                    it.copy(firstName = firstName, lastName = lastName, sesso = sesso, birthYear = birth.value, heightCm = height.value)
                }
            }
            // Se nel frattempo l'utente ha continuato a scrivere, la nuova bozza resta (e la scheda resta in modifica).
            if (draft.compareAndSet(pending, PersonalDraft())) editRequested.value = false
            messages.trySend("Dati personali salvati")
        }
    }

    private suspend fun latestWeight(): Double? =
        trackingRepository.observeMeasurements().first().sortedByDescending { it.date }.firstNotNullOfOrNull { it.weightKg }

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

/** Nome o cognome in digitazione: niente caratteri di controllo, al massimo [MAX_NAME_LENGTH] caratteri. */
private fun sanitizeName(text: String): String = text.filter { !it.isISOControl() }.take(MAX_NAME_LENGTH)

/** Almeno un dato personale salvato: da lì in poi la scheda si apre bloccata. */
private fun hasSavedPersonalData(profile: UserProfile): Boolean =
    profile.firstName.isNotBlank() || profile.lastName.isNotBlank() || profile.sesso != null ||
        profile.birthYear != null || profile.heightCm != null

/**
 * Il peso digitato è un cambiamento se non è leggibile (così "Salva" mostra l'errore) o se, arrotondato a un
 * decimale, differisce dall'ultimo peso registrato. Campo vuoto = nessun cambiamento (il peso non si cancella da qui).
 */
private fun weightChanged(parsed: WeightResult, text: String, stored: Double?): Boolean {
    if (text.isBlank()) return false
    val kg = parsed.kg ?: return true
    return stored == null || roundWeight(kg) != roundWeight(stored)
}
