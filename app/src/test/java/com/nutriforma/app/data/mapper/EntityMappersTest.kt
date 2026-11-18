package com.nutriforma.app.data.mapper

import com.nutriforma.app.data.local.dao.DailyWaterTotalRow
import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import com.nutriforma.app.data.local.entity.FoodItemEntity
import com.nutriforma.app.data.local.entity.PlannedMealEntity
import com.nutriforma.app.data.local.entity.WaterLogEntity
import com.nutriforma.app.data.local.entity.WeeklyPlanEntity
import com.nutriforma.app.domain.model.BodyMeasurement
import com.nutriforma.app.domain.model.DailyWaterTotal
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.SupermarketCategory
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import com.nutriforma.app.domain.model.WaterLog
import com.nutriforma.app.domain.model.WeekPlan
import com.nutriforma.app.domain.model.dayIndex
import com.nutriforma.app.domain.model.weekStart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EntityMappersTest {

    private val food = FoodItem(
        id = "uovo", nameIt = "Uovo", category = FoodCategory.UOVA,
        roles = setOf(MealRole.COLAZIONE_G1, MealRole.PRANZO_SECONDO, MealRole.CENA_SECONDO),
        gramsPerUnit = 55.0, kcalPer100g = 128.0, proteinPer100g = 12.4, carbsPer100g = 0.0, fatPer100g = 8.7,
        fiberPer100g = 0.0, supermarketCategory = SupermarketCategory.LATTICINI_UOVA,
        isPreferred = false, isExcludable = true,
    )

    private val components = listOf(
        PlannedComponent(MealRole.PRANZO_ANTIPASTO, "zucchine", 200.0, PortionUnit.GRAMMI, 200.0),
        PlannedComponent(MealRole.PRANZO_SECONDO, "uovo", 2.0, PortionUnit.PEZZI, 110.0),
    )

    private fun meal(id: Long, day: Int, slot: MealSlot) = PlannedMeal(
        id = id, planId = 7, dayIndex = day, slot = slot, type = MealType.PRANZO_STANDARD,
        components = components, isUserModified = id % 2 == 0L,
    )

    @Test
    fun foodRoundTripsAndKeepsSortOrder() {
        val entity = food.toEntity(sortOrder = 4)
        assertEquals(4, entity.sortOrder)
        assertEquals(food, entity.toDomain())
        val pastoLibero = food.copy(id = "pasto_libero", supermarketCategory = null, gramsPerUnit = null, isExcludable = false)
        assertEquals(pastoLibero, pastoLibero.toEntity(84).toDomain())
        val e: FoodItemEntity = pastoLibero.toEntity(84)
        assertEquals(null, e.supermarketCategory)
    }

    @Test
    fun plannedMealRoundTrips() {
        val m = meal(41, 0, MealSlot.PRANZO)
        assertEquals(m, m.toEntity().toDomain())
        val entity: PlannedMealEntity = m.toEntity()
        assertEquals(41L, entity.id)
        assertEquals(components, entity.components)
    }

    @Test
    fun weekPlanRoundTripsAndSortsMealsBySlotOrderNotAlphabetically() {
        val monday = LocalDate.of(2026, 11, 16)
        val turno = listOf(TurnoLungoDay(2, TurnoLungoOption.BARRETTA, MealSlot.META_MATTINA))
        val entity = WeeklyPlanEntity(
            id = 3, weekStartEpochDay = monday.toEpochDay(), seed = monday.toEpochDay(), goal = Goal.ENERGIA,
            sportLevel = SportLevel.INTENSO, cheatMealsPerWeek = 2, turnoLungoDays = turno,
            createdAtMillis = 1_000L, updatedAtMillis = 2_000L,
        )
        // SQL alphabetical slot order: CENA < COLAZIONE < META_MATTINA < META_POMERIGGIO < PRANZO.
        val shuffled = listOf(
            meal(5, 1, MealSlot.CENA), meal(1, 0, MealSlot.PRANZO), meal(2, 0, MealSlot.COLAZIONE),
            meal(4, 0, MealSlot.META_POMERIGGIO), meal(3, 0, MealSlot.META_MATTINA),
        )
        val plan: WeekPlan = entity.toDomain(shuffled)
        assertEquals(monday, plan.weekStart)
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L), plan.meals.map { it.id })
        assertEquals(entity, plan.toEntity())

        val fromEntities = shuffled.map { it.toEntity() }.toDomainSorted()
        assertEquals(plan.meals, fromEntities)
    }

    @Test
    fun bodyMeasurementRoundTrips() {
        val date = LocalDate.of(2026, 11, 17)
        val m = BodyMeasurement(date, weightKg = 78.4, waistCm = 84.0, thighCm = null, note = "dopo allenamento")
        val entity: BodyMeasurementEntity = m.toEntity(updatedAtMillis = 99L)
        assertEquals(date.toEpochDay(), entity.dateEpochDay)
        assertEquals(99L, entity.updatedAtMillis)
        assertEquals(m, entity.toDomain())
        val onlyThigh = BodyMeasurement(date, null, null, 56.5, null)
        assertEquals(onlyThigh, onlyThigh.toEntity(1L).toDomain())
    }

    @Test
    fun waterLogAndDailyTotalRoundTrip() {
        val log = WaterLog(id = 7, date = LocalDate.of(2026, 11, 17), timestampMillis = 1_794_000_000_000L, amountMl = 250)
        val entity: WaterLogEntity = log.toEntity()
        assertEquals(log.date.toEpochDay(), entity.dateEpochDay)
        assertEquals(log, entity.toDomain())
        val day = LocalDate.of(2026, 11, 16)
        assertEquals(DailyWaterTotal(day, 1800), DailyWaterTotalRow(day.toEpochDay(), 1800).toDomain())
    }

    @Test
    fun weekHelpers() {
        val wednesday = LocalDate.of(2026, 11, 18)
        assertEquals(LocalDate.of(2026, 11, 16), wednesday.weekStart())
        assertEquals(2, wednesday.dayIndex())
        assertEquals(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16).weekStart())
        assertEquals(6, LocalDate.of(2026, 11, 22).dayIndex())
        assertEquals(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22).weekStart())
    }
}
