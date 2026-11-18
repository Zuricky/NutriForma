package com.nutriforma.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.nutriforma.app.data.local.dao.FoodDao
import com.nutriforma.app.data.local.entity.FoodItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File

/** Hand-written in-memory [FoodDao] (no Mockito/Robolectric). */
class FakeFoodDao : FoodDao {
    private val rows = MutableStateFlow<List<FoodItemEntity>>(emptyList())
    var upsertCalls = 0
        private set

    override suspend fun upsertAll(items: List<FoodItemEntity>) {
        upsertCalls++
        val merged = rows.value.associateBy { it.id }.toMutableMap()
        items.forEach { merged[it.id] = it }
        rows.value = merged.values.sortedBy { it.sortOrder }
    }

    override fun observeAll(): Flow<List<FoodItemEntity>> = rows.map { it }

    override suspend fun getAll(): List<FoodItemEntity> = rows.value

    override suspend fun count(): Int = rows.value.size

    fun clear() {
        rows.value = emptyList()
    }
}

/** A real file-backed DataStore (works on the JVM) living in [dir]; cancel [scope] when the test ends. */
class TestDataStore(dir: File, name: String = "user_profile_test") {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(dir, "$name.preferences_pb") },
    )
}
