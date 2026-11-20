package com.nutriforma.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nutriforma.app.R

object NotificationChannels {
    const val MEALS = "promemoria_pasti"
    const val WATER = "promemoria_acqua"
    const val WEIGH_IN = "promemoria_peso"
    const val TIPS = "promemoria_consigli"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(MEALS, context.getString(R.string.channel_meals), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(WATER, context.getString(R.string.channel_water), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(WEIGH_IN, context.getString(R.string.channel_weigh_in), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(TIPS, context.getString(R.string.channel_tips), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }
}

/** `true` se l'app può pubblicare notifiche (permesso su Android 13+ e notifiche non bloccate dall'utente). */
fun Context.canPostNotifications(): Boolean = hasNotificationPermission() && NotificationManagerCompat.from(this).areNotificationsEnabled()

/** Solo il permesso runtime `POST_NOTIFICATIONS`: sempre concesso prima di Android 13. */
fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

val needsNotificationPermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
