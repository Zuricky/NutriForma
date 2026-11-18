package com.nutriforma.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {
    @Upsert
    suspend fun upsert(m: BodyMeasurementEntity)

    @Query("DELETE FROM body_measurements WHERE dateEpochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("SELECT * FROM body_measurements ORDER BY dateEpochDay")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE weightKg IS NOT NULL ORDER BY dateEpochDay DESC LIMIT 1")
    fun observeLatestWeight(): Flow<BodyMeasurementEntity?>

    @Query("SELECT * FROM body_measurements")
    suspend fun getAll(): List<BodyMeasurementEntity>

    @Insert
    suspend fun insertAll(items: List<BodyMeasurementEntity>)

    @Query("DELETE FROM body_measurements")
    suspend fun deleteAll()
}
