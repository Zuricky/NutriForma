package com.nutriforma.app.ui.food

import com.nutriforma.app.R
import com.nutriforma.app.data.repository.ShoppingExtra
import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.generate
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.shopping.ShoppingListGenerator
import com.nutriforma.app.domain.swap.MealSwapUseCase
import com.nutriforma.app.ui.dieta.mealComponents
import com.nutriforma.app.ui.dieta.swapOptionUi
import com.nutriforma.app.ui.profilo.buildExclusionGroups
import com.nutriforma.app.ui.spesa.buildSpesaContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FoodIconsTest {

    private val foods = TestFoods.all
    private val byId = TestFoods.byId

    /** Ids of the generated `R.drawable` fields (the real resources compiled into the app). */
    private val drawableIds: Map<String, Int> = R.drawable::class.java.fields
        .filter { it.type == Int::class.javaPrimitiveType }
        .associate { it.name to it.getInt(null) }

    private fun assertValid(icon: FoodIcon, what: String) {
        assertNotEquals("$what: id risorsa 0", 0, icon.resId)
        assertTrue("$what: ${icon.name} non e una risorsa drawable", icon.resId in drawableIds.values)
    }

    @Test
    fun everyFoodCategoryResolvesToAValidIcon() {
        FoodCategory.entries.forEach { category -> assertValid(FoodIcons.forCategory(category), category.name) }
    }

    @Test
    fun everyFoodOfTheSeedFileResolvesToAValidIcon() {
        assertEquals(93, foods.size)
        foods.forEach { food ->
            assertValid(FoodIcons.forFood(food), food.id)
            assertEquals(FoodIcons.forFood(food), FoodIcons.forFoodId(food.id, food.category))
        }
    }

    @Test
    fun everyIconPointsToAnExistingFoodDrawableFile() {
        FoodIcon.entries.forEach { icon ->
            val name = drawableIds.entries.first { it.value == icon.resId }.key
            assertTrue("${icon.name}: $name non e un'icona cibo", name.startsWith("ic_food_"))
            val file = File("src/main/res/drawable/$name.xml")
            assertTrue("${icon.name}: manca ${file.path}", file.isFile)
        }
        // Nessun file icona orfano: ognuno e usato da almeno un FoodIcon.
        val used = FoodIcon.entries.map { icon -> drawableIds.entries.first { it.value == icon.resId }.key }.toSet()
        File("src/main/res/drawable").listFiles { f -> f.name.startsWith("ic_food_") }!!.forEach {
            assertTrue("${it.name} non e collegata a nessun FoodIcon", it.nameWithoutExtension in used)
        }
    }

    @Test
    fun foodIconFilesAreWellFormedVectorsWithTheMediterraneanPalette() {
        val files = File("src/main/res/drawable").listFiles { f -> f.name.startsWith("ic_food_") }!!
        assertTrue(files.size >= 40)
        val colorPattern = Regex("""android:(?:fill|stroke)Color="(#[0-9A-Fa-f]{8})"""")
        val allColors = mutableSetOf<String>()
        files.forEach { file ->
            val xml = file.readText()
            assertTrue("${file.name}: non e un vector", xml.contains("<vector") && xml.contains("</vector>"))
            assertTrue("${file.name}: dimensioni", xml.contains("android:viewportWidth=\"24\"") && xml.contains("android:viewportHeight=\"24\""))
            assertTrue("${file.name}: nessun path", xml.contains("android:pathData="))
            assertFalse("${file.name}: colori da tema", xml.contains("?attr/") || xml.contains("@color/"))
            allColors += colorPattern.findAll(xml).map { it.groupValues[1].uppercase() }
        }
        // Palette del tema: verde erba, rosso pomodoro, viola melanzana.
        assertTrue(allColors.containsAll(listOf("#FF2E7D32", "#FFD84315", "#FF6A1B9A")))
    }

    @Test
    fun specificFoodsOverrideTheirCategoryIcon() {
        assertEquals(FoodIcon.POLPO, FoodIcons.forFood(byId.getValue("polpo")))
        assertEquals(FoodIcon.COOKIE, FoodIcons.forFood(byId.getValue("biscotti")))
        assertEquals(FoodIcon.PEANUT_BUTTER, FoodIcons.forFood(byId.getValue("burro_arachidi")))
        assertEquals(FoodIcon.JUICE, FoodIcons.forFood(byId.getValue("spremuta_arancia")))
        assertEquals(FoodIcon.LASAGNA, FoodIcons.forFood(byId.getValue("pasticcio")))
        assertEquals(FoodIcon.CARBONARA, FoodIcons.forFood(byId.getValue("carbonara")))
        assertEquals(FoodIcon.GNOCCHI, FoodIcons.forFood(byId.getValue("gnocchi_ragu")))
        assertEquals(FoodIcon.TORTELLINI, FoodIcons.forFood(byId.getValue("tortellini_brodo")))
        // Categorie: carne rossa, pesce, pasta, pane, uova, olio, pasto libero.
        assertEquals(FoodIcon.STEAK, FoodIcons.forFood(byId.getValue("manzo_magro")))
        assertEquals(FoodIcon.CHICKEN, FoodIcons.forFood(byId.getValue("petto_pollo")))
        assertEquals(FoodIcon.FISH, FoodIcons.forFood(byId.getValue("orata")))
        assertEquals(FoodIcon.PASTA, FoodIcons.forFood(byId.getValue("pasta_integrale")))
        assertEquals(FoodIcon.BREAD, FoodIcons.forFood(byId.getValue("pane_integrale")))
        assertEquals(FoodIcon.EGG, FoodIcons.forFood(byId.getValue("uovo")))
        assertEquals(FoodIcon.OIL, FoodIcons.forFood(byId.getValue("olio_evo")))
        assertEquals(FoodIcon.FREE_MEAL, FoodIcons.forFood(byId.getValue("pasto_libero")))
        assertEquals(FoodIcon.COFFEE, FoodIcons.forFood(byId.getValue("caffe_espresso")))
    }

    @Test
    fun unknownFoodsFallBackToAValidIcon() {
        assertValid(FoodIcons.forFoodId("non_esiste", null), "sconosciuto")
    }

    // ---- Where the icons are used ---------------------------------------------------------------------------

    @Test
    fun mealCardComponentsCarryOneIconPerFoodInOrder() {
        val meals = generate(profile = UserProfile()).meals
        meals.filter { it.type != MealType.SPUNTINO_SALTATO && it.type != MealType.PRANZO_LIBERO }.forEach { meal ->
            val parts = mealComponents(meal, byId)
            assertEquals(meal.components.size, parts.size)
            meal.components.zip(parts).forEach { (component, ui) ->
                assertEquals(FoodIcons.forFood(byId.getValue(component.foodId)), ui.icon)
                assertTrue(ui.text.isNotBlank())
                assertValid(ui.icon, component.foodId)
            }
        }
        val lunch = mealComponents(meals.first { it.dayIndex == 0 && it.slot == MealSlot.PRANZO }, byId)
        assertTrue(lunch.last().icon == FoodIcon.OIL && lunch.last().text == "Olio EVO (3 cucchiaini)")
        // Pasto libero: solo testo.
        assertTrue(mealComponents(meals.first { it.type == MealType.PRANZO_LIBERO }, byId).isEmpty())
    }

    @Test
    fun completeDishCardShowsTheDishNameWithItsIcon() {
        val week = (1L..60L).map { generate(seed = it, profile = UserProfile()).meals }
        val dinner = week.flatMap { it }.first { it.type == MealType.CENA_PIATTO_UNICO }
        val parts = mealComponents(dinner, byId)
        assertEquals(2, parts.size)
        val dish = parts.last()
        assertTrue(dish.text in setOf("Pasticcio", "Carbonara", "Gnocchi al ragù", "Tortellini in brodo"))
        assertTrue(dish.icon in setOf(FoodIcon.LASAGNA, FoodIcon.CARBONARA, FoodIcon.GNOCCHI, FoodIcon.TORTELLINI))
    }

    @Test
    fun swapOptionsExposeIconsWithoutTheOliveOil() {
        val profile = UserProfile()
        val week = generate(profile = profile).meals
        val dinner = week.first { it.dayIndex == 0 && it.slot == MealSlot.CENA }
        val alternatives = MealSwapUseCase().alternativesFor(dinner, week, foods, profile, maxAlternatives = 500)
        assertTrue(alternatives.isNotEmpty())
        alternatives.forEachIndexed { i, alt ->
            val ui = swapOptionUi(i, alt, byId)
            assertEquals(alt.components.size - 1, ui.icons.size) // senza il condimento
            assertFalse(FoodIcon.OIL in ui.icons)
            ui.icons.forEach { assertValid(it, alt.descriptionIt) }
        }
        // Senza catalogo (come nei vecchi chiamanti) l'opzione resta valida, senza icone.
        assertTrue(swapOptionUi(0, alternatives.first()).icons.isEmpty())
    }

    @Test
    fun dailyCapBadgeIsDistinctFromTheWeeklyOne() {
        val profile = UserProfile()
        val week = generate(seed = 11, profile = profile).meals
        val weekly = MealSwapUseCase().alternativesFor(week.first { it.slot == MealSlot.PRANZO && it.dayIndex == 0 }, week, foods, profile, maxAlternatives = 500)
            .first { !it.allowed }
        assertEquals("Supera il limite settimanale", swapOptionUi(0, weekly, byId).capBadge)
        assertEquals("Supera il limite giornaliero", swapOptionUi(0, weekly.copy(exceedsDailyCap = true), byId).capBadge)
    }

    @Test
    fun exclusionRowsCarryTheFoodIcon() {
        val groups = buildExclusionGroups(foods, emptySet(), "")
        val rows = groups.flatMap { it.rows }
        assertEquals(foods.count { it.isExcludable }, rows.size)
        rows.forEach { row ->
            assertEquals(FoodIcons.forFood(byId.getValue(row.id)), row.icon)
            assertValid(row.icon, row.id)
        }
        assertEquals(FoodIcon.POLPO, rows.first { it.id == "polpo" }.icon)
    }

    @Test
    fun shoppingRowsCarryTheFoodIconAndManualExtrasAUseBasket() {
        val meals = generate(profile = UserProfile()).meals
        val items = ShoppingListGenerator().generate(meals, byId)
        val content = buildSpesaContent(items, listOf(ShoppingExtra("e1", "Carta da forno", false)))
        val rows = (content.toBuy + content.purchased).flatMap { it.rows }
        assertEquals(items.size + 1, rows.size)
        rows.filterNot { it.isExtra }.forEach { row ->
            val id = row.key.removePrefix("food:")
            assertEquals(FoodIcons.forFood(byId.getValue(id)), row.icon)
        }
        assertEquals(FoodIcon.BASKET, rows.single { it.isExtra }.icon)
    }
}
