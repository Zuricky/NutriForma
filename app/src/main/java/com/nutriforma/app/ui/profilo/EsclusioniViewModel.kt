package com.nutriforma.app.ui.profilo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.data.repository.FoodRepository
import com.nutriforma.app.data.repository.PlanRepository
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.dayIndex
import com.nutriforma.app.domain.rules.ExclusionAdvisor
import com.nutriforma.app.ui.food.FoodIcon
import com.nutriforma.app.ui.food.FoodIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate

data class FoodRowUi(val id: String, val name: String, val excluded: Boolean, val icon: FoodIcon)

data class FoodGroupUi(val label: String, val rows: List<FoodRowUi>)

data class EsclusioniUiState(
    val query: String = "",
    val groups: List<FoodGroupUi> = emptyList(),
    val excludedCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val isLoading: Boolean = true,
)

private fun String.fold(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Alimenti del catalogo (solo escludibili) raggruppati per categoria e filtrati dalla ricerca (nome o categoria). */
fun buildExclusionGroups(foods: List<FoodItem>, disliked: Set<String>, query: String): List<FoodGroupUi> {
    val q = query.trim().fold()
    return foods.filter { it.isExcludable }
        .filter { q.isEmpty() || it.nameIt.fold().contains(q) || it.category.labelIt.fold().contains(q) }
        .groupBy { it.category }
        .toSortedMap(compareBy { it.ordinal })
        .map { (category, items) ->
            FoodGroupUi(category.labelIt, items.map { FoodRowUi(it.id, it.nameIt, it.id in disliked, FoodIcons.forFood(it)) })
        }
}

class EsclusioniViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val foodRepository: FoodRepository,
    private val planRepository: PlanRepository,
    /** Le esclusioni devono arrivare fino alla dieta anche se si esce subito dalla schermata. */
    private val appScope: CoroutineScope,
    private val clock: Clock,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val messages = Channel<String>(Channel.BUFFERED)

    /** Messaggi (snackbar) one-off in italiano. */
    val events = messages.receiveAsFlow()

    val uiState: StateFlow<EsclusioniUiState> = combine(foodRepository.observeFoods(), userProfileRepository.profile, query) { foods, profile, q ->
        EsclusioniUiState(
            query = q,
            groups = buildExclusionGroups(foods, profile.dislikedFoodIds, q),
            excludedCount = profile.dislikedFoodIds.size,
            warnings = ExclusionAdvisor.warnings(foods, profile.dislikedFoodIds),
            isLoading = foods.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EsclusioniUiState())

    fun setQuery(text: String) {
        query.value = text.take(40)
    }

    /** Esclude / riammette un alimento e riscrive subito i pasti da oggi in poi che lo contengono. */
    fun toggle(foodId: String) {
        appScope.launch {
            try {
                userProfileRepository.update {
                    it.copy(dislikedFoodIds = if (foodId in it.dislikedFoodIds) it.dislikedFoodIds - foodId else it.dislikedFoodIds + foodId)
                }
                val today = LocalDate.now(clock)
                val plan = planRepository.getOrCreateWeek(today)
                val warnings = planRepository.applyExclusions(plan.id, today.dayIndex())
                warnings.firstOrNull()?.let { messages.trySend(it.messageIt) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.trySend("Non riesco ad aggiornare la dieta con questa esclusione.")
            }
        }
    }
}
