package com.nutriforma.app.domain.nutrition

import com.nutriforma.app.domain.model.FoodItem
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.rules.PortionTable
import kotlin.math.floor

data class ResolvedPortion(
    val quantity: Double,
    val unit: PortionUnit,
    val grams: Double,
    val hintIt: String?,
)

/** SPEC §6.1: base quantity from the goal, then sport scaling for scalable gram rows. */
class PortionCalculator {

    fun portionFor(role: MealRole, food: FoodItem, profile: UserProfile): ResolvedPortion =
        portionFor(role, food, profile.goal, profile.sportLevel)

    fun portionFor(role: MealRole, food: FoodItem, goal: Goal, sportLevel: SportLevel): ResolvedPortion {
        val row = PortionTable.find(role, food)
            ?: throw IllegalArgumentException("Nessuna riga porzione per ${role.name} / ${food.id}")

        val base = if (row.unit == PortionUnit.GRAMMI) {
            when (goal) {
                Goal.DIMAGRIMENTO -> row.min
                Goal.EQUILIBRIO -> roundTo5((row.min + row.max) / 2.0)
                Goal.ENERGIA -> row.max
            }
        } else {
            if (goal == Goal.ENERGIA) row.max else row.min
        }

        val quantity = if (row.scalable && row.unit == PortionUnit.GRAMMI) {
            roundTo5(base * sportLevel.portionFactor)
        } else {
            base
        }

        val grams = when (row.unit) {
            PortionUnit.GRAMMI -> quantity
            PortionUnit.PORZIONE -> quantity * (row.gPerUnit ?: 0.0)
            else -> {
                val perUnit = row.gPerUnit ?: food.gramsPerUnit
                    ?: throw IllegalArgumentException("Grammi per unità mancanti: ${food.id}")
                quantity * perUnit
            }
        }
        return ResolvedPortion(quantity = quantity, unit = row.unit, grams = grams, hintIt = row.hintIt)
    }

    companion object {
        /** Half-up rounding to the nearest multiple of 5 (SPEC §6.1). */
        fun roundTo5(x: Double): Double = (floor(x / 5.0 + 0.5) * 5).toDouble()
    }
}
