package com.nutriforma.app.data.seed

import android.util.Log
import com.nutriforma.app.data.local.dao.FoodDao
import com.nutriforma.app.data.mapper.toEntity
import com.nutriforma.app.data.preferences.UserProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * Idempotent first-launch seeding of the food catalogue from `assets/foods_it.json`.
 *
 * Seeds when `profile.foodSeedVersion < file.seedVersion` **or** the table is empty; otherwise does nothing.
 */
class FoodSeeder(
    private val foodDao: FoodDao,
    private val userProfileRepository: UserProfileRepository,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
    private val openSeedFile: () -> InputStream,
    private val log: (String) -> Unit = { Log.i("NutriForma", it) },
) {

    /** @return number of foods written, or 0 when the catalogue was already up to date. */
    suspend fun seedIfNeeded(): Int = withContext(ioDispatcher) {
        val seed = openSeedFile().use { stream ->
            json.decodeFromString(FoodSeedFile.serializer(), stream.readBytes().decodeToString())
        }
        val storedVersion = userProfileRepository.current().foodSeedVersion
        if (storedVersion >= seed.seedVersion && foodDao.count() > 0) return@withContext 0

        val entities = seed.foods.mapIndexed { index, dto -> dto.toDomain().toEntity(sortOrder = index) }
        foodDao.upsertAll(entities)
        userProfileRepository.update { it.copy(foodSeedVersion = seed.seedVersion) }
        log("Seed: ${entities.size} alimenti")
        entities.size
    }
}
