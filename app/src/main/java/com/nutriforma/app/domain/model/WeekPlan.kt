package com.nutriforma.app.domain.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Serializable
data class TurnoLungoDay(val dayIndex: Int, val option: TurnoLungoOption, val slot: MealSlot)

data class WeekPlan(
    val id: Long,
    val weekStart: LocalDate,
    val seed: Long,
    val goal: Goal,
    val sportLevel: SportLevel,
    val cheatMealsPerWeek: Int,
    val turnoLungoDays: List<TurnoLungoDay>,
    val meals: List<PlannedMeal>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

object DayNames {
    val it: List<String> = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
    val short: List<String> = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
}

/** Monday of the ISO week containing this date. */
fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** 0 = Lunedì … 6 = Domenica. */
fun LocalDate.dayIndex(): Int = dayOfWeek.value - 1
