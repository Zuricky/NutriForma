package com.nutriforma.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "shopping_checks",
    primaryKeys = ["planId", "foodId"],
    foreignKeys = [
        ForeignKey(
            entity = WeeklyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ShoppingCheckEntity(
    val planId: Long,
    val foodId: String,
    val isChecked: Boolean,
    val updatedAtMillis: Long,
)
