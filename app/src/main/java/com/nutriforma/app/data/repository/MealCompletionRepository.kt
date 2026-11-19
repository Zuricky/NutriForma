package com.nutriforma.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * "Completato" ticks of the diet screen.
 * DECISION: kept in its own DataStore file instead of a Room column, so the locked schema v1 stays untouched
 * (no migration needed). Meal ids are auto-increment and never reused, so a regenerated week simply starts unticked.
 */
class MealCompletionRepository(
    private val dataStore: DataStore<Preferences>,
    /** Chiamata dopo ogni spunta cambiata dall'utente (es. backup automatico). */
    private val onDataChanged: () -> Unit = {},
) {

    val completedMealIds: Flow<Set<Long>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[KEY].orEmpty().mapNotNull { it.toLongOrNull() }.toSet() }
        .distinctUntilChanged()

    suspend fun current(): Set<Long> = completedMealIds.first()

    suspend fun setCompleted(mealId: Long, completed: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY].orEmpty()
            prefs[KEY] = if (completed) current + mealId.toString() else current - mealId.toString()
        }
        onDataChanged()
    }

    /** Ripristino da backup: sostituisce tutte le spunte (non avvia il backup automatico). */
    suspend fun replaceAll(mealIds: Set<Long>) {
        dataStore.edit { prefs -> prefs[KEY] = mealIds.map { it.toString() }.toSet() }
    }

    companion object {
        const val DATASTORE_NAME = "meal_completion"
        private val KEY = stringSetPreferencesKey("completed_meal_ids")
    }
}
