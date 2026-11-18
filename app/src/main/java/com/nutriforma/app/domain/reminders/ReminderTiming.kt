package com.nutriforma.app.domain.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private fun at(date: LocalDate, minutesOfDay: Int, zone: ZoneId): ZonedDateTime =
    LocalDateTime.of(date, LocalTime.of(minutesOfDay.coerceIn(0, 1439) / 60, minutesOfDay.coerceIn(0, 1439) % 60)).atZone(zone)

/** Prossimo istante (strettamente dopo [nowMillis]) in cui l'orologio segna [minutesOfDay]. */
fun nextTriggerMillis(nowMillis: Long, minutesOfDay: Int, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val today = at(now.toLocalDate(), minutesOfDay, zone)
    return (if (today.isAfter(now)) today else at(now.toLocalDate().plusDays(1), minutesOfDay, zone)).toInstant().toEpochMilli()
}

/**
 * Prossimo promemoria acqua: ogni [intervalMinutes] tra [startMinutes] e [endMinutes] (inclusi) di ogni giorno.
 * Passata l'ultima fascia di oggi, riparte da [startMinutes] di domani.
 */
fun nextWaterTriggerMillis(nowMillis: Long, intervalMinutes: Int, startMinutes: Int, endMinutes: Int, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val interval = intervalMinutes.coerceAtLeast(15)
    val end = endMinutes.coerceAtLeast(startMinutes)
    var minute = startMinutes
    while (minute <= end) {
        val candidate = at(now.toLocalDate(), minute, zone)
        if (candidate.isAfter(now)) return candidate.toInstant().toEpochMilli()
        minute += interval
    }
    return at(now.toLocalDate().plusDays(1), startMinutes, zone).toInstant().toEpochMilli()
}

/** Prossimo giorno della settimana ISO ([isoDayOfWeek], 1 = lunedì) all'ora [minutesOfDay]. */
fun nextWeighInMillis(nowMillis: Long, isoDayOfWeek: Int, minutesOfDay: Int, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val target = isoDayOfWeek.coerceIn(1, 7)
    for (offset in 0L..7L) {
        val date = now.toLocalDate().plusDays(offset)
        if (date.dayOfWeek.value != target) continue
        val candidate = at(date, minutesOfDay, zone)
        if (candidate.isAfter(now)) return candidate.toInstant().toEpochMilli()
    }
    error("unreachable")
}
