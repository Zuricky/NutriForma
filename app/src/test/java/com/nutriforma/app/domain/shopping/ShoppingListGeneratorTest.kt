package com.nutriforma.app.domain.shopping

import com.nutriforma.app.data.repository.ShoppingExtra
import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.generate
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.ui.spesa.buildSpesaContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListGeneratorTest {

    private val generator = ShoppingListGenerator()
    private val foods = TestFoods.byId

    private fun grams(role: MealRole, foodId: String, g: Double) =
        PlannedComponent(role, foodId, g, PortionUnit.GRAMMI, g)

    private fun pieces(role: MealRole, foodId: String, n: Int, gramsEach: Double) =
        PlannedComponent(role, foodId, n.toDouble(), PortionUnit.PEZZI, n * gramsEach)

    private fun meal(day: Int, slot: MealSlot, type: MealType, vararg parts: PlannedComponent) =
        PlannedMeal(0, 1, day, slot, type, parts.toList(), false)

    private fun lunch(day: Int, vararg parts: PlannedComponent) = meal(day, MealSlot.PRANZO, MealType.PRANZO_STANDARD, *parts)

    private fun week(petto: String = "petto_pollo") = listOf(
        lunch(0, grams(MealRole.PRANZO_SECONDO, petto, 120.0)),
        lunch(1, grams(MealRole.PRANZO_SECONDO, petto, 120.0)),
        lunch(2, grams(MealRole.PRANZO_SECONDO, petto, 120.0)),
        meal(0, MealSlot.CENA, MealType.CENA_SECONDO, pieces(MealRole.CENA_SECONDO, "uovo", 2, 55.0)),
        meal(3, MealSlot.CENA, MealType.CENA_SECONDO, pieces(MealRole.CENA_SECONDO, "uovo", 4, 55.0)),
    )

    @Test
    fun sumsGramsAndPiecesCleanly() {
        val items = generator.generate(week(), foods).associateBy { it.foodId }
        assertEquals("360 g petto di pollo", items.getValue("petto_pollo").title)
        assertEquals("6 uova", items.getValue("uovo").title)
        assertEquals(PortionUnit.PEZZI, items.getValue("uovo").unit)
    }

    @Test
    fun singleEggIsSingular() {
        val items = generator.generate(listOf(meal(0, MealSlot.CENA, MealType.CENA_SECONDO, pieces(MealRole.CENA_SECONDO, "uovo", 1, 55.0))), foods)
        assertEquals("1 uovo", items.single().title)
    }

    @Test
    fun swapRecalculatesAndKeepsCheckedStateByFood() {
        val before = generator.generate(week(), foods, checkedFoodIds = setOf("uovo", "petto_pollo"))
        assertTrue(before.first { it.foodId == "petto_pollo" }.isChecked)

        // Scambio: il pranzo del giorno 2 passa da pollo a merluzzo.
        val swapped = week().map {
            if (it.dayIndex == 2 && it.slot == MealSlot.PRANZO) {
                it.copy(components = listOf(grams(MealRole.PRANZO_SECONDO, "merluzzo", 200.0)), isUserModified = true)
            } else it
        }
        val after = generator.generate(swapped, foods, checkedFoodIds = setOf("uovo", "petto_pollo")).associateBy { it.foodId }

        assertEquals("240 g petto di pollo", after.getValue("petto_pollo").title)
        assertEquals("200 g merluzzo", after.getValue("merluzzo").title)
        assertTrue("la spunta del pollo resta", after.getValue("petto_pollo").isChecked)
        assertTrue("la spunta delle uova resta", after.getValue("uovo").isChecked)
        assertTrue("il nuovo alimento non è spuntato", !after.getValue("merluzzo").isChecked)
    }

    @Test
    fun foodRemovedBySwapDisappears() {
        val swapped = week().map {
            if (it.slot == MealSlot.PRANZO) it.copy(components = listOf(grams(MealRole.PRANZO_SECONDO, "merluzzo", 150.0))) else it
        }
        assertNull(generator.generate(swapped, foods).firstOrNull { it.foodId == "petto_pollo" })
    }

    @Test
    fun dayRangeFiltersMeals() {
        val items = generator.generate(week(), foods, range = DayRange.Days(setOf(0))).associateBy { it.foodId }
        assertEquals("120 g petto di pollo", items.getValue("petto_pollo").title)
        assertEquals("2 uova", items.getValue("uovo").title)
        assertTrue(generator.generate(week(), foods, range = DayRange.Days(emptySet())).isEmpty())
        assertEquals(
            generator.generate(week(), foods),
            generator.generate(week(), foods, range = DayRange.Days((0..6).toSet())),
        )
    }

    @Test
    fun pastoLiberoAndUnknownFoodsAreSkipped() {
        val items = generator.generate(
            listOf(
                meal(5, MealSlot.PRANZO, MealType.PRANZO_LIBERO, grams(MealRole.PRANZO_LIBERO, "pasto_libero", 1.0)),
                lunch(0, grams(MealRole.PRANZO_SECONDO, "alimento_sconosciuto", 100.0)),
            ),
            foods,
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun sectionsFollowSupermarketGroupOrder() {
        val week = generate().meals
        val sections = generator.toSections(generator.generate(week, foods))
        assertTrue(sections.isNotEmpty())
        assertEquals(sections.map { it.group.order }, sections.map { it.group.order }.sorted())
        assertEquals(sections.size, sections.map { it.group }.toSet().size)
        assertTrue(sections.all { s -> s.items.all { it.group == s.group } })
        assertTrue(sections.flatMap { it.items }.all { it.quantity > 0 })
    }

    @Test
    fun cheeseGoesWithDairyNotColdCuts() {
        assertEquals(ShoppingGroup.LATTICINI_UOVA, ShoppingGroup.of(foods.getValue("grana_padano")))
        assertEquals(ShoppingGroup.CARNE_AFFETTATI, ShoppingGroup.of(foods.getValue("bresaola")))
        assertEquals(ShoppingGroup.DISPENSA, ShoppingGroup.of(foods.getValue("tonno_sottolio")))
        assertNull(ShoppingGroup.of(foods.getValue("pasto_libero")))
    }

    @Test
    fun contentSplitsToBuyAndPurchasedAndAddsExtras() {
        val items = generator.generate(week(), foods, checkedFoodIds = setOf("uovo"))
        val content = buildSpesaContent(items, listOf(ShoppingExtra("a", "Detersivo"), ShoppingExtra("b", "Sale", isChecked = true)))
        assertEquals(listOf("360 g petto di pollo", "Detersivo"), content.toBuy.flatMap { it.rows }.map { it.title })
        assertEquals(listOf("6 uova", "Sale"), content.purchased.flatMap { it.rows }.map { it.title })
        assertEquals(2, content.toBuyCount)
        assertEquals(2, content.purchasedCount)
        assertEquals("Frutta Secca e Extra", content.toBuy.last().label)
    }
}
