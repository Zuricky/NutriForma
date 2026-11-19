package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealRole.*
import com.nutriforma.app.domain.model.MealType

/** One allowed component of a meal type: [required] = exactly 1, otherwise 0 or 1. */
data class ComponentSpec(val role: MealRole, val required: Boolean)

/** Meal composition per [MealType] (SPEC §2). Components must appear in exactly this order. */
object MealStructure {

    private fun req(role: MealRole) = ComponentSpec(role, required = true)
    private fun opt(role: MealRole) = ComponentSpec(role, required = false)

    val byType: Map<MealType, List<ComponentSpec>> = mapOf(
        MealType.COLAZIONE_STANDARD to listOf(
            req(COLAZIONE_G1), req(COLAZIONE_G2), opt(COLAZIONE_DOLCIFICANTE), opt(COLAZIONE_BEVANDA),
        ),
        MealType.COLAZIONE_DOLCE to listOf(req(COLAZIONE_DOLCE), req(COLAZIONE_DOLCE_BEVANDA)),
        MealType.SPUNTINO_MATTINA to listOf(req(SPUNTINO_SECCA)),
        MealType.SPUNTINO_POMERIGGIO to listOf(req(SPUNTINO_FRUTTA), req(SPUNTINO_ABBINAMENTO)),
        MealType.SPUNTINO_TURNO_LUNGO to listOf(req(SPUNTINO_FRUTTA), req(SPUNTINO_SECCA)),
        MealType.BARRETTA_TURNO_LUNGO to listOf(req(SPUNTINO_BARRETTA)),
        MealType.SPUNTINO_SALTATO to emptyList(),
        MealType.PRANZO_STANDARD to listOf(
            req(PRANZO_ANTIPASTO), req(PRANZO_SECONDO), req(PRANZO_ACCOMPAGNAMENTO), req(PRANZO_CONDIMENTO),
        ),
        MealType.PRANZO_LIBERO to listOf(req(MealRole.PRANZO_LIBERO)),
        MealType.CENA_PRIMO_VERDURE to listOf(
            req(CENA_VERDURE), req(CENA_PRIMO_BASE), req(CENA_PRIMO_CONDIMENTO_VERDURE),
            req(CENA_PRIMO_GRANA), req(CENA_CONDIMENTO),
        ),
        MealType.CENA_PRIMO_PROTEICO to listOf(
            req(CENA_VERDURE), req(CENA_PRIMO_BASE), req(CENA_PRIMO_CONDIMENTO_PROTEICO), req(CENA_CONDIMENTO),
        ),
        MealType.CENA_SECONDO to listOf(
            req(CENA_VERDURE), req(CENA_SECONDO), req(CENA_SECONDO_ACCOMPAGNAMENTO), req(CENA_CONDIMENTO),
        ),
        // Piatto completo: verdure di contorno + il piatto; nessun secondo e nessun condimento a parte.
        MealType.CENA_PIATTO_UNICO to listOf(req(CENA_VERDURE), req(CENA_PIATTO_UNICO)),
        MealType.CENA_MINESTRONE to listOf(
            req(CENA_VERDURE), req(CENA_MINESTRONE), req(CENA_MINESTRONE_AGGIUNTA), req(CENA_CONDIMENTO),
        ),
    )

    fun specFor(type: MealType): List<ComponentSpec> = byType.getValue(type)

    /** Every role that can appear in some meal type. */
    val allRoles: Set<MealRole> = byType.values.flatten().map { it.role }.toSet()
}
