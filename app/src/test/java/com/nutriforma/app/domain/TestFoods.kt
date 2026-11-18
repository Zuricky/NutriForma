package com.nutriforma.app.domain

import com.nutriforma.app.data.seed.FoodSeedFile
import com.nutriforma.app.data.seed.toDomain
import com.nutriforma.app.domain.generator.GenerationInput
import com.nutriforma.app.domain.generator.GenerationResult
import com.nutriforma.app.domain.generator.WeeklyPlanGenerator
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.nutrition.PortionCalculator
import com.nutriforma.app.domain.generator.CategoryTemplates
import com.nutriforma.app.domain.rules.MealStructure
import com.nutriforma.app.domain.rules.WeeklyCounter
import com.nutriforma.app.domain.rules.WeeklyCounts
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.time.LocalDate

private val seedJson = Json { ignoreUnknownKeys = true }

/** The real seed catalogue (`src/main/assets/foods_it.json`) as domain foods; tests run with `app/` as cwd. */
object TestFoods {
    val all: List<FoodItem> by lazy {
        val text = File("src/main/assets/foods_it.json").readText()
        seedJson.decodeFromString(FoodSeedFile.serializer(), text).foods.map { it.toDomain() }
    }
    val byId: Map<String, FoodItem> by lazy { all.associateBy { it.id } }

    fun idsOf(category: FoodCategory): Set<String> = all.filter { it.category == category }.map { it.id }.toSet()
}

/** Monday used by the acceptance tests. */
val MONDAY: LocalDate = LocalDate.of(2026, 11, 16)

private val sharedGenerator = WeeklyPlanGenerator()
private val portions = PortionCalculator()

fun generate(
    seed: Long = MONDAY.toEpochDay(),
    profile: UserProfile = UserProfile(),
    turnoLungo: List<TurnoLungoDay> = emptyList(),
    foods: List<FoodItem> = TestFoods.all,
): GenerationResult = sharedGenerator.generateWeek(GenerationInput(MONDAY, profile, foods, seed, turnoLungo))

fun countsOf(meals: List<PlannedMeal>): WeeklyCounts = WeeklyCounter.count(meals, TestFoods.byId)

fun List<PlannedMeal>.meal(day: Int, slot: MealSlot): PlannedMeal = first { it.dayIndex == day && it.slot == slot }

fun PlannedMeal.component(role: MealRole): PlannedComponent? = components.firstOrNull { it.role == role }

fun PlannedMeal.foodIds(): List<String> = components.map { it.foodId }

/** Components of a meal must match SPEC §2: exactly the required roles + optional ones, in structure order. */
fun assertMealStructure(meal: PlannedMeal) {
    val spec = MealStructure.specFor(meal.type)
    val expectedOrder = spec.map { it.role }
    val roles = meal.components.map { it.role }
    assertEquals("ruoli duplicati in ${meal.type} giorno ${meal.dayIndex}", roles.size, roles.toSet().size)
    assertTrue("ruolo non ammesso in ${meal.type}: $roles", roles.all { it in expectedOrder })
    assertEquals("ordine componenti ${meal.type}", expectedOrder.filter { it in roles }, roles)
    spec.filter { it.required }.forEach {
        assertTrue("ruolo obbligatorio ${it.role} mancante in ${meal.type} giorno ${meal.dayIndex}", it.role in roles)
    }
}

/** A week of 35 empty meals with the fixed types of SPEC §7.2 (no turno lungo, `cheat` pasti liberi). */
fun blankWeek(cheat: Int = 1): List<PlannedMeal> {
    val libero = if (cheat >= 2) setOf(2, 5) else setOf(5)
    val dolce = setOf(2, 6)
    return (0..6).flatMap { day ->
        listOf(
            PlannedMeal(0, 0, day, MealSlot.COLAZIONE, if (day in dolce) MealType.COLAZIONE_DOLCE else MealType.COLAZIONE_STANDARD, emptyList(), false),
            PlannedMeal(0, 0, day, MealSlot.META_MATTINA, MealType.SPUNTINO_MATTINA, emptyList(), false),
            PlannedMeal(0, 0, day, MealSlot.PRANZO, if (day in libero) MealType.PRANZO_LIBERO else MealType.PRANZO_STANDARD, emptyList(), false),
            PlannedMeal(0, 0, day, MealSlot.META_POMERIGGIO, MealType.SPUNTINO_POMERIGGIO, emptyList(), false),
            PlannedMeal(0, 0, day, MealSlot.CENA, CategoryTemplates.CENA_TYPES[day], emptyList(), false),
        )
    }
}

/** Replaces the components of one meal (portions from [PortionCalculator] with the default profile). */
fun List<PlannedMeal>.with(
    day: Int, slot: MealSlot, vararg parts: Pair<MealRole, String>, type: MealType? = null,
    profile: UserProfile = UserProfile(),
): List<PlannedMeal> = map { m ->
    if (m.dayIndex != day || m.slot != slot) {
        m
    } else {
        val comps = parts.map { (role, foodId) ->
            val p = portions.portionFor(role, TestFoods.byId.getValue(foodId), profile)
            PlannedComponent(role, foodId, p.quantity, p.unit, p.grams)
        }
        m.copy(type = type ?: m.type, components = comps)
    }
}

fun List<PlannedMeal>.withType(day: Int, slot: MealSlot, type: MealType): List<PlannedMeal> =
    map { if (it.dayIndex == day && it.slot == slot) it.copy(type = type) else it }
