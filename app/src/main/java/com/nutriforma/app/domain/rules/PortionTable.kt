package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealRole.*
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.PortionUnit.*

/**
 * One row of the portion table (SPEC §3).
 *
 * A row matches either a whole [category] or one specific [foodId] (exactly one of the two is non-null).
 * [gPerUnit] = grams per unit; `null` means "use [FoodItem.gramsPerUnit]" (the `food` value of the spec) when the
 * unit is not [PortionUnit.GRAMMI] / [PortionUnit.PORZIONE].
 */
data class PortionRow(
    val role: MealRole,
    val category: FoodCategory?,
    val foodId: String?,
    val min: Double,
    val max: Double,
    val unit: PortionUnit,
    val gPerUnit: Double?,
    val scalable: Boolean,
    val hintIt: String?,
) {
    init {
        require((category == null) != (foodId == null)) { "Una riga deve avere categoria oppure foodId" }
    }
}

object PortionTable {

    private fun cat(
        role: MealRole, category: FoodCategory, min: Double, max: Double = min, unit: PortionUnit = GRAMMI,
        gPerUnit: Double? = null, scalable: Boolean = false, hint: String? = null,
    ) = PortionRow(role, category, null, min, max, unit, gPerUnit, scalable, hint)

    private fun food(
        role: MealRole, foodId: String, min: Double, max: Double = min, unit: PortionUnit = GRAMMI,
        gPerUnit: Double? = null, scalable: Boolean = false, hint: String? = null,
    ) = PortionRow(role, null, foodId, min, max, unit, gPerUnit, scalable, hint)

    /** Rows in the exact order of SPEC §3. */
    val rows: List<PortionRow> = listOf(
        // Colazione
        cat(COLAZIONE_G1, FoodCategory.AFFETTATO, 40.0, hint = "circa 2 fette"),
        cat(COLAZIONE_G1, FoodCategory.UOVA, 1.0, unit = PEZZI, hint = "preferibilmente con tuorlo morbido"),
        cat(COLAZIONE_G1, FoodCategory.PESCE_AFFUMICATO, 50.0),
        food(COLAZIONE_G1, "yogurt_bianco_intero", 125.0, hint = "1 vasetto"),
        food(COLAZIONE_G1, "yogurt_greco_2", 125.0, hint = "1 vasetto"),
        food(COLAZIONE_G1, "ricotta", 50.0, hint = "½ confezione"),
        cat(COLAZIONE_G2, FoodCategory.PANE, 40.0, 50.0, scalable = true, hint = "2 fette"),
        cat(COLAZIONE_G2, FoodCategory.FETTE_BISCOTTATE, 3.0, 4.0, unit = PEZZI, hint = "preferibilmente integrali"),
        cat(COLAZIONE_G2, FoodCategory.CEREALI_COLAZIONE, 30.0, scalable = true),
        cat(COLAZIONE_G2, FoodCategory.FRUTTA_FRESCA, 150.0),
        cat(COLAZIONE_DOLCIFICANTE, FoodCategory.DOLCIFICANTE, 1.0, unit = CUCCHIAINI),
        cat(COLAZIONE_BEVANDA, FoodCategory.BEVANDA, 1.0, unit = TAZZINA),
        // Torta first: the "di preparazione casalinga" hint applies to the torta only (SPEC §3).
        food(COLAZIONE_DOLCE, "torta_casalinga", 1.0, unit = PEZZI, hint = "di preparazione casalinga"),
        cat(COLAZIONE_DOLCE, FoodCategory.DOLCE_COLAZIONE, 1.0, unit = PEZZI),
        food(COLAZIONE_DOLCE_BEVANDA, "caffe_espresso", 1.0, unit = TAZZINA),
        food(COLAZIONE_DOLCE_BEVANDA, "cappuccino", 1.0, unit = TAZZA),
        // Spuntini
        cat(SPUNTINO_SECCA, FoodCategory.FRUTTA_SECCA, 15.0, hint = "non salata (es. 12 mandorle o 3 noci)"),
        cat(SPUNTINO_SECCA, FoodCategory.MIX_FRUTTA, 20.0, 25.0),
        cat(SPUNTINO_FRUTTA, FoodCategory.FRUTTA_FRESCA, 150.0, hint = "1 frutto"),
        cat(SPUNTINO_ABBINAMENTO, FoodCategory.CIOCCOLATO, 15.0, hint = "min. 80% cacao"),
        cat(SPUNTINO_ABBINAMENTO, FoodCategory.FORMAGGIO_STAGIONATO, 20.0, hint = "1 cubetto"),
        cat(SPUNTINO_ABBINAMENTO, FoodCategory.AVOCADO, 80.0, hint = "circa ½ avocado"),
        cat(SPUNTINO_BARRETTA, FoodCategory.BARRETTA_PROTEICA, 1.0, unit = PEZZI),
        // Pranzo
        cat(PRANZO_ANTIPASTO, FoodCategory.VERDURE, 200.0, hint = "crude o cotte, a volontà"),
        cat(PRANZO_SECONDO, FoodCategory.CARNE_BIANCA, 100.0, 120.0, scalable = true, hint = "1 porzione"),
        cat(PRANZO_SECONDO, FoodCategory.CARNE_ROSSA, 100.0, 120.0, scalable = true, hint = "1 porzione"),
        cat(PRANZO_SECONDO, FoodCategory.PESCE, 150.0, 180.0, scalable = true, hint = "1 porzione"),
        cat(PRANZO_SECONDO, FoodCategory.UOVA, 2.0, unit = PEZZI),
        cat(PRANZO_SECONDO, FoodCategory.TONNO_SGOMBRO, 80.0, 100.0, hint = "1 scatolina, sgocciolato"),
        cat(PRANZO_ACCOMPAGNAMENTO, FoodCategory.PANE, 40.0, 50.0, scalable = true, hint = "1-2 fette"),
        cat(PRANZO_ACCOMPAGNAMENTO, FoodCategory.PATATE, 150.0, scalable = true, hint = "1 patata media"),
        cat(PRANZO_ACCOMPAGNAMENTO, FoodCategory.POLENTA, 100.0, scalable = true, hint = "1 fetta grigliata"),
        cat(PRANZO_ACCOMPAGNAMENTO, FoodCategory.CEREALE_CHICCO, 30.0, scalable = true, hint = "peso a crudo"),
        cat(PRANZO_ACCOMPAGNAMENTO, FoodCategory.FRUTTA_FRESCA, 150.0),
        cat(PRANZO_CONDIMENTO, FoodCategory.OLIO, 3.0, unit = CUCCHIAINI, gPerUnit = 5.0, hint = "preferibilmente a crudo"),
        cat(PRANZO_LIBERO, FoodCategory.PASTO_LIBERO, 1.0, unit = PORZIONE, hint = "es. pasticcio o sushi"),
        // Cena
        cat(CENA_VERDURE, FoodCategory.VERDURE, 200.0, hint = "antipasto o contorno, crude o cotte"),
        cat(CENA_PRIMO_BASE, FoodCategory.PASTA, 70.0, 80.0, scalable = true, hint = "peso a crudo"),
        cat(CENA_PRIMO_BASE, FoodCategory.CEREALE_CHICCO, 70.0, 80.0, scalable = true, hint = "peso a crudo"),
        cat(CENA_PRIMO_CONDIMENTO_VERDURE, FoodCategory.VERDURE, 150.0),
        cat(CENA_PRIMO_CONDIMENTO_VERDURE, FoodCategory.SALSA_POMODORO, 100.0),
        cat(CENA_PRIMO_GRANA, FoodCategory.FORMAGGIO_STAGIONATO, 2.0, unit = CUCCHIAINI, gPerUnit = 5.0, hint = "grattugiato"),
        cat(CENA_PRIMO_CONDIMENTO_PROTEICO, FoodCategory.UOVA, 1.0, unit = PEZZI),
        cat(CENA_PRIMO_CONDIMENTO_PROTEICO, FoodCategory.TONNO_SGOMBRO, 60.0, hint = "1 scatolina piccola, sgocciolato"),
        cat(CENA_PRIMO_CONDIMENTO_PROTEICO, FoodCategory.PESCE, 100.0, hint = "fresco o surgelato"),
        cat(CENA_PRIMO_CONDIMENTO_PROTEICO, FoodCategory.RAGU, 1.0, unit = CUCCHIAI, gPerUnit = 20.0),
        cat(CENA_PRIMO_CONDIMENTO_PROTEICO, FoodCategory.PESTO, 1.0, unit = CUCCHIAI, gPerUnit = 15.0),
        cat(CENA_SECONDO, FoodCategory.CARNE_BIANCA, 100.0, 120.0, scalable = true, hint = "1 porzione"),
        cat(CENA_SECONDO, FoodCategory.CARNE_ROSSA, 100.0, 120.0, scalable = true, hint = "1 porzione"),
        cat(CENA_SECONDO, FoodCategory.PESCE, 150.0, 180.0, scalable = true, hint = "1 porzione"),
        cat(CENA_SECONDO, FoodCategory.UOVA, 2.0, unit = PEZZI),
        cat(CENA_SECONDO, FoodCategory.LEGUMI, 150.0, scalable = true, hint = "lessati"),
        cat(CENA_SECONDO, FoodCategory.TONNO_SGOMBRO, 60.0, hint = "sgocciolato"),
        cat(CENA_SECONDO_ACCOMPAGNAMENTO, FoodCategory.PANE, 40.0, scalable = true),
        cat(CENA_SECONDO_ACCOMPAGNAMENTO, FoodCategory.FRUTTA_FRESCA, 150.0),
        cat(CENA_SECONDO_ACCOMPAGNAMENTO, FoodCategory.CEREALE_CHICCO, 30.0, scalable = true, hint = "peso a crudo"),
        cat(CENA_MINESTRONE, FoodCategory.MINESTRONE, 1.0, unit = PIATTO, gPerUnit = 300.0, hint = "1 piatto"),
        food(CENA_MINESTRONE_AGGIUNTA, "ricotta", 50.0, hint = "½ ricotta"),
        cat(CENA_MINESTRONE_AGGIUNTA, FoodCategory.UOVA, 1.0, unit = PEZZI),
        cat(CENA_MINESTRONE_AGGIUNTA, FoodCategory.AFFETTATO, 50.0, hint = "affettato magro"),
        cat(CENA_MINESTRONE_AGGIUNTA, FoodCategory.LEGUMI, 100.0),
        cat(CENA_CONDIMENTO, FoodCategory.OLIO, 3.0, unit = CUCCHIAINI, gPerUnit = 5.0, hint = "preferibilmente a crudo"),
    )

    /** Lookup order (SPEC §3): the row for `(role, foodId)` wins over the row for `(role, category)`. */
    fun find(role: MealRole, food: FoodItem): PortionRow? =
        rows.firstOrNull { it.role == role && it.foodId == food.id }
            ?: rows.firstOrNull { it.role == role && it.category == food.category }

    fun hasRow(role: MealRole, food: FoodItem): Boolean = find(role, food) != null

    /** Grams for one unit of [row] applied to [food], or `null` when neither the row nor the food defines it. */
    fun gramsPerUnit(row: PortionRow, food: FoodItem): Double? = when (row.unit) {
        GRAMMI -> 1.0
        PORZIONE -> 0.0
        else -> row.gPerUnit ?: food.gramsPerUnit
    }
}
