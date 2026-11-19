package com.nutriforma.app.ui.progressi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.data.repository.TrackingRepository
import com.nutriforma.app.domain.model.BodyMeasurement
import com.nutriforma.app.domain.tips.DailyTips
import com.nutriforma.app.domain.tracking.MeasurementInputResult
import com.nutriforma.app.domain.tracking.MeasurementMetric
import com.nutriforma.app.domain.tracking.SeriesPoint
import com.nutriforma.app.domain.tracking.parseMeasurementInput
import com.nutriforma.app.domain.tracking.seriesOf
import com.nutriforma.app.ui.TodayState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class ProgressiUiState(
    val waterMl: Int = 0,
    val waterGoalMl: Int = 1700,
    val tip: String = "",
    val series: Map<MeasurementMetric, List<SeriesPoint>> = emptyMap(),
    /** Più recenti per prime. */
    val recent: List<BodyMeasurement> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val dialogOpen: Boolean = false,
    val dialogError: String? = null,
)

private data class Overlay(val dialogOpen: Boolean = false, val dialogError: String? = null)

class ProgressiViewModel(
    private val trackingRepository: TrackingRepository,
    private val userProfileRepository: UserProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val todayState = TodayState(clock)
    private val overlay = MutableStateFlow(Overlay())
    private val messages = Channel<String>(Channel.BUFFERED)

    /** Messaggi (snackbar) one-off in italiano. */
    val events = messages.receiveAsFlow()

    /** Da chiamare quando l'app torna in primo piano o passa la mezzanotte: acqua e consiglio ripartono dal nuovo giorno. */
    fun refreshToday() {
        todayState.refresh()
    }

    // observeWaterToday() legge la data dall'orologio quando viene chiamato: a ogni cambio di giorno si ricrea.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressiUiState> = todayState.today.flatMapLatest { today ->
        combine(
            trackingRepository.observeWaterToday(),
            trackingRepository.observeMeasurements(),
            userProfileRepository.profile,
            overlay,
        ) { water, measurements, profile, o ->
            ProgressiUiState(
                waterMl = water.coerceAtLeast(0),
                waterGoalMl = profile.waterGoalMl,
                tip = DailyTips.tipFor(today),
                series = MeasurementMetric.entries.associateWith { seriesOf(measurements, it) },
                recent = measurements.sortedByDescending { it.date }.take(RECENT_LIMIT),
                today = today,
                dialogOpen = o.dialogOpen,
                dialogError = o.dialogError,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProgressiUiState(tip = DailyTips.tipFor(todayState.today.value), today = todayState.today.value),
    )

    // ---- Acqua ---------------------------------------------------------------------------------------------

    fun addWater(ml: Int) = launchSafely("Non riesco a registrare l'acqua.") { trackingRepository.addWater(ml) }

    fun subtractWater(ml: Int) = launchSafely("Non riesco a correggere l'acqua.") { trackingRepository.subtractWater(ml) }

    // ---- Misure --------------------------------------------------------------------------------------------

    fun openDialog() = overlay.update { Overlay(dialogOpen = true) }

    fun closeDialog() = overlay.update { Overlay() }

    fun saveMeasurement(date: LocalDate, weight: String, waist: String, thigh: String) {
        when (val parsed = parseMeasurementInput(weight, waist, thigh)) {
            is MeasurementInputResult.Invalid -> overlay.update { it.copy(dialogError = parsed.message) }
            is MeasurementInputResult.Valid -> launchSafely("Non riesco a salvare le misure.") {
                try {
                    trackingRepository.saveMeasurement(BodyMeasurement(date, parsed.weightKg, parsed.waistCm, parsed.thighCm, null))
                    overlay.update { Overlay() }
                    messages.trySend("Misure salvate")
                } catch (e: IllegalArgumentException) {
                    overlay.update { it.copy(dialogError = e.message ?: "Misure non valide.") }
                }
            }
        }
    }

    fun deleteMeasurement(date: LocalDate) =
        launchSafely("Non riesco a eliminare la misura.") {
            trackingRepository.deleteMeasurement(date)
            messages.trySend("Misura eliminata")
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

    private companion object {
        const val RECENT_LIMIT = 10
    }
}
