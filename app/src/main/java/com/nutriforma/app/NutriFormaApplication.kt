package com.nutriforma.app

import android.app.Application
import android.util.Log
import com.nutriforma.app.di.AppContainer
import com.nutriforma.app.di.DefaultAppContainer
import com.nutriforma.app.notifications.NotificationChannels
import kotlinx.coroutines.launch

/**
 * Application entry point. Owns the single manual-DI [AppContainer] (no Hilt/Dagger).
 *
 * Access from UI: `(LocalContext.current.applicationContext as NutriFormaApplication).container`
 * or from an Activity: `(application as NutriFormaApplication).container`.
 */
class NutriFormaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        container.applicationScope.launch {
            try {
                container.foodSeeder.seedIfNeeded()
            } catch (e: Exception) {
                Log.e("NutriForma", "Seed alimenti non riuscito", e)
            }
        }
        NotificationChannels.createAll(this)
        container.backupManager // avvia subito l'ascolto del backup automatico
        container.applicationScope.launch {
            try {
                container.reminderScheduler.syncWithProfile()
            } catch (e: Exception) {
                Log.e("NutriForma", "Sincronizzazione promemoria non riuscita", e)
            }
        }
    }
}
