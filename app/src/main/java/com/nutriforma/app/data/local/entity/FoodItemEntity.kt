package com.nutriforma.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.SupermarketCategory

@Entity(tableName = "food_items", indices = [Index("category")])
data class FoodItemEntity(
    @PrimaryKey val id: String,
    val nameIt: String,
    val category: FoodCategory,
    /** Converters: JSON array of names. */
    val roles: Set<MealRole>,
    val gramsPerUnit: Double?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double,
    val supermarketCategory: SupermarketCategory?,
    val isPreferred: Boolean,
    val isExcludable: Boolean,
    /** Index in the seed file. */
    val sortOrder: Int,
)
