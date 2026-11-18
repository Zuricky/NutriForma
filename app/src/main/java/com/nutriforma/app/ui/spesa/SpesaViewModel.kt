package com.nutriforma.app.ui.spesa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriforma.app.data.repository.FoodRepository
import com.nutriforma.app.data.repository.PlanRepository
import com.nutriforma.app.data.repository.ShoppingExtrasRepository
import com.nutriforma.app.data.repository.ShoppingRepository
import com.nutriforma.app.domain.model.dayIndex
import com.nutriforma.app.domain.shopping.DayRange
import com.nutriforma.app.domain.shopping.ShoppingListGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

private data class SpesaLocal(
    val wholeWeek: Boolean,
    val selectedDays: Set<Int>,
    val confirmClear: Boolean = false,
    val bootstrapError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpesaViewModel(
    private val planRepository: PlanRepository,
    private val foodRepository: FoodRepository,
    private val shoppingRepository: ShoppingRepository,
    private val extrasRepository: ShoppingExtrasRepository,
    private val generator: ShoppingListGenerator,
    clock: Clock,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)
    private val local = MutableStateFlow(SpesaLocal(wholeWeek = true, selectedDays = setOf(today.dayIndex())))
    private var currentPlanId: Long? = null

    private val messages = Channel<String>(Channel.BUFFERED)
    /** Messaggi (snackbar) one-off in italiano. */
    val events = messages.receiveAsFlow()

    private val plan = planRepository.observeWeek(today)
    private val checkedIds = plan.map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(emptySet()) else shoppingRepository.observeCheckedFoodIds(id)
    }

    val uiState: StateFlow<SpesaUiState> = combine(
        plan, foodRepository.observeFoods(), checkedIds, extrasRepository.extras, local,
    ) { plan, foods, checked, extras, state ->
        currentPlanId = plan?.id
        when {
            plan == null || foods.isEmpty() ->
                state.bootstrapError?.let { SpesaUiState.Error(it) } ?: SpesaUiState.Loading
            else -> {
                val range = if (state.wholeWeek) DayRange.WholeWeek else DayRange.Days(state.selectedDays)
                val items = generator.generate(plan.meals, foods.associateBy { it.id }, checked, range)
                // Gli extra manuali non dipendono dai giorni scelti: restano sempre visibili.
                SpesaUiState.Ready(state.wholeWeek, state.selectedDays, buildSpesaContent(items, extras), state.confirmClear)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpesaUiState.Loading)

    init {
        ensurePlan()
    }

    /** La scheda Spesa può aprirsi prima della Dieta: crea la settimana se manca (idempotente). */
    fun ensurePlan() {
        local.update { it.copy(bootstrapError = null) }
        viewModelScope.launch {
            try {
                foodRepository.observeFoods().first { it.isNotEmpty() }
                planRepository.getOrCreateWeek(today)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(bootstrapError = "Non riesco a preparare la lista della spesa. Riprova.") }
            }
        }
    }

    // ---- Filtro giorni -------------------------------------------------------------------------------------

    fun setWholeWeek(wholeWeek: Boolean) = local.update { it.copy(wholeWeek = wholeWeek) }

    fun toggleDay(dayIndex: Int) = local.update {
        val days = if (dayIndex in it.selectedDays) it.selectedDays - dayIndex else it.selectedDays + dayIndex
        it.copy(selectedDays = days)
    }

    // ---- Spunte / extra ------------------------------------------------------------------------------------

    fun setChecked(key: String, checked: Boolean) {
        launchSafely("Non riesco ad aggiornare la lista.") {
            when {
                key.startsWith(FOOD_KEY_PREFIX) -> {
                    val planId = currentPlanId ?: return@launchSafely
                    shoppingRepository.setChecked(planId, key.removePrefix(FOOD_KEY_PREFIX), checked)
                }
                key.startsWith(EXTRA_KEY_PREFIX) -> extrasRepository.setChecked(key.removePrefix(EXTRA_KEY_PREFIX), checked)
            }
        }
    }

    fun addExtra(name: String) {
        launchSafely("Non riesco ad aggiungere la voce.") {
            if (!extrasRepository.add(name)) messages.trySend("Scrivi il nome della voce da aggiungere.")
        }
    }

    fun removeExtra(key: String) {
        if (!key.startsWith(EXTRA_KEY_PREFIX)) return
        launchSafely("Non riesco a rimuovere la voce.") { extrasRepository.remove(key.removePrefix(EXTRA_KEY_PREFIX)) }
    }

    // ---- Svuota tutto --------------------------------------------------------------------------------------

    fun requestClear() = local.update { it.copy(confirmClear = true) }

    fun dismissClear() = local.update { it.copy(confirmClear = false) }

    /** Azzera le spunte (tutto torna in "Da comprare") e cancella gli extra manuali. */
    fun confirmClear() {
        local.update { it.copy(confirmClear = false) }
        launchSafely("Non riesco a svuotare la lista.") {
            currentPlanId?.let { shoppingRepository.clearChecks(it) }
            extrasRepository.clear()
            messages.trySend("Lista azzerata")
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
