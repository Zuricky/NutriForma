package com.nutriforma.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

/** Voce extra scritta a mano (mai alimenti della dieta: il catalogo resta in sola lettura). */
@Serializable
data class ShoppingExtra(val id: String, val name: String, val isChecked: Boolean = false)

/**
 * Extra manuali della spesa.
 * DECISION: salvati in un DataStore dedicato (JSON) invece che in Room, così lo schema v1 resta invariato.
 */
class ShoppingExtrasRepository(private val dataStore: DataStore<Preferences>) {

    val extras: Flow<List<ShoppingExtra>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> decode(prefs[KEY]) }
        .distinctUntilChanged()

    /** Aggiunge un extra; ignora testi vuoti. Restituisce `false` se il nome è vuoto. */
    suspend fun add(name: String): Boolean {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)
        if (clean.isEmpty()) return false
        update { it + ShoppingExtra(UUID.randomUUID().toString(), clean) }
        return true
    }

    suspend fun setChecked(id: String, checked: Boolean) =
        update { list -> list.map { if (it.id == id) it.copy(isChecked = checked) else it } }

    suspend fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

    suspend fun clear() = update { emptyList() }

    private suspend fun update(transform: (List<ShoppingExtra>) -> List<ShoppingExtra>) {
        dataStore.edit { prefs -> prefs[KEY] = encode(transform(decode(prefs[KEY]))) }
    }

    private fun decode(raw: String?): List<ShoppingExtra> =
        if (raw.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

    private fun encode(list: List<ShoppingExtra>): String = json.encodeToString(serializer, list)

    companion object {
        const val DATASTORE_NAME = "shopping_extras"
        const val MAX_NAME_LENGTH = 60
        private val KEY = stringPreferencesKey("extras_json")
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(ShoppingExtra.serializer())
    }
}
