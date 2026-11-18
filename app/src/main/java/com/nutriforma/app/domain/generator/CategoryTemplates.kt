package com.nutriforma.app.domain.generator

import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodCategory.*
import com.nutriforma.app.domain.model.MealType

/** Where a template category is used: the fallback chains of SPEC §7.4 depend on it. */
enum class ChainContext {
    PRANZO_SECONDO, CENA_SECONDO, PRIMO_PROTEICO, COLAZIONE_G1, COLAZIONE_G2, PRANZO_ACC, CENA_ACC,
    PRIMO_BASE, PRIMO_COND_VERDURE, SECCA, ABBINAMENTO, MINESTRONE_AGGIUNTA, GENERIC,
}

/** SPEC §7.2-7.4: fixed day patterns, category templates and fallback chains. */
object CategoryTemplates {

    /** Colazioni dolci: Mercoledì e Domenica. */
    val DOLCE_DAYS: Set<Int> = setOf(2, 6)

    /** Pasto libero days by `cheatMealsPerWeek`. */
    fun liberoDays(cheatMealsPerWeek: Int): Set<Int> = if (cheatMealsPerWeek >= 2) setOf(2, 5) else setOf(5)

    /** Fixed dinner pattern (SPEC §7.2), index = dayIndex. */
    val CENA_TYPES = listOf(
        MealType.CENA_PRIMO_VERDURE,
        MealType.CENA_SECONDO,
        MealType.CENA_PRIMO_PROTEICO,
        MealType.CENA_MINESTRONE,
        MealType.CENA_PRIMO_VERDURE,
        MealType.CENA_PRIMO_PROTEICO,
        MealType.CENA_SECONDO,
    )

    fun pranzoSecondi(cheatMealsPerWeek: Int): List<FoodCategory> =
        if (cheatMealsPerWeek >= 2) {
            listOf(CARNE_BIANCA, PESCE, CARNE_ROSSA, UOVA, PESCE)
        } else {
            listOf(CARNE_BIANCA, PESCE, CARNE_ROSSA, UOVA, PESCE, CARNE_BIANCA)
        }

    val PRANZO_ACCOMPAGNAMENTI = listOf(PANE, CEREALE_CHICCO, PATATE, PANE, CEREALE_CHICCO, POLENTA)

    /** Colazione G1 by dayIndex; days not listed (a dolce day turned standard) use LATTICINI. */
    val COLAZIONE_G1_BY_DAY: Map<Int, FoodCategory> =
        mapOf(0 to LATTICINI, 1 to PESCE_AFFUMICATO, 3 to AFFETTATO, 4 to UOVA, 5 to LATTICINI)

    val COLAZIONE_G2 = listOf(PANE, FETTE_BISCOTTATE, CEREALI_COLAZIONE, PANE, FRUTTA_FRESCA)

    val PRIMO_BASE = listOf(PASTA, CEREALE_CHICCO, PASTA, PASTA)

    val PRIMO_COND_VERDURE = listOf(VERDURE, SALSA_POMODORO)

    /** Primo proteico on the second occurrence: one of these, chosen at random among the available ones. */
    val PRIMO_PROTEICO_SET = listOf(PESTO, RAGU, TONNO_SGOMBRO)

    val MINESTRONE_AGGIUNTA_SET = listOf(LATTICINI, LEGUMI, AFFETTATO)

    val ABBINAMENTI = listOf(CIOCCOLATO, FORMAGGIO_STAGIONATO, AVOCADO)

    fun abbinamentoFor(dayIndex: Int): FoodCategory = ABBINAMENTI[dayIndex % 3]

    fun oppositeSecca(previous: FoodCategory?): FoodCategory = when (previous) {
        null -> FRUTTA_SECCA
        FRUTTA_SECCA -> MIX_FRUTTA
        else -> FRUTTA_SECCA
    }

    /** Fallback chain of [template] in [context] (SPEC §7.4 table), without the template itself. */
    fun fallbackChain(context: ChainContext, template: FoodCategory): List<FoodCategory> = when (context) {
        ChainContext.PRANZO_SECONDO, ChainContext.CENA_SECONDO -> when (template) {
            CARNE_BIANCA -> listOf(PESCE, CARNE_ROSSA, UOVA, TONNO_SGOMBRO)
            CARNE_ROSSA -> listOf(CARNE_BIANCA, PESCE, UOVA, TONNO_SGOMBRO)
            PESCE -> listOf(CARNE_BIANCA, UOVA, TONNO_SGOMBRO, CARNE_ROSSA)
            UOVA ->
                if (context == ChainContext.PRANZO_SECONDO) listOf(PESCE, CARNE_BIANCA, TONNO_SGOMBRO)
                else listOf(PESCE, LEGUMI, CARNE_BIANCA, TONNO_SGOMBRO)
            LEGUMI -> listOf(PESCE, UOVA, CARNE_BIANCA)
            else -> emptyList()
        }
        ChainContext.PRIMO_PROTEICO -> when (template) {
            PESCE -> listOf(TONNO_SGOMBRO, PESTO, RAGU, UOVA)
            PESTO, RAGU, TONNO_SGOMBRO -> PRIMO_PROTEICO_SET.filter { it != template } + listOf(PESCE, UOVA)
            else -> emptyList()
        }
        ChainContext.COLAZIONE_G1 -> when (template) {
            LATTICINI -> listOf(PESCE_AFFUMICATO, AFFETTATO, UOVA)
            PESCE_AFFUMICATO -> listOf(LATTICINI, AFFETTATO, UOVA)
            AFFETTATO -> listOf(LATTICINI, PESCE_AFFUMICATO, UOVA)
            UOVA -> listOf(LATTICINI, PESCE_AFFUMICATO, AFFETTATO)
            else -> emptyList()
        }
        ChainContext.COLAZIONE_G2 ->
            listOf(PANE, FETTE_BISCOTTATE, CEREALI_COLAZIONE, FRUTTA_FRESCA).filter { it != template }
        ChainContext.PRANZO_ACC -> when (template) {
            PANE -> listOf(CEREALE_CHICCO, POLENTA, FRUTTA_FRESCA)
            CEREALE_CHICCO -> listOf(PANE, POLENTA, FRUTTA_FRESCA)
            PATATE -> listOf(POLENTA, PANE, CEREALE_CHICCO)
            POLENTA -> listOf(PANE, CEREALE_CHICCO, FRUTTA_FRESCA)
            else -> emptyList()
        }
        ChainContext.CENA_ACC -> when (template) {
            PANE -> listOf(CEREALE_CHICCO, FRUTTA_FRESCA)
            CEREALE_CHICCO -> listOf(PANE, FRUTTA_FRESCA)
            else -> emptyList()
        }
        ChainContext.PRIMO_BASE -> when (template) {
            PASTA -> listOf(CEREALE_CHICCO)
            CEREALE_CHICCO -> listOf(PASTA)
            else -> emptyList()
        }
        ChainContext.PRIMO_COND_VERDURE -> when (template) {
            VERDURE -> listOf(SALSA_POMODORO)
            SALSA_POMODORO -> listOf(VERDURE)
            else -> emptyList()
        }
        ChainContext.SECCA -> when (template) {
            FRUTTA_SECCA -> listOf(MIX_FRUTTA)
            MIX_FRUTTA -> listOf(FRUTTA_SECCA)
            else -> emptyList()
        }
        ChainContext.ABBINAMENTO -> {
            val i = ABBINAMENTI.indexOf(template)
            if (i < 0) emptyList() else listOf(ABBINAMENTI[(i + 1) % 3], ABBINAMENTI[(i + 2) % 3])
        }
        ChainContext.MINESTRONE_AGGIUNTA -> listOf(UOVA)
        ChainContext.GENERIC -> emptyList()
    }
}
