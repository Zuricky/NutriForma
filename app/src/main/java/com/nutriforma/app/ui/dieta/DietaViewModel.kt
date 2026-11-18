package com.nutriforma.app.ui.dieta

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.data.repository.FoodRepository
import com.nutriforma.app.data.repository.MealCompletionRepository
import com.nutriforma.app.data.repository.PlanRepository
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.dayIndex
import com.nutriforma.app.domain.nutrition.NutritionCalculator
import com.nutriforma.app.domain.rules.WeeklyComplianceCalculator
import com.nutriforma.app.domain.swap.MealAlternative
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Dialogs / sheets that sit on top of the screen content. */
data class DietaOverlay(
    val confirmRegenerate: Boolean = false,
    val isBusy: Boolean = false,
    val swap: SwapSheetState? = null,
    val bootstrapError: String? = null,
)

class DietaViewModel(
    private val planRepository: PlanRepository,
    private val foodRepository: FoodRepository,
    private val userProfileRepository: UserProfileRepository,
    private val completionRepository: MealCompletionRepository,
    private val complianceCalculator: WeeklyComplianceCalculator,
    private val nutritionCalculator: NutritionCalculator,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)
    private val selectedDay = savedStateHandle.getStateFlow(KEY_SELECTED_DAY, today.dayIndex())
    private val overlay = MutableStateFlow(DietaOverlay())

    private val messages = Channel<String>(Channel.BUFFERED)
    /** One-off Italian snackbar messages. */
    val events = messages.receiveAsFlow()

    /** Alternatives of the open swap sheet, in the same order as [SwapSheetState.Loaded.options]. */
    private var pendingAlternatives: List<MealAlternative> = emptyList()
    private var pendingMealId: Long? = null
    private var currentPlanId: Long? = null

    private val content: StateFlow<DietaUiState> = combine(
        planRepository.observeWeek(today),
        foodRepository.observeFoods(),
        userProfileRepository.profile,
        completionRepository.completedMealIds,
        selectedDay,
    ) { plan, foods, profile, completed, day ->
        if (plan == null || foods.isEmpty()) {
            DietaUiState.Loading
        } else {
            currentPlanId = plan.id
            DietaUiState.Ready(
                buildDietaContent(plan, foods, profile, completed, day, today, complianceCalculator, nutritionCalculator),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietaUiState.Loading)

    val uiState: StateFlow<DietaUiState> = combine(content, overlay) { state, o ->
        if (state is DietaUiState.Loading && o.bootstrapError != null) DietaUiState.Error(o.bootstrapError) else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietaUiState.Loading)

    val overlayState: StateFlow<DietaOverlay> = overlay

    init {
        ensurePlan()
    }

    // ---- Bootstrap -----------------------------------------------------------------------------------------

    /** Waits for the food seed, then creates the current week if it does not exist yet. */
    fun ensurePlan() {
        overlay.update { it.copy(bootstrapError = null) }
        viewModelScope.launch {
            try {
                foodRepository.observeFoods().first { it.isNotEmpty() }
                planRepository.getOrCreateWeek(today)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                overlay.update { it.copy(bootstrapError = "Non riesco a preparare la dieta della settimana. Riprova.") }
            }
        }
    }

    // ---- Day / completion ----------------------------------------------------------------------------------

    fun selectDay(dayIndex: Int) {
        savedStateHandle[KEY_SELECTED_DAY] = dayIndex.coerceIn(0, 6)
    }

    fun setCompleted(mealId: Long, completed: Boolean) {
        viewModelScope.launch { completionRepository.setCompleted(mealId, completed) }
    }

    // ---- Turno lungo / pasto libero ------------------------------------------------------------------------

    fun setTurnoLungo(dayIndex: Int, enabled: Boolean) {
        val planId = currentPlanId ?: return
        runAction("Non riesco a modificare il turno lungo.") {
            val config = if (enabled) {
                val profile = userProfileRepository.current()
                TurnoLungoDay(dayIndex, profile.turnoLungoDefaultOption, profile.turnoLungoDefaultSlot)
            } else {
                null
            }
            planRepository.setTurnoLungo(planId, dayIndex, config)
        }
    }

    fun setPranzoLibero(mealId: Long, enabled: Boolean) {
        runAction("Non riesco a modificare il pasto libero.") { planRepository.setPranzoLibero(mealId, enabled) }
    }

    // ---- Regenerate week -----------------------------------------------------------------------------------

    fun requestRegenerate() = overlay.update { it.copy(confirmRegenerate = true) }

    fun dismissRegenerate() = overlay.update { it.copy(confirmRegenerate = false) }

    fun confirmRegenerate() {
        val planId = currentPlanId ?: return
        overlay.update { it.copy(confirmRegenerate = false) }
        runAction("Non riesco a generare la settimana. Riprova.") {
            planRepository.regenerateWeek(planId, clock.millis())
            messages.trySend("Nuova settimana generata")
        }
    }

    // ---- Swap ----------------------------------------------------------------------------------------------

    fun openSwap(mealId: Long, title: String) {
        pendingMealId = mealId
        pendingAlternatives = emptyList()
        overlay.update { it.copy(swap = SwapSheetState.Loading(title)) }
        viewModelScope.launch {
            try {
                val alternatives = planRepository.swapAlternatives(mealId)
                if (pendingMealId != mealId) return@launch // sheet closed meanwhile
                pendingAlternatives = alternatives
                overlay.update {
                    it.copy(swap = SwapSheetState.Loaded(title, alternatives.mapIndexed { i, a -> swapOptionUi(i, a) }))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                closeSwap()
                messages.trySend("Non riesco a caricare le alternative.")
            }
        }
    }

    fun closeSwap() {
        pendingMealId = null
        pendingAlternatives = emptyList()
        overlay.update { it.copy(swap = null) }
    }

    fun applySwap(optionIndex: Int) {
        val mealId = pendingMealId ?: return
        val alternative = pendingAlternatives.getOrNull(optionIndex) ?: return
        if (!alternative.allowed) return
        closeSwap()
        runAction("Non riesco a cambiare il pasto.") {
            planRepository.applyAlternative(mealId, alternative.components)
            messages.trySend("Pasto cambiato")
        }
    }

    // ---- Helpers -------------------------------------------------------------------------------------------

    private fun runAction(errorMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            overlay.update { it.copy(isBusy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                messages.trySend(e.message ?: errorMessage) // domain guards carry an Italian message
            } catch (e: Exception) {
                messages.trySend(errorMessage)
            } finally {
                overlay.update { it.copy(isBusy = false) }
            }
        }
    }

    private companion object {
        const val KEY_SELECTED_DAY = "selected_day"
    }
}
