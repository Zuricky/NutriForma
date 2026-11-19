package com.nutriforma.app.domain.reminders

import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.ReminderSettings
import java.time.ZoneId

/** Tipi di promemoria locale. */
enum class ReminderType { MEAL, WATER, TIP, WEIGH_IN }

/**
 * Esito della pianificazione di un singolo promemoria: [triggerAtMillis] è `null` quando il promemoria è spento
 * (il lavoro con nome [uniqueName] va annullato), altrimenti è il prossimo istante di invio.
 */
data class ReminderPlan(
    val type: ReminderType,
    val slot: MealSlot?,
    val uniqueName: String,
    val triggerAtMillis: Long?,
) {
    val enabled: Boolean get() = triggerAtMillis != null
}

fun reminderUniqueName(type: ReminderType, slot: MealSlot?): String = when (type) {
    ReminderType.MEAL -> "reminder_meal_${requireNotNull(slot) { "Serve lo slot per i promemoria dei pasti" }.name}"
    ReminderType.WATER -> "reminder_water"
    ReminderType.TIP -> "reminder_tip"
    ReminderType.WEIGH_IN -> "reminder_weighin"
}

/** Pianifica un solo promemoria a partire dalle impostazioni e dall'istante [nowMillis]. */
fun planReminder(
    type: ReminderType,
    slot: MealSlot?,
    reminders: ReminderSettings,
    nowMillis: Long,
    zone: ZoneId,
): ReminderPlan {
    val enabled = when (type) {
        ReminderType.MEAL -> reminders.mealRemindersEnabled
        ReminderType.WATER -> reminders.waterRemindersEnabled
        ReminderType.TIP -> reminders.dailyTipEnabled
        ReminderType.WEIGH_IN -> reminders.weighInReminderEnabled
    }
    val trigger = if (!enabled) {
        null
    } else {
        when (type) {
            ReminderType.MEAL -> {
                val mealSlot = requireNotNull(slot) { "Serve lo slot per i promemoria dei pasti" }
                nextTriggerMillis(nowMillis, reminders.mealTimes[mealSlot] ?: mealSlot.defaultTimeMinutes, zone)
            }
            ReminderType.WATER -> nextWaterTriggerMillis(
                nowMillis, reminders.waterIntervalMinutes, reminders.waterStartMinutes, reminders.waterEndMinutes, zone,
            )
            ReminderType.TIP -> nextTriggerMillis(nowMillis, reminders.dailyTipMinutes, zone)
            ReminderType.WEIGH_IN -> nextWeighInMillis(nowMillis, reminders.weighInDayOfWeek, reminders.weighInMinutes, zone)
        }
    }
    return ReminderPlan(type, if (type == ReminderType.MEAL) slot else null, reminderUniqueName(type, slot), trigger)
}

/** Pianifica tutti i promemoria: un lavoro per slot dei pasti, più acqua, consiglio del giorno e pesata. */
fun planAllReminders(reminders: ReminderSettings, nowMillis: Long, zone: ZoneId): List<ReminderPlan> =
    MealSlot.entries.map { planReminder(ReminderType.MEAL, it, reminders, nowMillis, zone) } +
        planReminder(ReminderType.WATER, null, reminders, nowMillis, zone) +
        planReminder(ReminderType.TIP, null, reminders, nowMillis, zone) +
        planReminder(ReminderType.WEIGH_IN, null, reminders, nowMillis, zone)
