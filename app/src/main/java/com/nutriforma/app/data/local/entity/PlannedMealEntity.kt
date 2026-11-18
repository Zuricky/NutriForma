package com.nutriforma.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent

@Entity(
    tableName = "planned_meals",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["planId", "dayIndex", "slot"], unique = true)],
)
data class PlannedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    /** 0 = Lunedì … 6 = Domenica. */
    val dayIndex: Int,
    val slot: MealSlot,
    val type: MealType,
    /** Converters: JSON. */
    val components: List<PlannedComponent>,
    val isUserModified: Boolean = false,
)
