package com.nutriforma.app.data.local

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Seam over `database.withTransaction { … }` so repositories stay testable on the plain JVM with hand-written DAO
 * fakes (no Robolectric/Mockito).
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(private val database: RoomDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
