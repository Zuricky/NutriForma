package com.nutriforma.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.TurnoLungoDay

@Entity(tableName = "weekly_plans", indices = [Index(value = ["weekStartEpochDay"], unique = true)])
data class WeeklyPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Monday. */
    val weekStartEpochDay: Long,
    val seed: Long,
    val goal: Goal,
    val sportLevel: SportLevel,
    val cheatMealsPerWeek: Int,
    /** Converters: JSON. */
    val turnoLungoDays: List<TurnoLungoDay>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
