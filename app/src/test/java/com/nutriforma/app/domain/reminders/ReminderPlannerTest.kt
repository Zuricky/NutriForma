package com.nutriforma.app.domain.reminders

import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.ReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlannerTest {
    private val zone = ZoneId.of("Europe/Rome")

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // Martedì 17 novembre 2026, 07:00
    private val now = millis(2026, 11, 17, 7, 0)

    private val allOff = ReminderSettings(dailyTipEnabled = false)

    @Test
    fun tipIsScheduledAtTheConfiguredTimeEveryDay() {
        val plan = planReminder(ReminderType.TIP, null, ReminderSettings(dailyTipEnabled = true, dailyTipMinutes = 9 * 60), now, zone)
        assertEquals("reminder_tip", plan.uniqueName)
        assertTrue(plan.enabled)
        assertEquals(millis(2026, 11, 17, 9, 0), plan.triggerAtMillis)
        assertNull(plan.slot)
    }

    @Test
    fun tipAfterTodaysTimeMovesToTomorrow() {
        val late = millis(2026, 11, 17, 9, 30)
        val plan = planReminder(ReminderType.TIP, null, ReminderSettings(dailyTipMinutes = 540), late, zone)
        assertEquals(millis(2026, 11, 18, 9, 0), plan.triggerAtMillis)
        // esattamente all'ora: la ricorrenza successiva è domani
        val exact = planReminder(ReminderType.TIP, null, ReminderSettings(dailyTipMinutes = 540), millis(2026, 11, 17, 9, 0), zone)
        assertEquals(millis(2026, 11, 18, 9, 0), exact.triggerAtMillis)
    }

    @Test
    fun tipFollowsACustomTime() {
        val plan = planReminder(ReminderType.TIP, null, ReminderSettings(dailyTipMinutes = 20 * 60 + 15), now, zone)
        assertEquals(millis(2026, 11, 17, 20, 15), plan.triggerAtMillis)
    }

    @Test
    fun disabledTipIsCancelledNotScheduled() {
        val plan = planReminder(ReminderType.TIP, null, ReminderSettings(dailyTipEnabled = false), now, zone)
        assertFalse(plan.enabled)
        assertNull(plan.triggerAtMillis)
        assertEquals("reminder_tip", plan.uniqueName)
    }

    @Test
    fun otherTogglesDoNotAffectTheTip() {
        val s = ReminderSettings(
            mealRemindersEnabled = true, waterRemindersEnabled = true, weighInReminderEnabled = true, dailyTipEnabled = false,
        )
        assertNull(planReminder(ReminderType.TIP, null, s, now, zone).triggerAtMillis)
        val onlyTip = allOff.copy(dailyTipEnabled = true)
        assertTrue(planReminder(ReminderType.TIP, null, onlyTip, now, zone).enabled)
        assertFalse(planReminder(ReminderType.WATER, null, onlyTip, now, zone).enabled)
        assertFalse(planReminder(ReminderType.WEIGH_IN, null, onlyTip, now, zone).enabled)
        assertFalse(planReminder(ReminderType.MEAL, MealSlot.CENA, onlyTip, now, zone).enabled)
    }

    @Test
    fun planAllCoversEveryMealSlotWaterTipAndWeighIn() {
        val plans = planAllReminders(ReminderSettings(), now, zone)
        assertEquals(MealSlot.entries.size + 3, plans.size)
        assertEquals(plans.size, plans.map { it.uniqueName }.toSet().size)
        assertEquals(MealSlot.entries.toSet(), plans.filter { it.type == ReminderType.MEAL }.mapNotNull { it.slot }.toSet())
        assertEquals(1, plans.count { it.type == ReminderType.TIP })
        assertEquals(1, plans.count { it.type == ReminderType.WATER })
        assertEquals(1, plans.count { it.type == ReminderType.WEIGH_IN })
    }

    @Test
    fun defaultSettingsScheduleOnlyTheTip() {
        val enabled = planAllReminders(ReminderSettings(), now, zone).filter { it.enabled }
        assertEquals(listOf(ReminderType.TIP), enabled.map { it.type })
    }

    @Test
    fun everythingOffCancelsEverything() {
        assertTrue(planAllReminders(allOff, now, zone).none { it.enabled })
    }

    @Test
    fun everythingOnSchedulesEverythingInTheFuture() {
        val all = ReminderSettings(mealRemindersEnabled = true, waterRemindersEnabled = true, weighInReminderEnabled = true, dailyTipEnabled = true)
        val plans = planAllReminders(all, now, zone)
        assertTrue(plans.all { it.enabled })
        assertTrue(plans.all { it.triggerAtMillis!! > now })
    }

    @Test
    fun mealPlansUseThePerSlotTimes() {
        val s = ReminderSettings(mealRemindersEnabled = true, mealTimes = MealSlot.entries.associateWith { 600 })
        val plan = planReminder(ReminderType.MEAL, MealSlot.CENA, s, now, zone)
        assertEquals("reminder_meal_CENA", plan.uniqueName)
        assertEquals(MealSlot.CENA, plan.slot)
        assertEquals(millis(2026, 11, 17, 10, 0), plan.triggerAtMillis)
    }

    @Test
    fun mealSlotFallsBackToTheDefaultTime() {
        val s = ReminderSettings(mealRemindersEnabled = true, mealTimes = emptyMap())
        val plan = planReminder(ReminderType.MEAL, MealSlot.COLAZIONE, s, now, zone)
        assertEquals(nextTriggerMillis(now, MealSlot.COLAZIONE.defaultTimeMinutes, zone), plan.triggerAtMillis)
    }

    @Test
    fun waterAndWeighInUseTheirOwnTiming() {
        val s = ReminderSettings(waterRemindersEnabled = true, weighInReminderEnabled = true, weighInDayOfWeek = 1, weighInMinutes = 480)
        val water = planReminder(ReminderType.WATER, null, s, millis(2026, 11, 17, 9, 15), zone)
        assertEquals(millis(2026, 11, 17, 10, 0), water.triggerAtMillis)
        val weighIn = planReminder(ReminderType.WEIGH_IN, null, s, now, zone)
        assertEquals(millis(2026, 11, 23, 8, 0), weighIn.triggerAtMillis) // lunedì successivo
    }

    @Test
    fun mealRemindersNeedASlot() {
        assertThrows(IllegalArgumentException::class.java) { reminderUniqueName(ReminderType.MEAL, null) }
        assertThrows(IllegalArgumentException::class.java) {
            planReminder(ReminderType.MEAL, null, ReminderSettings(mealRemindersEnabled = true), now, zone)
        }
    }

    @Test
    fun nonMealPlansIgnoreAStraySlot() {
        val plan = planReminder(ReminderType.TIP, MealSlot.CENA, ReminderSettings(), now, zone)
        assertNull(plan.slot)
        assertEquals("reminder_tip", plan.uniqueName)
    }

    @Test
    fun rescheduleAfterFiringMovesToTheNextDay() {
        // Il worker si riprogramma subito dopo l'invio: l'istante appena scattato non deve ripetersi.
        val s = ReminderSettings(dailyTipEnabled = true, dailyTipMinutes = 540)
        val first = planReminder(ReminderType.TIP, null, s, now, zone).triggerAtMillis!!
        val second = planReminder(ReminderType.TIP, null, s, first, zone).triggerAtMillis!!
        assertEquals(millis(2026, 11, 18, 9, 0), second)
    }

    @Test
    fun dstChangeKeepsTheLocalClockTime() {
        // 25 ottobre 2026: fine ora legale in Italia. Il consiglio resta alle 9:00 locali.
        val before = millis(2026, 10, 24, 10, 0)
        val plan = planReminder(ReminderType.TIP, null, ReminderSettings(dailyTipMinutes = 540), before, zone)
        assertEquals(millis(2026, 10, 25, 9, 0), plan.triggerAtMillis)
    }
}
