package com.nutriforma.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/**
 * Chiede alla schermata di rileggere la data di oggi quando torna in primo piano e subito dopo la mezzanotte
 * se resta aperta, così settimana e giorno corrente non restano fermi a ieri.
 */
@Composable
fun RefreshTodayEffect(onRefresh: () -> Unit) {
    val refresh by rememberUpdatedState(onRefresh)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalDateTime.now()
                val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
                delay(untilMidnight + MIDNIGHT_MARGIN_MS)
                refresh()
            }
        }
    }
}

private const val MIDNIGHT_MARGIN_MS = 1_000L
