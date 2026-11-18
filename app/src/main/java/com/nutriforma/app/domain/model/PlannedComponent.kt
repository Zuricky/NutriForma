package com.nutriforma.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PlannedComponent(
    val role: MealRole,
    val foodId: String,
    val quantity: Double,
    val unit: PortionUnit,
    val grams: Double,
    val isOptional: Boolean = false,
)
