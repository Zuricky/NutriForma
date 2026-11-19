package com.nutriforma.app.domain.shopping

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.nutrition.PortionCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shopping list output of the release 1.1 foods: complete dishes by name only, ml, pieces and grams. */
class ShoppingNewFoodsTest {

    private val generator = ShoppingListGenerator()
    private val foods = TestFoods.byId
    private val portions = PortionCalculator()
    private val profile = UserProfile()

    private fun component(role: MealRole, foodId: String): PlannedComponent {
        val p = portions.portionFor(role, foods.getValue(foodId), profile)
        return PlannedComponent(role, foodId, p.quantity, p.unit, p.grams)
    }

    private fun dishDinner(day: Int, dish: String) = PlannedMeal(
        0, 1, day, MealSlot.CENA, MealType.CENA_PIATTO_UNICO,
        listOf(component(MealRole.CENA_VERDURE, "zucchine"), component(MealRole.CENA_PIATTO_UNICO, dish)), false,
    )

    @Test
    fun completeDishIsListedByNameWithPortionsAndNoIngredients() {
        val items = generator.generate(listOf(dishDinner(2, "pasticcio")), foods)
        val dish = items.single { it.foodId == "pasticcio" }
        assertEquals("Pasticcio — 1 porzione", dish.title)
        assertEquals(PortionUnit.PORZIONE, dish.unit)
        assertEquals(1, dish.quantity)
        assertEquals(ShoppingGroup.GASTRONOMIA, dish.group)
        // Nessuna scomposizione in ingredienti: solo il piatto e le verdure di contorno.
        assertEquals(setOf("pasticcio", "zucchine"), items.map { it.foodId }.toSet())
    }

    @Test
    fun portionsOfTheSameDishAreSummedInItalian() {
        val items = generator.generate(listOf(dishDinner(2, "carbonara"), dishDinner(5, "carbonara")), foods)
        assertEquals("Carbonara — 2 porzioni", items.single { it.foodId == "carbonara" }.title)
    }

    @Test
    fun everyCompleteDishHasItsOwnTitle() {
        val titles = listOf("pasticcio", "carbonara", "gnocchi_ragu", "tortellini_brodo").map { dish ->
            generator.generate(listOf(dishDinner(2, dish)), foods).single { it.foodId == dish }.title
        }
        assertEquals(
            listOf("Pasticcio — 1 porzione", "Carbonara — 1 porzione", "Gnocchi al ragù — 1 porzione", "Tortellini in brodo — 1 porzione"),
            titles,
        )
    }

    @Test
    fun orangeJuiceIsListedInMillilitres() {
        fun snack(day: Int, slot: MealSlot, type: MealType, role: MealRole) =
            PlannedMeal(0, 1, day, slot, type, listOf(component(role, "spremuta_arancia")), false)
        val meals = listOf(
            snack(0, MealSlot.META_POMERIGGIO, MealType.SPUNTINO_TURNO_LUNGO, MealRole.SPUNTINO_FRUTTA),
            snack(1, MealSlot.META_POMERIGGIO, MealType.SPUNTINO_TURNO_LUNGO, MealRole.SPUNTINO_FRUTTA),
            snack(2, MealSlot.COLAZIONE, MealType.COLAZIONE_STANDARD, MealRole.COLAZIONE_BEVANDA),
        )
        val juice = generator.generate(meals, foods).single { it.foodId == "spremuta_arancia" }
        assertEquals(PortionUnit.MILLILITRI, juice.unit)
        assertEquals(600, juice.quantity)
        assertEquals("600 ml spremuta d'arancia", juice.title)
    }

    @Test
    fun biscuitsPolpoAndPeanutButterUseNaturalUnits() {
        val meals = listOf(
            PlannedMeal(0, 1, 2, MealSlot.COLAZIONE, MealType.COLAZIONE_DOLCE, listOf(component(MealRole.COLAZIONE_DOLCE, "biscotti")), false),
            PlannedMeal(0, 1, 6, MealSlot.COLAZIONE, MealType.COLAZIONE_DOLCE, listOf(component(MealRole.COLAZIONE_DOLCE, "biscotti")), false),
            PlannedMeal(0, 1, 0, MealSlot.PRANZO, MealType.PRANZO_STANDARD, listOf(component(MealRole.PRANZO_SECONDO, "polpo")), false),
            PlannedMeal(0, 1, 1, MealSlot.CENA, MealType.CENA_SECONDO, listOf(component(MealRole.CENA_SECONDO, "polpo")), false),
            PlannedMeal(0, 1, 3, MealSlot.META_MATTINA, MealType.SPUNTINO_MATTINA, listOf(component(MealRole.SPUNTINO_SECCA, "burro_arachidi")), false),
        )
        val byFood = generator.generate(meals, foods).associateBy { it.foodId }
        assertEquals("4 pz biscotti", byFood.getValue("biscotti").title)
        assertEquals(ShoppingGroup.CEREALI_PANE, byFood.getValue("biscotti").group)
        assertEquals("sommati i due pasti", "460 g polpo", byFood.getValue("polpo").title)
        assertEquals(ShoppingGroup.PESCE, byFood.getValue("polpo").group)
        assertEquals("20 g burro di arachidi", byFood.getValue("burro_arachidi").title)
        assertEquals(ShoppingGroup.FRUTTA_SECCA_EXTRA, byFood.getValue("burro_arachidi").group)
    }

    @Test
    fun gastronomyIsTheLastShoppingSectionAndHasAnItalianName() {
        val items = generator.generate(listOf(dishDinner(2, "pasticcio")), foods)
        val sections = generator.toSections(items)
        assertEquals(ShoppingGroup.GASTRONOMIA, sections.last().group)
        assertEquals("Gastronomia e Piatti Pronti", sections.last().group.labelIt)
        assertNotNull(ShoppingGroup.entries.firstOrNull { it.labelIt.contains("Gastronomia") })
        assertTrue(ShoppingGroup.entries.map { it.order }.let { it == it.sorted() })
    }
}
