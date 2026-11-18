package com.nutriforma.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "water_logs", indices = [Index("dateEpochDay")])
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val timestampMillis: Long,
    /** 50..1000 */
    val amountMl: Int,
)
