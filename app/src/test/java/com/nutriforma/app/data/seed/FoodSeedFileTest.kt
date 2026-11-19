package com.nutriforma.app.data.seed

import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodCategory.*
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.SupermarketCategory
import com.nutriforma.app.domain.rules.PortionTable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Integrity of `src/main/assets/foods_it.json` against the diet rules.
 * JVM test: the working directory of unit tests is the `app/` module.
 */
private val testJson = Json { ignoreUnknownKeys = true }

class FoodSeedFileTest {

    private val seedFile = File("src/main/assets/foods_it.json")
    private val seed: FoodSeedFile by lazy {
        testJson.decodeFromString(FoodSeedFile.serializer(), seedFile.readText())
    }
    private val foods: List<FoodItem> by lazy { seed.foods.map { it.toDomain() } }
    private val byId: Map<String, FoodItem> by lazy { foods.associateBy { it.id } }

    private fun foodsFor(role: MealRole) = foods.filter { role in it.roles }

    // ---- File structure ---------------------------------------------------------------------------------

    @Test
    fun fileExistsAndHas93FoodsAtSeedVersion2() {
        assertTrue("File mancante: ${seedFile.absolutePath}", seedFile.isFile)
        assertEquals(2, seed.seedVersion)
        assertEquals(93, seed.foods.size)
    }

    @Test
    fun idsAreUniqueAndWellFormed() {
        val ids = seed.foods.map { it.id }
        assertEquals("id duplicati", ids.size, ids.toSet().size)
        val pattern = Regex("^[a-z0-9_]+$")
        ids.forEach { assertTrue("id non valido: $it", pattern.matches(it)) }
    }

    @Test
    fun everyEnumStringParses() {
        // toDomain() throws IllegalArgumentException with the food id on any unknown category/role/supermarket.
        assertEquals(93, foods.size)
        foods.forEach { assertTrue("${it.id}: nessun ruolo", it.roles.isNotEmpty()) }
    }

    @Test
    fun namesAreNonBlankAndTrimmed() {
        foods.forEach {
            assertTrue("${it.id}: nome vuoto", it.nameIt.isNotBlank())
            assertEquals("${it.id}: spazi superflui nel nome", it.nameIt.trim(), it.nameIt)
        }
    }

    @Test
    fun everyFoodCategoryHasAtLeastOneFood() {
        FoodCategory.entries.forEach { category ->
            assertTrue("Nessun alimento per la categoria $category", foods.any { it.category == category })
        }
    }

    // ---- Portion table consistency ---------------------------------------------------------------------

    @Test
    fun everyRoleOfEveryFoodHasAPortionRow() {
        foods.forEach { food ->
            food.roles.forEach { role ->
                assertNotNull("${food.id}: nessuna riga porzione per $role", PortionTable.find(role, food))
            }
        }
    }

    @Test
    fun unitBasedRowsResolveGramsPerUnit() {
        foods.forEach { food ->
            food.roles.forEach { role ->
                val row = PortionTable.find(role, food) ?: return@forEach
                if (row.unit != PortionUnit.GRAMMI && row.unit != PortionUnit.PORZIONE) {
                    val gramsPerUnit = PortionTable.gramsPerUnit(row, food)
                    assertNotNull("${food.id}/$role: grammi per unità mancanti", gramsPerUnit)
                    assertTrue("${food.id}/$role: grammi per unità <= 0", gramsPerUnit!! > 0.0)
                }
            }
        }
    }

    @Test
    fun everyPortionRowIsBackedByAFoodWithThatRole() {
        PortionTable.rows.forEach { row ->
            val candidates = foodsFor(row.role)
            val matching = if (row.foodId != null) {
                candidates.filter { it.id == row.foodId }
            } else {
                candidates.filter { it.category == row.category }
            }
            assertTrue("Riga porzione senza alimenti: ${row.role} / ${row.foodId ?: row.category}", matching.isNotEmpty())
        }
    }

    // ---- Slot / rule coverage (SPEC §2, §4, §7.4) --------------------------------------------------------

    @Test
    fun everyMealRoleHasEnoughFoods() {
        val single = setOf(
            MealRole.PRANZO_LIBERO, MealRole.PRANZO_CONDIMENTO, MealRole.CENA_CONDIMENTO,
            MealRole.CENA_MINESTRONE, MealRole.SPUNTINO_BARRETTA,
        )
        MealRole.entries.forEach { role ->
            val count = foodsFor(role).size
            val required = if (role in single) 1 else 2
            assertTrue("Ruolo $role: $count alimenti (minimo $required)", count >= required)
        }
        assertEquals(listOf("pasto_libero"), foodsFor(MealRole.PRANZO_LIBERO).map { it.id })
    }

    @Test
    fun theWeeklyRulesAreReachableWithTheCatalogue() {
        fun count(category: FoodCategory) = foods.count { it.category == category }
        // Pesce >= 2 per settimana, carne bianca/rossa, uova, tonno/sgombro, affettato, patate, legumi.
        assertTrue(count(PESCE) >= 4)
        assertTrue(count(CARNE_BIANCA) >= 2)
        assertTrue(count(CARNE_ROSSA) >= 2)
        assertTrue(count(TONNO_SGOMBRO) >= 2)
        assertTrue(count(AFFETTATO) >= 2)
        assertEquals(1, count(UOVA))
        assertEquals(1, count(PATATE))
        assertTrue(count(LEGUMI) >= 2)
        // 2 colazioni dolci/settimana: torta, brioche e biscotti.
        assertEquals(setOf("torta_casalinga", "brioche", "biscotti"), foods.filter { it.category == DOLCE_COLAZIONE }.map { it.id }.toSet())
        // Primo con condimento proteico: uovo, tonno/sgombro, pesce, ragù, pesto.
        listOf(UOVA, TONNO_SGOMBRO, PESCE, RAGU, PESTO).forEach { category ->
            assertTrue(
                "Nessun $category per il condimento proteico",
                foodsFor(MealRole.CENA_PRIMO_CONDIMENTO_PROTEICO).any { it.category == category },
            )
        }
        // Minestrone: ½ ricotta | uovo | affettato magro | legumi.
        assertEquals(
            setOf(LATTICINI, UOVA, AFFETTATO, LEGUMI),
            foodsFor(MealRole.CENA_MINESTRONE_AGGIUNTA).map { it.category }.toSet(),
        )
        assertEquals("ricotta", foodsFor(MealRole.CENA_MINESTRONE_AGGIUNTA).single { it.category == LATTICINI }.id)
    }

    @Test
    fun spuntinoAndColazioneGroupsFollowTheSpec() {
        assertEquals(setOf(FRUTTA_SECCA, MIX_FRUTTA), foodsFor(MealRole.SPUNTINO_SECCA).map { it.category }.toSet())
        // Abbinamento: cioccolato, formaggio, avocado e (solo a scelta, max 1 al giorno) burro di arachidi.
        assertEquals(
            setOf(CIOCCOLATO, FORMAGGIO_STAGIONATO, AVOCADO, FRUTTA_SECCA),
            foodsFor(MealRole.SPUNTINO_ABBINAMENTO).map { it.category }.toSet(),
        )
        assertEquals(
            listOf("burro_arachidi"),
            foodsFor(MealRole.SPUNTINO_ABBINAMENTO).filter { it.category == FRUTTA_SECCA }.map { it.id },
        )
        assertEquals(
            setOf(AFFETTATO, UOVA, PESCE_AFFUMICATO, LATTICINI),
            foodsFor(MealRole.COLAZIONE_G1).map { it.category }.toSet(),
        )
        assertEquals(
            setOf(PANE, FETTE_BISCOTTATE, CEREALI_COLAZIONE, FRUTTA_FRESCA),
            foodsFor(MealRole.COLAZIONE_G2).map { it.category }.toSet(),
        )
        // Pane a colazione: solo integrale o di segale.
        assertEquals(
            setOf("pane_integrale", "pane_segale"),
            foodsFor(MealRole.COLAZIONE_G2).filter { it.category == PANE }.map { it.id }.toSet(),
        )
        // Caffè o ginseng; miele o marmellata.
        assertEquals(
            setOf("caffe_espresso", "caffe_ginseng", "spremuta_arancia"),
            foodsFor(MealRole.COLAZIONE_BEVANDA).map { it.id }.toSet(),
        )
        assertEquals(setOf("miele", "marmellata"), foodsFor(MealRole.COLAZIONE_DOLCIFICANTE).map { it.id }.toSet())
        // Cioccolato fondente almeno all'80%.
        assertTrue(byId.getValue("cioccolato_fondente_80").nameIt.contains("80%"))
    }

    @Test
    fun pranzoAndCenaGroupsFollowTheSpec() {
        // Antipasto e contorni: solo verdure (mai patate/legumi); fagiolini sono verdure, non legumi.
        assertEquals(setOf(VERDURE), foodsFor(MealRole.PRANZO_ANTIPASTO).map { it.category }.toSet())
        assertEquals(setOf(VERDURE), foodsFor(MealRole.CENA_VERDURE).map { it.category }.toSet())
        assertEquals(VERDURE, byId.getValue("fagiolini").category)
        assertEquals(
            setOf("ceci_lessati", "fagioli_lessati", "piselli_lessati"),
            foods.filter { it.category == LEGUMI }.map { it.id }.toSet(),
        )
        // Secondo pranzo: carne bianca/rossa, pesce, uova, tonno/sgombro.
        assertEquals(
            setOf(CARNE_BIANCA, CARNE_ROSSA, PESCE, UOVA, TONNO_SGOMBRO),
            foodsFor(MealRole.PRANZO_SECONDO).map { it.category }.toSet(),
        )
        // Cena secondo: niente carne (solo a pranzo), ma anche i legumi.
        assertEquals(
            setOf(PESCE, UOVA, LEGUMI, TONNO_SGOMBRO),
            foodsFor(MealRole.CENA_SECONDO).map { it.category }.toSet(),
        )
        // Primo: pasta oppure riso (per i cereali in chicco solo riso).
        assertEquals(
            setOf("riso_integrale", "riso_bianco"),
            foodsFor(MealRole.CENA_PRIMO_BASE).filter { it.category == CEREALE_CHICCO }.map { it.id }.toSet(),
        )
        assertEquals(setOf(PASTA, CEREALE_CHICCO), foodsFor(MealRole.CENA_PRIMO_BASE).map { it.category }.toSet())
        // Grana o parmigiano, olio EVO.
        assertEquals(setOf("grana_padano", "parmigiano_reggiano"), foodsFor(MealRole.CENA_PRIMO_GRANA).map { it.id }.toSet())
        assertEquals(listOf("olio_evo"), foodsFor(MealRole.PRANZO_CONDIMENTO).map { it.id })
        assertEquals(listOf("olio_evo"), foodsFor(MealRole.CENA_CONDIMENTO).map { it.id })
        assertEquals(listOf("minestrone_verdure"), foodsFor(MealRole.CENA_MINESTRONE).map { it.id })
        assertEquals(listOf("barretta_proteica"), foodsFor(MealRole.SPUNTINO_BARRETTA).map { it.id })
    }

    // ---- Release 1.1: carne solo a pranzo, pasta solo a cena, nuovi alimenti -----------------------------

    private val newFoodIds = listOf(
        "biscotti", "burro_arachidi", "spremuta_arancia", "polpo", "pasticcio", "carbonara", "gnocchi_ragu", "tortellini_brodo",
    )

    @Test
    fun newFoodsExistWithItalianNames() {
        assertEquals("Biscotti", byId.getValue("biscotti").nameIt)
        assertEquals("Burro di arachidi", byId.getValue("burro_arachidi").nameIt)
        assertEquals("Spremuta d'arancia", byId.getValue("spremuta_arancia").nameIt)
        assertEquals("Polpo", byId.getValue("polpo").nameIt)
        assertEquals("Pasticcio", byId.getValue("pasticcio").nameIt)
        assertEquals("Carbonara", byId.getValue("carbonara").nameIt)
        assertEquals("Gnocchi al ragù", byId.getValue("gnocchi_ragu").nameIt)
        assertEquals("Tortellini in brodo", byId.getValue("tortellini_brodo").nameIt)
        newFoodIds.forEach { assertTrue("$it: escludibile", byId.getValue(it).isExcludable) }
    }

    @Test
    fun existingFoodIdsAreNeverRemoved() {
        val v1 = listOf(
            "prosciutto_crudo", "uovo", "pane_integrale", "mela", "caffe_espresso", "torta_casalinga", "mandorle", "zucchine",
            "petto_pollo", "manzo_magro", "merluzzo", "tonno_sottolio", "ceci_lessati", "patate", "riso_integrale",
            "pasta_integrale", "minestrone_verdure", "ragu_carne", "pesto_genovese", "olio_evo", "pasto_libero",
        )
        v1.forEach { assertTrue("alimento rimosso: $it", it in byId) }
    }

    @Test
    fun meatIsLunchOnly() {
        foods.filter { it.category == CARNE_BIANCA || it.category == CARNE_ROSSA }.forEach { meat ->
            assertTrue("${meat.id}: manca il pranzo", MealRole.PRANZO_SECONDO in meat.roles)
            assertFalse("${meat.id}: carne a cena", MealRole.CENA_SECONDO in meat.roles)
        }
        assertTrue(
            PortionTable.rows.none { it.role == MealRole.CENA_SECONDO && (it.category == CARNE_BIANCA || it.category == CARNE_ROSSA) },
        )
        // Gli affettati restano per colazione e minestrone.
        assertTrue(foodsFor(MealRole.COLAZIONE_G1).any { it.category == AFFETTATO })
        assertTrue(foodsFor(MealRole.CENA_MINESTRONE_AGGIUNTA).any { it.category == AFFETTATO })
    }

    @Test
    fun pastaAndCompleteDishesAreDinnerOnly() {
        val dinnerRoles = setOf(
            MealRole.CENA_VERDURE, MealRole.CENA_PRIMO_BASE, MealRole.CENA_PRIMO_CONDIMENTO_VERDURE, MealRole.CENA_PRIMO_GRANA,
            MealRole.CENA_PRIMO_CONDIMENTO_PROTEICO, MealRole.CENA_SECONDO, MealRole.CENA_SECONDO_ACCOMPAGNAMENTO,
            MealRole.CENA_MINESTRONE, MealRole.CENA_MINESTRONE_AGGIUNTA, MealRole.CENA_CONDIMENTO, MealRole.CENA_PIATTO_UNICO,
        )
        foods.filter { it.category == PASTA || it.category == PIATTO_UNICO }.forEach { f ->
            assertTrue("${f.id}: ruoli non di cena ${f.roles}", f.roles.all { it in dinnerRoles })
        }
        assertEquals(
            setOf("pasticcio", "carbonara", "gnocchi_ragu", "tortellini_brodo"),
            foodsFor(MealRole.CENA_PIATTO_UNICO).map { it.id }.toSet(),
        )
        assertTrue(foodsFor(MealRole.CENA_PIATTO_UNICO).all { it.category == PIATTO_UNICO })
    }

    @Test
    fun polpoIsFishAtLunchAndDinner() {
        val polpo = byId.getValue("polpo")
        assertEquals(PESCE, polpo.category)
        assertEquals(
            setOf(MealRole.PRANZO_SECONDO, MealRole.CENA_SECONDO, MealRole.CENA_PRIMO_CONDIMENTO_PROTEICO),
            polpo.roles,
        )
    }

    @Test
    fun biscottiBurroAndSpremutaRolesAndPortions() {
        val biscotti = byId.getValue("biscotti")
        assertEquals(DOLCE_COLAZIONE, biscotti.category)
        assertEquals(setOf(MealRole.COLAZIONE_DOLCE), biscotti.roles)
        val burro = byId.getValue("burro_arachidi")
        assertEquals(setOf(MealRole.COLAZIONE_DOLCE, MealRole.SPUNTINO_SECCA, MealRole.SPUNTINO_ABBINAMENTO), burro.roles)
        val spremuta = byId.getValue("spremuta_arancia")
        assertEquals(
            setOf(MealRole.COLAZIONE_BEVANDA, MealRole.COLAZIONE_DOLCE_BEVANDA, MealRole.SPUNTINO_FRUTTA),
            spremuta.roles,
        )
        spremuta.roles.forEach { role ->
            val row = PortionTable.find(role, spremuta)!!
            assertEquals(PortionUnit.MILLILITRI, row.unit)
            assertEquals(200.0, row.min, 0.0)
            assertEquals(250.0, row.max, 0.0)
        }
        burro.roles.forEach { role ->
            val row = PortionTable.find(role, burro)!!
            assertEquals(PortionUnit.GRAMMI, row.unit)
            assertEquals(15.0, row.min, 0.0)
            assertEquals(20.0, row.max, 0.0)
        }
    }

    @Test
    fun countingDecisionsOfTheSpecAreRespected() {
        assertEquals(CARNE_BIANCA, byId.getValue("petto_pollo").category)
        assertEquals(CARNE_BIANCA, byId.getValue("petto_tacchino").category)
        assertEquals(CARNE_BIANCA, byId.getValue("coniglio").category)
        listOf("manzo_magro", "vitello_magro", "lonza_maiale").forEach { assertEquals(CARNE_ROSSA, byId.getValue(it).category) }
        assertEquals(PESCE_AFFUMICATO, byId.getValue("salmone_affumicato").category)
        assertEquals(TONNO_SGOMBRO, byId.getValue("tonno_sottolio").category)
        assertEquals(TONNO_SGOMBRO, byId.getValue("sgombro_sottolio").category)
        assertEquals(RAGU, byId.getValue("ragu_carne").category)
        assertEquals(AFFETTATO, byId.getValue("prosciutto_crudo").category)
        assertEquals(AFFETTATO, byId.getValue("prosciutto_cotto").category)
    }

    @Test
    fun fixedGramsPerUnitMatchTheSpec() {
        val expected = mapOf(
            "uovo" to 55.0, "fette_biscottate_integrali" to 9.0, "fette_biscottate" to 9.0, "caffe_espresso" to 30.0,
            "caffe_ginseng" to 30.0, "cappuccino" to 150.0, "torta_casalinga" to 60.0, "brioche" to 50.0,
            "barretta_proteica" to 45.0, "miele" to 7.0, "marmellata" to 10.0,
            "biscotti" to 13.0, "spremuta_arancia" to 1.0, // 2 biscotti = 26 g; spremuta: 1 ml = 1 g
        )
        expected.forEach { (id, grams) -> assertEquals("gramsPerUnit di $id", grams, byId.getValue(id).gramsPerUnit) }
        // Tutti gli altri alimenti sono "a grammi".
        foods.filter { it.id !in expected }.forEach { assertNull("${it.id}: gramsPerUnit inatteso", it.gramsPerUnit) }
    }

    // ---- Nutrition values -------------------------------------------------------------------------------

    @Test
    fun macrosAreValidAndCoherentWithKcal() {
        foods.filter { it.id != "pasto_libero" }.forEach { f ->
            assertTrue("${f.id}: kcal <= 0", f.kcalPer100g > 0.0)
            listOf(f.proteinPer100g, f.carbsPer100g, f.fatPer100g, f.fiberPer100g).forEach {
                assertTrue("${f.id}: valore nutrizionale negativo", it >= 0.0)
            }
            assertTrue("${f.id}: macro > 100 g/100 g", f.proteinPer100g + f.carbsPer100g + f.fatPer100g <= 100.5)
            assertTrue("${f.id}: kcal > 900", f.kcalPer100g <= 900.0)
            val atwater = 4 * f.proteinPer100g + 4 * f.carbsPer100g + 9 * f.fatPer100g
            val tolerance = maxOf(15.0, 0.20 * f.kcalPer100g)
            assertTrue(
                "${f.id}: kcal ${f.kcalPer100g} incoerenti con i macro ($atwater)",
                abs(atwater - f.kcalPer100g) <= tolerance,
            )
        }
        val libero = byId.getValue("pasto_libero")
        assertEquals(0.0, libero.kcalPer100g, 0.0)
        assertEquals(0.0, libero.proteinPer100g + libero.carbsPer100g + libero.fatPer100g + libero.fiberPer100g, 0.0)
    }

    // ---- Excludability, supermarket categories (Italian) -----------------------------------------------

    @Test
    fun onlyPastoLiberoIsNotExcludable() {
        assertEquals(listOf("pasto_libero"), foods.filter { !it.isExcludable }.map { it.id })
    }

    @Test
    fun supermarketCategoriesAreItalianAndConsistentWithFoodCategory() {
        SupermarketCategory.entries.forEach { assertTrue(it.labelIt.isNotBlank()) }
        assertEquals(
            SupermarketCategory.entries.size,
            SupermarketCategory.entries.map { it.labelIt }.toSet().size,
        )
        assertEquals(
            listOf(
                "Frutta e verdura", "Macelleria", "Pescheria", "Salumi e formaggi", "Latte, yogurt e uova", "Pane e forno",
                "Pasta, riso e cereali", "Conserve e sughi", "Frutta secca e snack", "Surgelati", "Dispensa", "Caffè e bevande",
                "Gastronomia e piatti pronti",
            ),
            SupermarketCategory.entries.sortedBy { it.order }.map { it.labelIt },
        )
        FoodCategory.entries.forEach { assertTrue(it.labelIt.isNotBlank()) }

        val expected: Map<FoodCategory, SupermarketCategory?> = mapOf(
            AFFETTATO to SupermarketCategory.SALUMI_FORMAGGI,
            FORMAGGIO_STAGIONATO to SupermarketCategory.SALUMI_FORMAGGI,
            CARNE_BIANCA to SupermarketCategory.CARNE,
            CARNE_ROSSA to SupermarketCategory.CARNE,
            PESCE to SupermarketCategory.PESCE,
            PESCE_AFFUMICATO to SupermarketCategory.PESCE,
            TONNO_SGOMBRO to SupermarketCategory.CONSERVE_SUGHI,
            LEGUMI to SupermarketCategory.CONSERVE_SUGHI,
            SALSA_POMODORO to SupermarketCategory.CONSERVE_SUGHI,
            RAGU to SupermarketCategory.CONSERVE_SUGHI,
            PESTO to SupermarketCategory.CONSERVE_SUGHI,
            UOVA to SupermarketCategory.LATTICINI_UOVA,
            LATTICINI to SupermarketCategory.LATTICINI_UOVA,
            PANE to SupermarketCategory.PANE_FORNO,
            FETTE_BISCOTTATE to SupermarketCategory.PANE_FORNO,
            DOLCE_COLAZIONE to SupermarketCategory.PANE_FORNO,
            CEREALI_COLAZIONE to SupermarketCategory.PASTA_RISO_CEREALI,
            CEREALE_CHICCO to SupermarketCategory.PASTA_RISO_CEREALI,
            PASTA to SupermarketCategory.PASTA_RISO_CEREALI,
            POLENTA to SupermarketCategory.PASTA_RISO_CEREALI,
            PATATE to SupermarketCategory.FRUTTA_VERDURA,
            FRUTTA_FRESCA to SupermarketCategory.FRUTTA_VERDURA,
            VERDURE to SupermarketCategory.FRUTTA_VERDURA,
            AVOCADO to SupermarketCategory.FRUTTA_VERDURA,
            FRUTTA_SECCA to SupermarketCategory.FRUTTA_SECCA_SNACK,
            MIX_FRUTTA to SupermarketCategory.FRUTTA_SECCA_SNACK,
            CIOCCOLATO to SupermarketCategory.FRUTTA_SECCA_SNACK,
            BARRETTA_PROTEICA to SupermarketCategory.FRUTTA_SECCA_SNACK,
            MINESTRONE to SupermarketCategory.SURGELATI,
            OLIO to SupermarketCategory.DISPENSA,
            DOLCIFICANTE to SupermarketCategory.DISPENSA,
            BEVANDA to SupermarketCategory.BEVANDE,
            PASTO_LIBERO to null,
            PIATTO_UNICO to SupermarketCategory.GASTRONOMIA,
        )
        assertEquals("mappa di test incompleta", FoodCategory.entries.toSet(), expected.keys)
        foods.forEach { assertEquals("${it.id}: reparto", expected.getValue(it.category), it.supermarketCategory) }
        assertFalse(foods.any { it.supermarketCategory == null && it.id != "pasto_libero" })
    }

    @Test
    fun preferredFoodsAreTheWholegrainAndWhiteMeats() {
        val preferred = foods.filter { it.isPreferred }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "pane_integrale", "fette_biscottate_integrali", "petto_pollo", "petto_tacchino", "coniglio",
                "riso_integrale", "pasta_integrale",
            ),
            preferred,
        )
    }
}
