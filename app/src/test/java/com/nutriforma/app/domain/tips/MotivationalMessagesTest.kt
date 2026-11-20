package com.nutriforma.app.domain.tips

import com.nutriforma.app.domain.model.BodyMeasurement
import com.nutriforma.app.domain.model.Goal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MotivationalMessagesTest {

    private val today = LocalDate.of(2026, 11, 20)

    private fun weight(daysAgo: Long, kg: Double, waist: Double? = null) =
        BodyMeasurement(today.minusDays(daysAgo), kg, waist, null, null)

    // ---- Acqua ---------------------------------------------------------------------------------------------

    @Test
    fun waterComplimentAppearsExactlyWhenTheGoalIsReached() {
        assertNull(MotivationalMessages.forWater(0, 1700, today))
        assertNull(MotivationalMessages.forWater(1699, 1700, today))
        val reached = MotivationalMessages.forWater(1700, 1700, today)
        assertEquals(MotivationTrigger.WATER_GOAL, reached!!.trigger)
        assertTrue(reached.text in MotivationalMessages.WATER_PHRASES)
        assertNotNull(MotivationalMessages.forWater(3000, 1700, today))
    }

    @Test
    fun waterComplimentIgnoresAnInvalidGoal() {
        assertNull(MotivationalMessages.forWater(500, 0, today))
        assertNull(MotivationalMessages.forWater(500, -1, today))
    }

    // ---- Giornata / settimana --------------------------------------------------------------------------------

    @Test
    fun dayComplimentNeedsEveryPlannedMealCompleted() {
        assertNull("nessun pasto: niente da celebrare", MotivationalMessages.forDay(0, 0, today))
        assertNull(MotivationalMessages.forDay(5, 0, today))
        assertNull(MotivationalMessages.forDay(5, 4, today))
        val done = MotivationalMessages.forDay(5, 5, today)
        assertEquals(MotivationTrigger.DAY_COMPLETED, done!!.trigger)
        assertTrue(done.text in MotivationalMessages.DAY_PHRASES)
    }

    @Test
    fun weekComplimentNeedsStrongCompletionAndNoViolations() {
        // 100 pasti: la soglia è l'85%.
        assertNull(MotivationalMessages.forWeek(100, 84, hasViolations = false, date = today))
        val strong = MotivationalMessages.forWeek(100, 85, hasViolations = false, date = today)
        assertEquals(MotivationTrigger.WEEK_STRONG, strong!!.trigger)
        assertTrue(strong.text in MotivationalMessages.WEEK_PHRASES)
        assertNotNull(MotivationalMessages.forWeek(100, 100, hasViolations = false, date = today))
        assertNull("una regola superata cancella il complimento", MotivationalMessages.forWeek(100, 100, hasViolations = true, date = today))
        assertNull(MotivationalMessages.forWeek(0, 0, hasViolations = false, date = today))
    }

    // ---- Progressi verso l'obiettivo -------------------------------------------------------------------------

    @Test
    fun weightLossIsCelebratedUnderDimagrimento() {
        val m = listOf(weight(30, 82.0), weight(10, 80.5), weight(0, 79.9))
        val message = MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, m, today)!!
        assertEquals(MotivationTrigger.GOAL_PROGRESS, message.trigger)
        assertTrue(message.text in MotivationalMessages.LOSS_PHRASES)
        assertEquals("−2,1 kg dall'inizio", message.detail)
    }

    @Test
    fun noWeightLossComplimentWhenTheWeightWentUpOrIsSingle() {
        assertNull(MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, listOf(weight(30, 80.0), weight(0, 81.0)), today))
        assertNull(MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, listOf(weight(0, 80.0)), today))
        assertNull(MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, emptyList(), today))
        assertNull("stesso peso: nessuna perdita", MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, listOf(weight(30, 80.0), weight(0, 80.0)), today))
    }

    @Test
    fun aSmallerWaistIsCelebratedUnderDimagrimentoEvenIfTheWeightIsFlat() {
        val m = listOf(weight(30, 80.0, waist = 90.0), weight(0, 80.0, waist = 88.5))
        val message = MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, m, today)!!
        assertTrue(message.text in MotivationalMessages.WAIST_PHRASES)
        assertEquals("−1,5 cm dall'inizio", message.detail)
    }

    @Test
    fun stableWeightIsTheGoalForEquilibrioAndEnergia() {
        val m = listOf(weight(28, 70.0), weight(14, 70.4), weight(0, 70.3))
        for (goal in listOf(Goal.EQUILIBRIO, Goal.ENERGIA)) {
            val message = MotivationalMessages.forProgress(goal, m, today)!!
            assertTrue(message.text in MotivationalMessages.STABLE_PHRASES)
            assertEquals("+0,3 kg dall'inizio", message.detail)
        }
    }

    @Test
    fun stableWeightNeedsTimeAndASmallSwing() {
        // Due misure troppo vicine: non dicono ancora nulla di stabile.
        assertNull(MotivationalMessages.forProgress(Goal.EQUILIBRIO, listOf(weight(3, 70.0), weight(0, 70.0)), today))
        // Oscillazione oltre 1 kg: niente complimento di stabilità.
        assertNull(MotivationalMessages.forProgress(Goal.EQUILIBRIO, listOf(weight(30, 70.0), weight(0, 71.5)), today))
        // Oltre il tetto di giorni e dentro la tolleranza: ok.
        assertNotNull(MotivationalMessages.forProgress(Goal.EQUILIBRIO, listOf(weight(14, 70.0), weight(0, 71.0)), today))
    }

    @Test
    fun consistentTrackingIsCelebratedWhenThereIsNoGoalProgress() {
        val m = listOf(weight(10, 81.0), weight(5, 81.5), weight(0, 82.0)) // dimagrimento fallito, ma 3 misure in 14 giorni
        val message = MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, m, today)!!
        assertEquals(MotivationTrigger.TRACKING_STREAK, message.trigger)
        assertTrue(message.text in MotivationalMessages.TRACKING_PHRASES)
        assertNull(message.detail)
    }

    @Test
    fun trackingNeedsEnoughDistinctRecentDays() {
        val twoOnly = listOf(weight(5, 81.0), weight(0, 82.0))
        assertNull(MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, twoOnly, today))
        // Misure vecchie (fuori dai 14 giorni) non contano.
        val old = listOf(weight(40, 81.0), weight(30, 81.5), weight(20, 82.0), weight(0, 82.5))
        assertNull(MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, old, today))
        // Più righe nello stesso giorno non fanno costanza.
        val sameDay = listOf(weight(0, 82.0), weight(0, 82.0, waist = 90.0), weight(0, 82.0, waist = 91.0))
        assertNull(MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, sameDay, today))
        // Il confine: 14 giorni inclusi (oggi e i 13 precedenti).
        val edge = listOf(weight(13, 81.0), weight(7, 81.5), weight(0, 82.0))
        assertNotNull(MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, edge, today))
        val justOut = listOf(weight(14, 81.0), weight(7, 81.5), weight(0, 82.0))
        assertNull(MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, justOut, today))
    }

    @Test
    fun goalProgressWinsOverTrackingStreak() {
        val m = listOf(weight(12, 82.0), weight(6, 81.0), weight(0, 80.0))
        assertEquals(MotivationTrigger.GOAL_PROGRESS, MotivationalMessages.forProgress(Goal.DIMAGRIMENTO, m, today)!!.trigger)
    }

    // ---- Determinismo e qualità delle frasi ---------------------------------------------------------------------

    @Test
    fun theSameDateAndTriggerAlwaysGiveTheSamePhrase() {
        repeat(20) {
            assertEquals(
                MotivationalMessages.forWater(2000, 1700, today),
                MotivationalMessages.forWater(2000, 1700, today),
            )
            assertEquals(MotivationalMessages.forDay(4, 4, today), MotivationalMessages.forDay(4, 4, today))
        }
        assertEquals(
            MotivationalMessages.pick(MotivationalMessages.WATER_PHRASES, MotivationTrigger.WATER_GOAL, today),
            MotivationalMessages.pick(MotivationalMessages.WATER_PHRASES, MotivationTrigger.WATER_GOAL, today),
        )
    }

    @Test
    fun phrasesRotateAcrossDaysAndCoverEveryPhrase() {
        val phrases = MotivationalMessages.WATER_PHRASES
        val picked = (0 until phrases.size).map {
            MotivationalMessages.pick(phrases, MotivationTrigger.WATER_GOAL, today.plusDays(it.toLong()))
        }
        assertEquals("cinque giorni di fila = cinque frasi diverse", phrases.toSet(), picked.toSet())
        assertNotEquals(picked[0], picked[1])
        // Il ciclo si ripete dopo una rotazione completa.
        assertEquals(picked[0], MotivationalMessages.pick(phrases, MotivationTrigger.WATER_GOAL, today.plusDays(phrases.size.toLong())))
    }

    @Test
    fun pickWorksForDatesBeforeTheEpoch() {
        val phrases = MotivationalMessages.DAY_PHRASES
        val picked = MotivationalMessages.pick(phrases, MotivationTrigger.DAY_COMPLETED, LocalDate.of(1950, 1, 1))
        assertTrue(picked in phrases)
    }

    @Test
    fun everyPhraseListIsNonEmptyUniqueAndNaturalItalian() {
        val all = mapOf(
            "acqua" to MotivationalMessages.WATER_PHRASES,
            "giornata" to MotivationalMessages.DAY_PHRASES,
            "settimana" to MotivationalMessages.WEEK_PHRASES,
            "dimagrimento" to MotivationalMessages.LOSS_PHRASES,
            "stabile" to MotivationalMessages.STABLE_PHRASES,
            "vita" to MotivationalMessages.WAIST_PHRASES,
            "costanza" to MotivationalMessages.TRACKING_PHRASES,
        )
        all.forEach { (name, phrases) ->
            assertTrue("$name: servono più frasi per non ripetersi", phrases.size >= 4)
            assertEquals("$name: frasi duplicate", phrases.size, phrases.toSet().size)
            phrases.forEach {
                assertTrue("$name: «$it» deve finire con la punteggiatura", it.last() in ".!")
                assertTrue("$name: «$it» ha spazi di troppo", it == it.trim() && !it.contains("  "))
                val lower = it.lowercase()
                assertTrue("$name: «$it» è al maschile/femminile: meglio un testo neutro", !lower.contains("bravo") && !lower.contains("brava"))
            }
        }
    }
}
