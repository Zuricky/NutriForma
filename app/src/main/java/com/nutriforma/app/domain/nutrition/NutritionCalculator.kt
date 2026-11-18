package com.nutriforma.app.domain.nutrition

import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.NutritionTotals
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal

/** Totals of one day; [includesPastoLibero] → the UI appends "(pasto libero escluso)". */
data class DayNutrition(
    val dayIndex: Int,
    val totals: NutritionTotals,
    val includesPastoLibero: Boolean,
)

/**
 * SPEC §6.2. Component grams are already scaled for the sport level by [PortionCalculator] when the
 * plan is generated, so the totals here are the "sporty portion" totals. Intermediate sums are never rounded.
 */
class NutritionCalculator {

    fun component(component: PlannedComponent, foodsById: Map<String, FoodItem>): NutritionTotals {
        val food = foodsById[component.foodId] ?: return NutritionTotals.ZERO
        val f = component.grams / 100.0
        return NutritionTotals(
            kcal = food.kcalPer100g * f,
            proteinG = food.proteinPer100g * f,
            carbsG = food.carbsPer100g * f,
            fatG = food.fatPer100g * f,
            fiberG = food.fiberPer100g * f,
        )
    }

    fun meal(meal: PlannedMeal, foodsById: Map<String, FoodItem>): NutritionTotals =
        meal.components.fold(NutritionTotals.ZERO) { acc, c -> acc + component(c, foodsById) }

    fun day(meals: List<PlannedMeal>, dayIndex: Int, foodsById: Map<String, FoodItem>): DayNutrition {
        val dayMeals = meals.filter { it.dayIndex == dayIndex }
        return DayNutrition(
            dayIndex = dayIndex,
            totals = dayMeals.fold(NutritionTotals.ZERO) { acc, m -> acc + meal(m, foodsById) },
            includesPastoLibero = dayMeals.any { it.type == MealType.PRANZO_LIBERO },
        )
    }

    fun week(meals: List<PlannedMeal>, foodsById: Map<String, FoodItem>): List<DayNutrition> =
        (0..6).map { day(meals, it, foodsById) }

    fun weekAverage(meals: List<PlannedMeal>, foodsById: Map<String, FoodItem>): NutritionTotals {
        val sum = week(meals, foodsById).fold(NutritionTotals.ZERO) { acc, d -> acc + d.totals }
        return NutritionTotals(sum.kcal / 7, sum.proteinG / 7, sum.carbsG / 7, sum.fatG / 7, sum.fiberG / 7)
    }
}
