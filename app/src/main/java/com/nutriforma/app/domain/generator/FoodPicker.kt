package com.nutriforma.app.domain.generator

import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.MealRole
import kotlin.random.Random

/**
 * SPEC §7.5 concrete food choice. `score = usage / weight` (weight 2 for preferred foods); foods used in the same
 * role on the previous day are dropped when another candidate remains; ties are broken with [random] over the
 * candidates sorted by id, so the result is deterministic for a given seed.
 */
class FoodPicker(private val random: Random) {

    private val usage = HashMap<Pair<MealRole, String>, Int>()
    private val lastByRoleDay = HashMap<Pair<MealRole, Int>, String>()

    fun usageOf(role: MealRole, foodId: String): Int = usage[role to foodId] ?: 0

    fun record(role: MealRole, foodId: String, dayIndex: Int) {
        usage.merge(role to foodId, 1, Int::plus)
        lastByRoleDay[role to dayIndex] = foodId
    }

    /** Picks and records the food; `null` when [candidates] is empty. [avoidFoodId] is a soft exclusion. */
    fun pick(role: MealRole, candidates: List<FoodItem>, dayIndex: Int, avoidFoodId: String? = null): FoodItem? {
        if (candidates.isEmpty()) return null
        var pool = candidates.sortedBy { it.id }

        lastByRoleDay[role to dayIndex - 1]?.let { previous ->
            pool.filter { it.id != previous }.takeIf { it.isNotEmpty() }?.let { pool = it }
        }
        if (avoidFoodId != null) {
            pool.filter { it.id != avoidFoodId }.takeIf { it.isNotEmpty() }?.let { pool = it }
        }

        fun score(food: FoodItem) = usageOf(role, food.id) / (if (food.isPreferred) 2.0 else 1.0)
        val best = pool.minOf { score(it) }
        val ties = pool.filter { score(it) == best }
        val chosen = ties[random.nextInt(ties.size)]
        record(role, chosen.id, dayIndex)
        return chosen
    }
}
