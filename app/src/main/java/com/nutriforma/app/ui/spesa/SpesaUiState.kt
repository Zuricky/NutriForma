package com.nutriforma.app.ui.spesa

import com.nutriforma.app.data.repository.ShoppingExtra
import com.nutriforma.app.domain.shopping.ShoppingGroup
import com.nutriforma.app.domain.shopping.ShoppingItem
import com.nutriforma.app.ui.food.FoodIcon
import com.nutriforma.app.ui.food.FoodIcons

/** Una riga della lista. [key] = `food:<id>` per gli alimenti della dieta, `extra:<id>` per le voci manuali. */
data class SpesaRow(val key: String, val title: String, val isChecked: Boolean, val isExtra: Boolean, val icon: FoodIcon)

data class SpesaSectionUi(val label: String, val rows: List<SpesaRow>)

data class SpesaContent(
    val toBuy: List<SpesaSectionUi>,
    val purchased: List<SpesaSectionUi>,
    val toBuyCount: Int,
    val purchasedCount: Int,
) {
    val isEmpty: Boolean get() = toBuyCount == 0 && purchasedCount == 0
}

const val FOOD_KEY_PREFIX = "food:"
const val EXTRA_KEY_PREFIX = "extra:"

sealed interface SpesaUiState {
    data object Loading : SpesaUiState
    data class Error(val message: String) : SpesaUiState
    data class Ready(
        val wholeWeek: Boolean,
        val selectedDays: Set<Int>,
        val content: SpesaContent,
        val confirmClear: Boolean,
    ) : SpesaUiState
}

/** Unisce alimenti della dieta ed extra manuali e li divide in "Da comprare" / "Già in casa / Acquistato". */
fun buildSpesaContent(items: List<ShoppingItem>, extras: List<ShoppingExtra>): SpesaContent {
    val rows = buildList {
        items.forEach { add(it.group to SpesaRow(FOOD_KEY_PREFIX + it.foodId, it.title, it.isChecked, false, FoodIcons.forFoodId(it.foodId, it.category))) }
        extras.forEach { add(ShoppingGroup.FRUTTA_SECCA_EXTRA to SpesaRow(EXTRA_KEY_PREFIX + it.id, it.name, it.isChecked, true, FoodIcon.BASKET)) }
    }
    fun sections(checked: Boolean): List<SpesaSectionUi> =
        rows.filter { it.second.isChecked == checked }
            .groupBy({ it.first }, { it.second })
            .toSortedMap(compareBy { it.order })
            .map { (group, list) -> SpesaSectionUi(group.labelIt, list) }
    val toBuy = sections(false)
    val purchased = sections(true)
    return SpesaContent(toBuy, purchased, toBuy.sumOf { it.rows.size }, purchased.sumOf { it.rows.size })
}
