package com.nutriforma.app.ui

import com.nutriforma.app.data.repository.FoodRepository
import com.nutriforma.app.data.repository.PlanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * Il "oggi" di una schermata. Va riletto dall'orologio quando l'app torna in primo piano o scatta la mezzanotte:
 * un valore letto una sola volta alla creazione del ViewModel resterebbe quello di ieri.
 */
class TodayState(private val clock: Clock) {
    private val _today = MutableStateFlow(LocalDate.now(clock))
    val today: StateFlow<LocalDate> = _today

    /** Rilegge la data; restituisce `true` se è cambiata rispetto all'ultima lettura. */
    fun refresh(): Boolean {
        val now = LocalDate.now(clock)
        val changed = now != _today.value
        _today.value = now
        return changed
    }
}

/**
 * Tiene sempre presente la settimana di "oggi" nel database: se manca (primo avvio, nuova settimana, dopo un
 * ripristino che non la conteneva) la crea non appena il catalogo alimenti è pronto, così le schermate che la
 * osservano non restano ferme su "Caricamento". [onResult] riceve `null` se la settimana è pronta, altrimenti l'errore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.keepCurrentWeekPresent(
    today: Flow<LocalDate>,
    planRepository: PlanRepository,
    foodRepository: FoodRepository,
    onResult: (Throwable?) -> Unit,
): Job = launch {
    val foodsReady = foodRepository.observeFoods().map { it.isNotEmpty() }.distinctUntilChanged()
    combine(today, foodsReady) { day, ready -> day to ready }
        .flatMapLatest { (day, ready) ->
            if (!ready) {
                emptyFlow()
            } else {
                planRepository.observeWeek(day).map { it == null }.distinctUntilChanged().filter { missing -> missing }.map { day }
            }
        }
        .collect { day ->
            try {
                planRepository.getOrCreateWeek(day)
                onResult(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(e)
            }
        }
}
