package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.SupermarketCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionTableTest {

    private fun food(
        id: String,
        category: FoodCategory,
        gramsPerUnit: Double? = null,
    ) = FoodItem(
        id = id, nameIt = id, category = category, roles = emptySet(), gramsPerUnit = gramsPerUnit,
        kcalPer100g = 100.0, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 0.0, fiberPer100g = 0.0,
        supermarketCategory = SupermarketCategory.DISPENSA, isPreferred = false, isExcludable = true,
    )

    @Test
    fun rowsAreSane() {
        assertTrue("tabella porzioni troppo corta", PortionTable.rows.size >= 60)
        PortionTable.rows.forEach { row ->
            assertTrue("$row: min > max", row.min <= row.max)
            assertTrue("$row: min <= 0", row.min > 0.0)
            if (row.unit == PortionUnit.PORZIONE) assertEquals(MealRole.PRANZO_LIBERO, row.role)
        }
        assertEquals("nessuna riga duplicata", PortionTable.rows.size,
            PortionTable.rows.map { Triple(it.role, it.category, it.foodId) }.toSet().size)
    }

    @Test
    fun foodRowWinsOverCategoryRow() {
        val torta = food("torta_casalinga", FoodCategory.DOLCE_COLAZIONE, 60.0)
        val brioche = food("brioche", FoodCategory.DOLCE_COLAZIONE, 50.0)
        assertEquals("di preparazione casalinga", PortionTable.find(MealRole.COLAZIONE_DOLCE, torta)?.hintIt)
        assertNull(PortionTable.find(MealRole.COLAZIONE_DOLCE, brioche)?.hintIt)
        assertNotNull(PortionTable.find(MealRole.COLAZIONE_DOLCE, brioche))
    }

    @Test
    fun foodsWithoutARowAreNotValidForTheRole() {
        val patate = food("patate", FoodCategory.PATATE)
        val olio = food("olio_evo", FoodCategory.OLIO, null)
        assertNull("niente patate nell'antipasto", PortionTable.find(MealRole.PRANZO_ANTIPASTO, patate))
        assertNull("niente patate a cena", PortionTable.find(MealRole.CENA_VERDURE, patate))
        assertNull(PortionTable.find(MealRole.CENA_PRIMO_BASE, olio))
        assertNotNull(PortionTable.find(MealRole.PRANZO_ACCOMPAGNAMENTO, patate))
    }

    @Test
    fun gramsPerUnitResolution() {
        val olio = food("olio_evo", FoodCategory.OLIO)
        val oliRow = PortionTable.find(MealRole.PRANZO_CONDIMENTO, olio)!!
        assertEquals(5.0, PortionTable.gramsPerUnit(oliRow, olio)!!, 0.0)

        val uovo = food("uovo", FoodCategory.UOVA, 55.0)
        assertEquals(55.0, PortionTable.gramsPerUnit(PortionTable.find(MealRole.PRANZO_SECONDO, uovo)!!, uovo)!!, 0.0)

        val uovoSenzaPeso = food("uovo", FoodCategory.UOVA, null)
        assertNull(PortionTable.gramsPerUnit(PortionTable.find(MealRole.PRANZO_SECONDO, uovoSenzaPeso)!!, uovoSenzaPeso))

        val pasto = food("pasto_libero", FoodCategory.PASTO_LIBERO)
        assertEquals(0.0, PortionTable.gramsPerUnit(PortionTable.find(MealRole.PRANZO_LIBERO, pasto)!!, pasto)!!, 0.0)

        val pollo = food("petto_pollo", FoodCategory.CARNE_BIANCA)
        assertEquals(1.0, PortionTable.gramsPerUnit(PortionTable.find(MealRole.PRANZO_SECONDO, pollo)!!, pollo)!!, 0.0)
    }

    @Test
    fun scalableFlagsFollowTheSpec() {
        fun row(role: MealRole, category: FoodCategory) =
            PortionTable.find(role, food("x", category, 10.0))!!
        assertTrue(row(MealRole.PRANZO_SECONDO, FoodCategory.CARNE_BIANCA).scalable)
        assertTrue(row(MealRole.CENA_PRIMO_BASE, FoodCategory.PASTA).scalable)
        assertTrue(row(MealRole.COLAZIONE_G2, FoodCategory.PANE).scalable)
        assertTrue(!row(MealRole.PRANZO_SECONDO, FoodCategory.UOVA).scalable)
        assertTrue(!row(MealRole.COLAZIONE_G2, FoodCategory.FETTE_BISCOTTATE).scalable)
        assertTrue(!row(MealRole.PRANZO_CONDIMENTO, FoodCategory.OLIO).scalable)
        // Scalable rows are always gram based.
        PortionTable.rows.filter { it.scalable }.forEach { assertEquals("$it", PortionUnit.GRAMMI, it.unit) }
    }

    // ---- MealStructure ----------------------------------------------------------------------------------

    @Test
    fun everyMealRoleBelongsToSomeMealTypeAndHasPortionRows() {
        assertEquals(MealRole.entries.toSet(), MealStructure.allRoles)
        MealRole.entries.forEach { role ->
            assertTrue("Nessuna riga porzione per $role", PortionTable.rows.any { it.role == role })
        }
    }

    @Test
    fun mealStructureMatchesTheSpecTable() {
        assertEquals(MealType.entries.toSet(), MealStructure.byType.keys)
        assertTrue(MealStructure.specFor(MealType.SPUNTINO_SALTATO).isEmpty())
        assertEquals(
            listOf(MealRole.COLAZIONE_G1, MealRole.COLAZIONE_G2, MealRole.COLAZIONE_DOLCIFICANTE, MealRole.COLAZIONE_BEVANDA),
            MealStructure.specFor(MealType.COLAZIONE_STANDARD).map { it.role },
        )
        assertEquals(
            listOf(false, false),
            MealStructure.specFor(MealType.COLAZIONE_STANDARD).drop(2).map { it.required },
        )
        assertEquals(
            listOf(
                MealRole.CENA_VERDURE, MealRole.CENA_PRIMO_BASE, MealRole.CENA_PRIMO_CONDIMENTO_VERDURE,
                MealRole.CENA_PRIMO_GRANA, MealRole.CENA_CONDIMENTO,
            ),
            MealStructure.specFor(MealType.CENA_PRIMO_VERDURE).map { it.role },
        )
        // "No grana" for the protein primo.
        assertTrue(MealRole.CENA_PRIMO_GRANA !in MealStructure.specFor(MealType.CENA_PRIMO_PROTEICO).map { it.role })
        // Every non-optional component of every type is required; optional only at breakfast.
        MealStructure.byType.forEach { (type, specs) ->
            if (type != MealType.COLAZIONE_STANDARD) assertTrue("$type", specs.all { it.required })
        }
        // Each dinner type starts with the vegetables and ends with the olive oil.
        listOf(
            MealType.CENA_PRIMO_VERDURE, MealType.CENA_PRIMO_PROTEICO, MealType.CENA_SECONDO, MealType.CENA_MINESTRONE,
        ).forEach {
            val roles = MealStructure.specFor(it).map { s -> s.role }
            assertEquals(MealRole.CENA_VERDURE, roles.first())
            assertEquals(MealRole.CENA_CONDIMENTO, roles.last())
        }
    }
}
