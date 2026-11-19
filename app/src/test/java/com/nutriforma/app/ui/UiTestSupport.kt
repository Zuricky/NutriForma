package com.nutriforma.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sostituisce il dispatcher "Main" con uno immediato: i `viewModelScope` partono subito nel thread del test e le
 * riprese avvengono sui thread dei DataStore, quindi i test aspettano con [awaitValue] / [awaitUntil].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(Dispatchers.Unconfined)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

const val TEST_TIMEOUT_MS = 10_000L

/** Primo elemento del flusso che soddisfa [predicate], con tempo massimo. */
suspend fun <T> Flow<T>.awaitValue(predicate: (T) -> Boolean): T = withTimeout(TEST_TIMEOUT_MS) { first(predicate) }

/** Prossimo messaggio (snackbar) emesso dal ViewModel. */
suspend fun <T> Flow<T>.nextEvent(): T = withTimeout(TEST_TIMEOUT_MS) { first() }

/** Attende che [condition] diventi vera (per effetti che passano da thread di I/O). */
suspend fun awaitUntil(condition: suspend () -> Boolean) {
    withTimeout(TEST_TIMEOUT_MS) {
        while (!condition()) delay(10)
    }
}
