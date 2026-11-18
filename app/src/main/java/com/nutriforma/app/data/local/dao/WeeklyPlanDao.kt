package com.nutriforma.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nutriforma.app.data.local.entity.WeeklyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyPlanDao {
    @Insert
    suspend fun insert(plan: WeeklyPlanEntity): Long

    @Update
    suspend fun update(plan: WeeklyPlanEntity)

    @Query("SELECT * FROM weekly_plans WHERE weekStartEpochDay = :epochDay")
    suspend fun getByWeekStart(epochDay: Long): WeeklyPlanEntity?

    @Query("SELECT * FROM weekly_plans WHERE weekStartEpochDay = :epochDay")
    fun observeByWeekStart(epochDay: Long): Flow<WeeklyPlanEntity?>

    @Query("SELECT * FROM weekly_plans WHERE id = :id")
    suspend fun getById(id: Long): WeeklyPlanEntity?

    @Query("SELECT * FROM weekly_plans ORDER BY weekStartEpochDay")
    suspend fun getAll(): List<WeeklyPlanEntity>

    @Insert
    suspend fun insertAll(plans: List<WeeklyPlanEntity>)

    @Query("DELETE FROM weekly_plans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM weekly_plans")
    suspend fun deleteAll()
}
