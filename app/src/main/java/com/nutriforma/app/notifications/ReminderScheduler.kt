package com.nutriforma.app.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.ReminderSettings
import com.nutriforma.app.domain.reminders.nextTriggerMillis
import com.nutriforma.app.domain.reminders.nextWaterTriggerMillis
import com.nutriforma.app.domain.reminders.nextWeighInMillis
import java.time.Clock
import java.util.concurrent.TimeUnit

enum class ReminderType { MEAL, WATER, WEIGH_IN }

/**
 * Promemoria locali con WorkManager: ogni promemoria è un lavoro unico "one-time" che, dopo l'invio, si riprogramma
 * da solo per la ricorrenza successiva ([ReminderWorker]). L'orario non è al secondo (Doze può ritardarlo di poco).
 */
class ReminderScheduler(
    private val context: Context,
    private val profileRepository: UserProfileRepository,
    private val clock: Clock,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Allinea tutti i lavori alle impostazioni del profilo: abilitati → (ri)programmati, altri → annullati. */
    suspend fun syncWithProfile() {
        val reminders = profileRepository.current().reminders
        MealSlot.entries.forEach { schedule(ReminderType.MEAL, it, reminders) }
        schedule(ReminderType.WATER, null, reminders)
        schedule(ReminderType.WEIGH_IN, null, reminders)
    }

    /** Programma la prossima occorrenza (o annulla se il promemoria è spento). */
    fun schedule(type: ReminderType, slot: MealSlot?, reminders: ReminderSettings) {
        val name = uniqueName(type, slot)
        val enabled = when (type) {
            ReminderType.MEAL -> reminders.mealRemindersEnabled
            ReminderType.WATER -> reminders.waterRemindersEnabled
            ReminderType.WEIGH_IN -> reminders.weighInReminderEnabled
        }
        if (!enabled) {
            workManager.cancelUniqueWork(name)
            return
        }
        val now = clock.millis()
        val zone = clock.zone
        val triggerAt = when (type) {
            ReminderType.MEAL -> nextTriggerMillis(now, reminders.mealTimes[slot] ?: checkNotNull(slot).defaultTimeMinutes, zone)
            ReminderType.WATER -> nextWaterTriggerMillis(
                now, reminders.waterIntervalMinutes, reminders.waterStartMinutes, reminders.waterEndMinutes, zone,
            )
            ReminderType.WEIGH_IN -> nextWeighInMillis(now, reminders.weighInDayOfWeek, reminders.weighInMinutes, zone)
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay((triggerAt - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_TYPE, type.name)
                    .also { if (slot != null) it.putString(ReminderWorker.KEY_SLOT, slot.name) }
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    private fun uniqueName(type: ReminderType, slot: MealSlot?): String = when (type) {
        ReminderType.MEAL -> "reminder_meal_${checkNotNull(slot).name}"
        ReminderType.WATER -> "reminder_water"
        ReminderType.WEIGH_IN -> "reminder_weighin"
    }
}
