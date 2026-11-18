package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.model.FoodCategory
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionAdvisorTest {

    @Test
    fun noExclusionsNoWarnings() {
        assertTrue(ExclusionAdvisor.warnings(TestFoods.all, emptySet()).isEmpty())
    }

    @Test
    fun excludingEveryMainCourseWarnsAboutImpossibleMeals() {
        val secondi = TestFoods.all.filter {
            it.category in setOf(FoodCategory.CARNE_BIANCA, FoodCategory.CARNE_ROSSA, FoodCategory.PESCE, FoodCategory.UOVA,
                FoodCategory.TONNO_SGOMBRO, FoodCategory.LEGUMI)
        }.map { it.id }.toSet()
        val warnings = ExclusionAdvisor.warnings(TestFoods.all, secondi)
        assertTrue(warnings.toString(), warnings.any { it.contains("Secondo piatto") && it.contains("non potranno essere composti") })
    }

    @Test
    fun leavingASingleFoodWarnsAboutRepetitiveMeals() {
        val verdure = TestFoods.all.filter { it.category == FoodCategory.VERDURE }.map { it.id }
        val warnings = ExclusionAdvisor.warnings(TestFoods.all, verdure.drop(1).toSet())
        assertTrue(warnings.toString(), warnings.any { it.contains("un solo alimento") })
    }
}
