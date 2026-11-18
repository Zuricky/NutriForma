package com.nutriforma.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "body_measurements")
data class BodyMeasurementEntity(
    /** One row per day (upsert replaces). */
    @PrimaryKey val dateEpochDay: Long,
    /** At least one non-null (validated in the repository). */
    val weightKg: Double?,
    val waistCm: Double?,
    val thighCm: Double?,
    val note: String?,
    val updatedAtMillis: Long,
)
