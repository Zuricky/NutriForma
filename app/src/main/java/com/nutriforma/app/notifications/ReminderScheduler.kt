package com.nutriforma.app.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.ReminderSettings
import com.nutriforma.app.domain.reminders.ReminderType
import com.nutriforma.app.domain.reminders.planAllReminders
import com.nutriforma.app.domain.reminders.planReminder
import java.time.Clock
import java.util.concurrent.TimeUnit

/** Allinea i promemoria programmati alle impostazioni correnti del profilo. */
interface ReminderSync {
    suspend fun syncWithProfile()
}

/**
 * Promemoria locali con WorkManager: ogni promemoria è un lavoro unico "one-time" che, dopo l'invio, si riprogramma
 * da solo per la ricorrenza successiva ([ReminderWorker]). L'orario non è al secondo (Doze può ritardarlo di poco).
 * Le regole su "quando" e "se" sono nel pianificatore puro (`planReminder`), qui resta solo l'accodamento.
 */
class ReminderScheduler(
    private val context: Context,
    private val profileRepository: UserProfileRepository,
    private val clock: Clock,
) : ReminderSync {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Allinea tutti i lavori alle impostazioni del profilo: abilitati → (ri)programmati, altri → annullati. */
    override suspend fun syncWithProfile() {
        val reminders = profileRepository.current().reminders
        planAllReminders(reminders, clock.millis(), clock.zone).forEach { plan ->
            if (plan.triggerAtMillis == null) {
                workManager.cancelUniqueWork(plan.uniqueName)
            } else {
                enqueue(plan.type, plan.slot, plan.uniqueName, plan.triggerAtMillis)
            }
        }
    }

    /** Programma la prossima occorrenza (o annulla se il promemoria è spento). */
    fun schedule(type: ReminderType, slot: MealSlot?, reminders: ReminderSettings) {
        val plan = planReminder(type, slot, reminders, clock.millis(), clock.zone)
        if (plan.triggerAtMillis == null) {
            workManager.cancelUniqueWork(plan.uniqueName)
        } else {
            enqueue(plan.type, plan.slot, plan.uniqueName, plan.triggerAtMillis)
        }
    }

    private fun enqueue(type: ReminderType, slot: MealSlot?, name: String, triggerAtMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay((triggerAtMillis - clock.millis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_TYPE, type.name)
                    .also { if (slot != null) it.putString(ReminderWorker.KEY_SLOT, slot.name) }
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }
}
