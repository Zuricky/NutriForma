package com.nutriforma.app.data.mapper

import com.nutriforma.app.data.local.dao.DailyWaterTotalRow
import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import com.nutriforma.app.data.local.entity.FoodItemEntity
import com.nutriforma.app.data.local.entity.PlannedMealEntity
import com.nutriforma.app.data.local.entity.WaterLogEntity
import com.nutriforma.app.data.local.entity.WeeklyPlanEntity
import com.nutriforma.app.domain.model.BodyMeasurement
import com.nutriforma.app.domain.model.DailyWaterTotal
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.WaterLog
import com.nutriforma.app.domain.model.WeekPlan
import java.time.LocalDate

// ---- Food -------------------------------------------------------------------------------------------------

fun FoodItemEntity.toDomain() = FoodItem(
    id = id, nameIt = nameIt, category = category, roles = roles, gramsPerUnit = gramsPerUnit,
    kcalPer100g = kcalPer100g, proteinPer100g = proteinPer100g, carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g, fiberPer100g = fiberPer100g, supermarketCategory = supermarketCategory,
    isPreferred = isPreferred, isExcludable = isExcludable,
)

fun FoodItem.toEntity(sortOrder: Int) = FoodItemEntity(
    id = id, nameIt = nameIt, category = category, roles = roles, gramsPerUnit = gramsPerUnit,
    kcalPer100g = kcalPer100g, proteinPer100g = proteinPer100g, carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g, fiberPer100g = fiberPer100g, supermarketCategory = supermarketCategory,
    isPreferred = isPreferred, isExcludable = isExcludable, sortOrder = sortOrder,
)

// ---- Plans & meals ----------------------------------------------------------------------------------------

fun PlannedMealEntity.toDomain() = PlannedMeal(
    id = id, planId = planId, dayIndex = dayIndex, slot = slot, type = type,
    components = components, isUserModified = isUserModified,
)

fun PlannedMeal.toEntity() = PlannedMealEntity(
    id = id, planId = planId, dayIndex = dayIndex, slot = slot, type = type,
    components = components, isUserModified = isUserModified,
)

/** `slot` is TEXT in SQL, so the DAO order is alphabetical: always re-sort by `(dayIndex, slot.order)`. */
fun List<PlannedMeal>.sortedForDisplay(): List<PlannedMeal> = sortedWith(compareBy({ it.dayIndex }, { it.slot.order }))

fun List<PlannedMealEntity>.toDomainSorted(): List<PlannedMeal> = map { it.toDomain() }.sortedForDisplay()

fun WeeklyPlanEntity.toDomain(meals: List<PlannedMeal>) = WeekPlan(
    id = id, weekStart = LocalDate.ofEpochDay(weekStartEpochDay), seed = seed, goal = goal, sportLevel = sportLevel,
    cheatMealsPerWeek = cheatMealsPerWeek, turnoLungoDays = turnoLungoDays, meals = meals.sortedForDisplay(),
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

/** Meals are persisted separately (see [PlannedMeal.toEntity]). */
fun WeekPlan.toEntity() = WeeklyPlanEntity(
    id = id, weekStartEpochDay = weekStart.toEpochDay(), seed = seed, goal = goal, sportLevel = sportLevel,
    cheatMealsPerWeek = cheatMealsPerWeek, turnoLungoDays = turnoLungoDays,
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

// ---- Tracking ---------------------------------------------------------------------------------------------

fun BodyMeasurementEntity.toDomain() = BodyMeasurement(
    date = LocalDate.ofEpochDay(dateEpochDay), weightKg = weightKg, waistCm = waistCm, thighCm = thighCm, note = note,
)

fun BodyMeasurement.toEntity(updatedAtMillis: Long) = BodyMeasurementEntity(
    dateEpochDay = date.toEpochDay(), weightKg = weightKg, waistCm = waistCm, thighCm = thighCm,
    note = note, updatedAtMillis = updatedAtMillis,
)

fun WaterLogEntity.toDomain() = WaterLog(
    id = id, date = LocalDate.ofEpochDay(dateEpochDay), timestampMillis = timestampMillis, amountMl = amountMl,
)

fun WaterLog.toEntity() = WaterLogEntity(
    id = id, dateEpochDay = date.toEpochDay(), timestampMillis = timestampMillis, amountMl = amountMl,
)

fun DailyWaterTotalRow.toDomain() = DailyWaterTotal(date = LocalDate.ofEpochDay(dateEpochDay), totalMl = totalMl)
