package com.nutriforma.app.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nutriforma.app.MainActivity
import com.nutriforma.app.NutriFormaApplication
import com.nutriforma.app.R
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.dayIndex
import com.nutriforma.app.domain.reminders.ReminderType
import com.nutriforma.app.domain.tips.DailyTips
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Mostra un promemoria (se consentito) e poi programma la ricorrenza successiva. Non fallisce mai: niente retry. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE)?.let { n -> ReminderType.entries.firstOrNull { it.name == n } }
            ?: return Result.success()
        val slot = inputData.getString(KEY_SLOT)?.let { n -> MealSlot.entries.firstOrNull { it.name == n } }
        val container = (applicationContext as NutriFormaApplication).container

        try {
            val reminders = container.userProfileRepository.current().reminders
            val content = when (type) {
                ReminderType.MEAL -> if (reminders.mealRemindersEnabled && slot != null) mealContent(slot) else null
                ReminderType.WATER -> if (reminders.waterRemindersEnabled) waterContent() else null
                ReminderType.TIP -> if (reminders.dailyTipEnabled) {
                    Content(
                        "Consiglio del giorno",
                        DailyTips.tipFor(LocalDate.now(container.clock)),
                        TIP_ID,
                        NotificationChannels.TIPS,
                    )
                } else {
                    null
                }
                ReminderType.WEIGH_IN -> if (reminders.weighInReminderEnabled) {
                    Content(
                        "Misurazioni settimanali",
                        "Registra peso, vita e cosce per seguire i tuoi progressi.",
                        WEIGH_IN_ID,
                        NotificationChannels.WEIGH_IN,
                    )
                } else {
                    null
                }
            }
            if (content != null && applicationContext.canPostNotifications()) post(content)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un promemoria mancato non deve interrompere la catena: si riprogramma comunque.
        }

        // Ultimo passo (REPLACE annulla questo stesso lavoro): riprogramma sempre, così il promemoria riparte
        // anche se il permesso viene concesso più tardi.
        container.reminderScheduler.schedule(type, slot, container.userProfileRepository.current().reminders)
        return Result.success()
    }

    private class Content(val title: String, val body: String, val id: Int, val channel: String)

    private suspend fun mealContent(slot: MealSlot): Content? {
        val container = (applicationContext as NutriFormaApplication).container
        val today = LocalDate.now(container.clock)
        val plan = container.planRepository.observeWeek(today).first()
        val meal = plan?.meals?.firstOrNull { it.dayIndex == today.dayIndex() && it.slot == slot }
        val body = when {
            meal?.type == MealType.SPUNTINO_SALTATO -> return null
            meal?.type == MealType.PRANZO_LIBERO -> "Oggi pasto libero, goditelo!"
            meal == null || meal.components.isEmpty() -> "È ora di ${slot.labelIt.lowercase()}."
            else -> {
                val foods = container.foodRepository.getFoods().associateBy { it.id }
                val names = meal.components.take(2).map { foods[it.foodId]?.nameIt ?: "Alimento non disponibile" }
                "È ora di: " + names.joinToString(" + ")
            }
        }
        return Content(slot.labelIt, body, MEAL_ID_BASE + slot.order, NotificationChannels.MEALS)
    }

    private suspend fun waterContent(): Content? {
        val container = (applicationContext as NutriFormaApplication).container
        val goal = container.userProfileRepository.current().waterGoalMl
        val total = container.trackingRepository.observeWaterToday().first()
        if (total >= goal) return null // obiettivo raggiunto: niente notifica
        return Content("Bevi un bicchiere d'acqua", "Oggi: $total ml su $goal ml", WATER_ID, NotificationChannels.WATER)
    }

    // Il permesso è verificato da canPostNotifications() prima della chiamata.
    @SuppressLint("MissingPermission")
    private fun post(content: Content) {
        val open = PendingIntent.getActivity(
            applicationContext, content.id,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, content.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(content.id, notification)
    }

    companion object {
        const val KEY_TYPE = "type"
        const val KEY_SLOT = "slot"
        private const val MEAL_ID_BASE = 1000
        private const val WATER_ID = 2000
        private const val WEIGH_IN_ID = 3000
        private const val TIP_ID = 4000
    }
}
