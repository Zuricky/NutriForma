package com.nutriforma.app.domain.model

data class PlannedMeal(
    val id: Long,
    val planId: Long,
    /** 0 = Lunedì … 6 = Domenica. */
    val dayIndex: Int,
    val slot: MealSlot,
    val type: MealType,
    val components: List<PlannedComponent>,
    val isUserModified: Boolean,
)
