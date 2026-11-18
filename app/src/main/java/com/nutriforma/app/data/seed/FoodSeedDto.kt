package com.nutriforma.app.data.seed

import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.SupermarketCategory
import kotlinx.serialization.Serializable

/** Root of `assets/foods_it.json`. */
@Serializable
data class FoodSeedFile(
    val seedVersion: Int,
    val foods: List<FoodSeedDto>,
)

/** One food of the seed file. Enums are kept as strings so a bad value is reported with its food id. */
@Serializable
data class FoodSeedDto(
    val id: String,
    val nameIt: String,
    val category: String,
    val roles: List<String>,
    val gramsPerUnit: Double? = null,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val supermarket: String? = null,
    val preferred: Boolean,
    val excludable: Boolean,
)

/** @throws IllegalArgumentException if an enum string does not match any constant. */
fun FoodSeedDto.toDomain(): FoodItem = FoodItem(
    id = id,
    nameIt = nameIt,
    category = enumOf<FoodCategory>(category, "category", id),
    roles = roles.map { enumOf<MealRole>(it, "role", id) }.toSet(),
    gramsPerUnit = gramsPerUnit,
    kcalPer100g = kcal,
    proteinPer100g = protein,
    carbsPer100g = carbs,
    fatPer100g = fat,
    fiberPer100g = fiber,
    supermarketCategory = supermarket?.let { enumOf<SupermarketCategory>(it, "supermarket", id) },
    isPreferred = preferred,
    isExcludable = excludable,
)

private inline fun <reified E : Enum<E>> enumOf(value: String, field: String, foodId: String): E =
    enumValues<E>().firstOrNull { it.name == value }
        ?: throw IllegalArgumentException("Alimento '$foodId': $field '$value' non valido")
