package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.UserProfile

/** One weekly counter rule of SPEC §4.2. `max == null` means no upper bound. */
data class RuleDefinition(
    val ruleId: RuleId,
    val value: (WeeklyCounts) -> Int,
    val min: (UserProfile) -> Int,
    val max: (UserProfile) -> Int?,
)

object DietRules {

    private fun fixed(id: RuleId, min: Int, max: Int?, value: (WeeklyCounts) -> Int) =
        RuleDefinition(id, value, { min }, { max })

    /** SPEC §4.2, in report order. */
    val counterRules: List<RuleDefinition> = listOf(
        fixed(RuleId.COLAZIONE_DOLCE, 2, 2) { it.colazioniDolci },
        RuleDefinition(
            RuleId.PASTO_LIBERO, { it.pastiLiberi },
            { it.cheatMealsPerWeek.coerceIn(1, 2) }, { it.cheatMealsPerWeek.coerceIn(1, 2) },
        ),
        fixed(RuleId.CENA_PRIMO, 3, 4) { it.cenePrimo },
        fixed(RuleId.CENA_PRIMO_VERDURE, 2, 2) { it.cenePrimoVerdure },
        fixed(RuleId.CENA_PRIMO_PROTEICO, 2, 2) { it.cenePrimoProteico },
        fixed(RuleId.CENA_SECONDO, 1, 2) { it.ceneSecondo },
        fixed(RuleId.CENA_MINESTRONE, 1, 1) { it.ceneMinestrone },
        fixed(RuleId.CARNE, 0, 4) { it.carne },
        fixed(RuleId.CARNE_ROSSA, 0, 1) { it.carneRossa },
        fixed(RuleId.PESCE, 2, null) { it.pesce },
        fixed(RuleId.UOVA_OCCASIONI, 2, 3) { it.uovaOccasioni },
        fixed(RuleId.UOVA_TOTALE, 5, 6) { it.uovaTotale },
        fixed(RuleId.TONNO, 0, 1) { it.tonno },
        fixed(RuleId.AFFETTATO, 0, 2) { it.affettato },
        fixed(RuleId.PATATE, 0, 1) { it.patate },
    )

    /** Rules driven by ingredients (the others are structural: they depend only on meal types). */
    val ingredientCapRules: List<RuleDefinition> = counterRules.filter {
        it.ruleId in setOf(
            RuleId.CARNE, RuleId.CARNE_ROSSA, RuleId.UOVA_OCCASIONI, RuleId.UOVA_TOTALE,
            RuleId.TONNO, RuleId.AFFETTATO, RuleId.PATATE,
        )
    }

    /** "{min}" if min==max, "max {max}" if min==0, "almeno {min}" if unbounded, else "{min}-{max}". */
    fun rangeText(min: Int, max: Int?): String = when {
        max != null && min == max -> "$min"
        min == 0 && max != null -> "max $max"
        max == null -> "almeno $min"
        else -> "$min-$max"
    }
}
