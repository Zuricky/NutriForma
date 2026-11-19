package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealType

/**
 * Avvisi (in italiano) quando le esclusioni "Non mi piace / Intolleranze" lasciano troppo pochi alimenti
 * in un gruppo obbligatorio di un pasto.
 */
object ExclusionAdvisor {

    /** Sotto questa soglia di alternative rimaste il gruppo è considerato "quasi esaurito". */
    private const val LOW_AVAILABILITY = 1

    /** Alternative facoltative a un altro pasto (il piatto unico sostituisce il primo proteico): non servono avvisi. */
    private val ALTERNATIVE_ONLY_TYPES = setOf(MealType.CENA_PIATTO_UNICO)

    fun warnings(foods: List<FoodItem>, disliked: Set<String>): List<String> {
        val requiredRoles: Map<MealRole, List<MealType>> = MealType.entries
            .filter { it !in ALTERNATIVE_ONLY_TYPES }
            .flatMap { type -> MealStructure.specFor(type).filter { it.required }.map { it.role to type } }
            .groupBy({ it.first }, { it.second })

        val missing = mutableListOf<String>()
        val scarce = mutableListOf<String>()
        for ((role, types) in requiredRoles) {
            val all = foods.filter { role in it.roles && PortionTable.hasRow(role, it) }
            val left = all.count { it.id !in disliked }
            if (left == all.size) continue
            val where = types.map { it.labelIt }.distinct().joinToString(", ")
            val name = "«${role.labelIt}» ($where)"
            when {
                left == 0 -> missing += "Hai escluso tutti gli alimenti per $name: questi pasti non potranno essere composti."
                left <= LOW_AVAILABILITY && all.size > LOW_AVAILABILITY + 1 -> scarce += "Per $name ti resta un solo alimento: i pasti saranno molto ripetitivi."
            }
        }
        return (missing + scarce).distinct()
    }
}
