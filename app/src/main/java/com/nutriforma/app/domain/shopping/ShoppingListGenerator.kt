package com.nutriforma.app.domain.shopping

import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.PlannedMeal
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.SupermarketCategory
import kotlin.math.roundToInt

/**
 * Reparti della lista della spesa mostrati all'utente.
 * DECISION: i 13 [SupermarketCategory] del catalogo sono raggruppati negli 8 reparti.
 */
enum class ShoppingGroup(val labelIt: String, val order: Int) {
    ORTOFRUTTA("Ortofrutta", 0),
    CARNE_AFFETTATI("Carne e Affettati", 1),
    PESCE("Pesce", 2),
    LATTICINI_UOVA("Latticini e Uova", 3),
    CEREALI_PANE("Cereali e Pane", 4),
    DISPENSA("Dispensa e Condimenti", 5),
    FRUTTA_SECCA_EXTRA("Frutta Secca e Extra", 6),
    GASTRONOMIA("Gastronomia e Piatti Pronti", 7),
    ;

    companion object {
        fun of(food: FoodItem): ShoppingGroup? {
            // I formaggi stagionati stanno con i latticini, non con gli affettati.
            if (food.category == FoodCategory.FORMAGGIO_STAGIONATO) return LATTICINI_UOVA
            return when (food.supermarketCategory ?: return null) {
                SupermarketCategory.FRUTTA_VERDURA -> ORTOFRUTTA
                SupermarketCategory.CARNE, SupermarketCategory.SALUMI_FORMAGGI -> CARNE_AFFETTATI
                SupermarketCategory.PESCE -> PESCE
                SupermarketCategory.LATTICINI_UOVA -> LATTICINI_UOVA
                SupermarketCategory.PANE_FORNO, SupermarketCategory.PASTA_RISO_CEREALI -> CEREALI_PANE
                SupermarketCategory.CONSERVE_SUGHI, SupermarketCategory.SURGELATI,
                SupermarketCategory.DISPENSA, SupermarketCategory.BEVANDE -> DISPENSA
                SupermarketCategory.FRUTTA_SECCA_SNACK -> FRUTTA_SECCA_EXTRA
                SupermarketCategory.GASTRONOMIA -> GASTRONOMIA
            }
        }
    }
}

/**
 * Una voce aggregata della spesa. [unit] è [PortionUnit.GRAMMI], [PortionUnit.PEZZI], [PortionUnit.MILLILITRI]
 * (bevande) o [PortionUnit.PORZIONE] (piatti completi, mostrati solo con il nome del piatto).
 */
data class ShoppingItem(
    val foodId: String,
    val name: String,
    val quantity: Int,
    val unit: PortionUnit,
    val group: ShoppingGroup,
    val isChecked: Boolean,
    /** Categoria dell'alimento (per l'icona nella lista). */
    val category: FoodCategory? = null,
) {
    /** Riga completa in italiano, es. "6 uova", "360 g petto di pollo". */
    val title: String
        get() = when {
            foodId == EGG_ID -> if (quantity == 1) "1 uovo" else "$quantity uova"
            unit == PortionUnit.PEZZI -> "$quantity pz ${name.lowercaseFirst()}"
            unit == PortionUnit.MILLILITRI -> "$quantity ml ${name.lowercaseFirst()}"
            unit == PortionUnit.PORZIONE -> "$name — $quantity ${if (quantity == 1) PortionUnit.PORZIONE.singularIt else PortionUnit.PORZIONE.pluralIt}"
            else -> "$quantity g ${name.lowercaseFirst()}"
        }

    private companion object {
        const val EGG_ID = "uovo"
    }
}

data class ShoppingSection(val group: ShoppingGroup, val items: List<ShoppingItem>)

/** Selezione dei giorni (0 = Lunedì … 6 = Domenica) per cui generare la spesa. */
sealed interface DayRange {
    data object WholeWeek : DayRange
    data class Days(val dayIndexes: Set<Int>) : DayRange
}

/**
 * Aggrega gli ingredienti dei pasti pianificati. Funzione pura: ricalcolarla dopo uno scambio di pasto
 * restituisce subito la lista aggiornata, mentre le spunte (per `foodId`) restano valide.
 */
class ShoppingListGenerator {

    fun generate(
        meals: List<PlannedMeal>,
        foodsById: Map<String, FoodItem>,
        checkedFoodIds: Set<String> = emptySet(),
        range: DayRange = DayRange.WholeWeek,
    ): List<ShoppingItem> {
        val inRange = meals.filter { range.contains(it.dayIndex) }
        val totals = LinkedHashMap<String, Totals>()
        for (component in inRange.flatMap { it.components }) {
            val food = foodsById[component.foodId] ?: continue
            ShoppingGroup.of(food) ?: continue // es. "pasto libero": non si compra
            val t = totals.getOrPut(food.id) { Totals() }
            t.grams += component.grams
            t.quantities[component.unit] = (t.quantities[component.unit] ?: 0.0) + component.quantity
        }
        return totals.mapNotNull { (foodId, t) ->
            val food = foodsById.getValue(foodId)
            val group = ShoppingGroup.of(food) ?: return@mapNotNull null
            // Un'unica unità "naturale" (pezzi, ml, porzioni) → quantità in quell'unità; altrimenti somma dei grammi.
            val single = t.quantities.keys.singleOrNull()
            val unit = if (single in NATURAL_UNITS) single!! else PortionUnit.GRAMMI
            val quantity = (if (unit == PortionUnit.GRAMMI) t.grams else t.quantities.getValue(unit)).roundToInt()
            if (quantity <= 0) return@mapNotNull null
            ShoppingItem(
                foodId = foodId,
                name = if (foodId == "uovo") "Uova" else food.nameIt,
                quantity = quantity,
                unit = unit,
                group = group,
                isChecked = foodId in checkedFoodIds,
                category = food.category,
            )
        }.sortedWith(compareBy({ it.group.order }, { it.name.lowercase() }))
    }

    /** Raggruppa per reparto (ordine fisso), omettendo i reparti vuoti. */
    fun toSections(items: List<ShoppingItem>): List<ShoppingSection> =
        items.groupBy { it.group }
            .toSortedMap(compareBy { it.order })
            .map { (group, list) -> ShoppingSection(group, list) }

    private fun DayRange.contains(dayIndex: Int): Boolean = when (this) {
        DayRange.WholeWeek -> true
        is DayRange.Days -> dayIndex in dayIndexes
    }

    private class Totals {
        var grams = 0.0
        val quantities = LinkedHashMap<PortionUnit, Double>()
    }

    private companion object {
        val NATURAL_UNITS = setOf(PortionUnit.PEZZI, PortionUnit.MILLILITRI, PortionUnit.PORZIONE)
    }
}

private fun String.lowercaseFirst(): String =
    if (isNotEmpty() && (length == 1 || !this[1].isUpperCase())) replaceFirstChar { it.lowercase() } else this
