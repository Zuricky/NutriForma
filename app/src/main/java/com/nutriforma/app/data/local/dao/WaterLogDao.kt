package com.nutriforma.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nutriforma.app.data.local.entity.WaterLogEntity
import kotlinx.coroutines.flow.Flow

/** Projection of the per-day water totals query. */
data class DailyWaterTotalRow(val dateEpochDay: Long, val totalMl: Int)

@Dao
interface WaterLogDao {
    @Insert
    suspend fun insert(log: WaterLogEntity): Long

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM water_logs WHERE dateEpochDay = :epochDay ORDER BY timestampMillis")
    fun observeForDay(epochDay: Long): Flow<List<WaterLogEntity>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_logs WHERE dateEpochDay = :epochDay")
    fun observeTotalForDay(epochDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_logs WHERE dateEpochDay = :epochDay")
    suspend fun totalForDay(epochDay: Long): Int

    @Query(
        "SELECT dateEpochDay, SUM(amountMl) AS totalMl FROM water_logs " +
            "WHERE dateEpochDay >= :fromEpochDay GROUP BY dateEpochDay ORDER BY dateEpochDay",
    )
    fun observeDailyTotals(fromEpochDay: Long): Flow<List<DailyWaterTotalRow>>

    @Query("SELECT * FROM water_logs")
    suspend fun getAll(): List<WaterLogEntity>

    @Insert
    suspend fun insertAll(items: List<WaterLogEntity>)

    @Query("DELETE FROM water_logs")
    suspend fun deleteAll()
}
