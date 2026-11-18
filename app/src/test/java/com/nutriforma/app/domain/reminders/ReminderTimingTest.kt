package com.nutriforma.app.domain.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderTimingTest {
    private val zone = ZoneId.of("Europe/Rome")

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun mealTimeLaterTodayOrTomorrow() {
        val now = millis(2026, 11, 17, 7, 0)
        assertEquals(millis(2026, 11, 17, 7, 30), nextTriggerMillis(now, 450, zone))
        assertEquals(millis(2026, 11, 18, 7, 30), nextTriggerMillis(millis(2026, 11, 17, 7, 30), 450, zone))
        assertEquals(millis(2026, 11, 18, 7, 30), nextTriggerMillis(millis(2026, 11, 17, 20, 0), 450, zone))
    }

    @Test
    fun waterFollowsIntervalInsideWindow() {
        // finestra 08:00-21:00 ogni 2 ore
        assertEquals(millis(2026, 11, 17, 10, 0), nextWaterTriggerMillis(millis(2026, 11, 17, 9, 15), 120, 480, 1260, zone))
        assertEquals(millis(2026, 11, 17, 8, 0), nextWaterTriggerMillis(millis(2026, 11, 17, 6, 0), 120, 480, 1260, zone))
        assertEquals(millis(2026, 11, 17, 20, 0), nextWaterTriggerMillis(millis(2026, 11, 17, 18, 30), 120, 480, 1260, zone))
    }

    @Test
    fun waterAfterWindowStartsTomorrow() {
        assertEquals(millis(2026, 11, 18, 8, 0), nextWaterTriggerMillis(millis(2026, 11, 17, 20, 30), 120, 480, 1260, zone))
    }

    @Test
    fun weighInPicksNextMatchingWeekday() {
        // 17/11/2026 è un martedì; lunedì ISO = 1 -> 23/11
        assertEquals(millis(2026, 11, 23, 8, 0), nextWeighInMillis(millis(2026, 11, 17, 10, 0), 1, 480, zone))
        // stesso giorno, ora ancora futura
        assertEquals(millis(2026, 11, 17, 18, 0), nextWeighInMillis(millis(2026, 11, 17, 10, 0), 2, 1080, zone))
        // stesso giorno, ora passata -> settimana dopo
        assertEquals(millis(2026, 11, 24, 8, 0), nextWeighInMillis(millis(2026, 11, 17, 10, 0), 2, 480, zone))
    }
}
