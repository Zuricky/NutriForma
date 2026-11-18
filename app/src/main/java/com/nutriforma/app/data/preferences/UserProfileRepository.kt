package com.nutriforma.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nutriforma.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Single-profile store on DataStore Preferences (file `user_profile`).
 * All keys are written inside one `dataStore.edit`, so readers never observe a half-updated profile.
 */
class UserProfileRepository(private val dataStore: DataStore<Preferences>) {

    val profile: Flow<UserProfile> = dataStore.data
        .catch { error ->
            // A corrupted/unreadable file must not crash the app: fall back to the defaults.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toProfile() }
        .distinctUntilChanged()

    suspend fun current(): UserProfile = profile.first()

    suspend fun update(transform: (UserProfile) -> UserProfile) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toProfile())
            prefs.writeAll(UserProfileMapper.toMap(updated))
        }
    }

    /** Backup restore: replaces the whole profile (the caller preserves device-specific fields). */
    suspend fun replaceAll(profile: UserProfile) {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs.writeAll(UserProfileMapper.toMap(profile))
        }
    }

    private fun Preferences.toProfile(): UserProfile =
        UserProfileMapper.fromMap(asMap().entries.associate { (key, value) -> key.name to value })

    private fun MutablePreferences.writeAll(values: Map<String, Any?>) {
        for ((name, value) in values) {
            when (value) {
                // DECISION: Preferences.Key equality is by name only, so removing through a String key
                // removes the entry whatever its stored type.
                null -> remove(stringPreferencesKey(name))
                is Boolean -> this[booleanPreferencesKey(name)] = value
                is Int -> this[intPreferencesKey(name)] = value
                is Long -> this[longPreferencesKey(name)] = value
                is String -> this[stringPreferencesKey(name)] = value
                is Set<*> -> this[stringSetPreferencesKey(name)] = value.filterIsInstance<String>().toSet()
                else -> error("Tipo non supportato per la chiave $name: ${value::class}")
            }
        }
    }
}
