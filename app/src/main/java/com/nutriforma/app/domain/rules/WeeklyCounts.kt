package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.PortionUnit

/** SPEC §4.1 counters, computed from the planned meals of one week. */
data class WeeklyCounts(
    val colazioniDolci: Int = 0,
    val pastiLiberi: Int = 0,
    val cenePrimo: Int = 0,
    val cenePrimoVerdure: Int = 0,
    val cenePrimoProteico: Int = 0,
    val ceneSecondo: Int = 0,
    val ceneMinestrone: Int = 0,
    val carne: Int = 0,
    val carneBianca: Int = 0,
    val carneRossa: Int = 0,
    val pesce: Int = 0,
    val uovaOccasioni: Int = 0,
    val uovaTotale: Int = 0,
    val tonno: Int = 0,
    val affettato: Int = 0,
    val patate: Int = 0,
)

object WeeklyCounter {

    /**
     * An "occasion" is one meal containing at least one component of the counted category
     * (two components of the same category in one meal count once). Unknown food ids are ignored.
     */
    fun count(meals: List<PlannedMeal>, foodsById: Map<String, FoodItem>): WeeklyCounts {
        var dolci = 0; var liberi = 0; var verdure = 0; var proteico = 0; var secondo = 0; var minestrone = 0
        var carne = 0; var bianca = 0; var rossa = 0; var pesce = 0; var uovaOcc = 0; var uovaTot = 0.0
        var tonno = 0; var affettato = 0; var patate = 0

        for (meal in meals) {
            when (meal.type) {
                MealType.COLAZIONE_DOLCE -> dolci++
                MealType.PRANZO_LIBERO -> liberi++
                MealType.CENA_PRIMO_VERDURE -> verdure++
                MealType.CENA_PRIMO_PROTEICO -> proteico++
                MealType.CENA_SECONDO -> secondo++
                MealType.CENA_MINESTRONE -> minestrone++
                else -> Unit
            }
            val categories = HashSet<FoodCategory>()
            for (component in meal.components) {
                val food = foodsById[component.foodId] ?: continue
                categories += food.category
                if (food.category == FoodCategory.UOVA && component.unit == PortionUnit.PEZZI) {
                    uovaTot += component.quantity
                }
            }
            val hasBianca = FoodCategory.CARNE_BIANCA in categories
            val hasRossa = FoodCategory.CARNE_ROSSA in categories
            if (hasBianca) bianca++
            if (hasRossa) rossa++
            if (hasBianca || hasRossa) carne++
            if (FoodCategory.PESCE in categories) pesce++
            if (FoodCategory.UOVA in categories) uovaOcc++
            if (FoodCategory.TONNO_SGOMBRO in categories) tonno++
            if (FoodCategory.AFFETTATO in categories) affettato++
            if (FoodCategory.PATATE in categories) patate++
        }
        return WeeklyCounts(
            colazioniDolci = dolci, pastiLiberi = liberi,
            cenePrimo = verdure + proteico, cenePrimoVerdure = verdure, cenePrimoProteico = proteico,
            ceneSecondo = secondo, ceneMinestrone = minestrone,
            carne = carne, carneBianca = bianca, carneRossa = rossa, pesce = pesce,
            uovaOccasioni = uovaOcc, uovaTotale = Math.round(uovaTot).toInt(),
            tonno = tonno, affettato = affettato, patate = patate,
        )
    }
}
