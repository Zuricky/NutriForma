package com.nutriforma.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nutriforma.app.data.local.entity.PlannedMealEntity
import kotlinx.coroutines.flow.Flow

/**
 * NOTE: `slot` is stored as TEXT, so SQL `ORDER BY slot` is alphabetical. Mappers/repositories MUST re-sort
 * meals by `(dayIndex, slot.order)` before exposing them (see `sortedForDisplay()` in EntityMappers).
 */
@Dao
interface PlannedMealDao {
    @Insert
    suspend fun insertAll(meals: List<PlannedMealEntity>): List<Long>

    @Update
    suspend fun update(meal: PlannedMealEntity)

    @Update
    suspend fun updateAll(meals: List<PlannedMealEntity>)

    @Query("SELECT * FROM planned_meals WHERE planId = :planId ORDER BY dayIndex, slot")
    fun observeForPlan(planId: Long): Flow<List<PlannedMealEntity>>

    @Query("SELECT * FROM planned_meals WHERE planId = :planId ORDER BY dayIndex, slot")
    suspend fun getForPlan(planId: Long): List<PlannedMealEntity>

    @Query("SELECT * FROM planned_meals WHERE id = :id")
    suspend fun getById(id: Long): PlannedMealEntity?

    @Query("DELETE FROM planned_meals WHERE planId = :planId")
    suspend fun deleteForPlan(planId: Long)

    @Query("SELECT * FROM planned_meals")
    suspend fun getAll(): List<PlannedMealEntity>
}
