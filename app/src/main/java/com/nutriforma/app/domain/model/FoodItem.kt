package com.nutriforma.app.domain.model

data class FoodItem(
    val id: String,
    val nameIt: String,
    val category: FoodCategory,
    val roles: Set<MealRole>,
    /** Grams per natural unit (pz, cucchiaino, tazzina…); null for gram-only foods. */
    val gramsPerUnit: Double?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double,
    /** null = not on the shopping list (pasto_libero). */
    val supermarketCategory: SupermarketCategory?,
    /** "Preferibilmente" (integrale, carni bianche…) → FoodPicker weight 2. */
    val isPreferred: Boolean,
    /** false only for pasto_libero. */
    val isExcludable: Boolean,
)
