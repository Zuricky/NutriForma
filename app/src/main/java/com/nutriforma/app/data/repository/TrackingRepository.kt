package com.nutriforma.app.data.repository

import com.nutriforma.app.data.local.dao.BodyMeasurementDao
import com.nutriforma.app.data.local.dao.WaterLogDao
import com.nutriforma.app.data.local.entity.WaterLogEntity
import com.nutriforma.app.data.mapper.toDomain
import com.nutriforma.app.data.mapper.toEntity
import com.nutriforma.app.domain.model.BodyMeasurement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/** Acqua e misure corporee. */
class TrackingRepository(
    private val waterDao: WaterLogDao,
    private val measurementDao: BodyMeasurementDao,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    /** Chiamata dopo ogni misura o registrazione d'acqua salvata o eliminata (es. backup automatico). */
    private val onDataChanged: () -> Unit = {},
) {
    private fun today(): LocalDate = LocalDate.now(clock)

    fun observeWaterToday(): Flow<Int> = waterDao.observeTotalForDay(today().toEpochDay())

    /** Aggiunge [amountMl] (1..1000) al totale di oggi. */
    suspend fun addWater(amountMl: Int) = withContext(ioDispatcher) {
        require(amountMl in 1..1000) { "Quantità d'acqua non valida" }
        waterDao.insert(WaterLogEntity(dateEpochDay = today().toEpochDay(), timestampMillis = clock.millis(), amountMl = amountMl))
        onDataChanged()
    }

    /**
     * Toglie [amountMl] dal totale di oggi senza scendere sotto zero.
     * DECISION: registra una correzione negativa invece di cancellare un log, così il tasto "−250 ml" funziona
     * anche quando l'ultimo log è di 500 ml. Restituisce i ml realmente tolti.
     */
    suspend fun subtractWater(amountMl: Int): Int = withContext(ioDispatcher) {
        require(amountMl in 1..1000) { "Quantità d'acqua non valida" }
        val day = today().toEpochDay()
        val removed = minOf(amountMl, waterDao.totalForDay(day))
        if (removed > 0) {
            waterDao.insert(WaterLogEntity(dateEpochDay = day, timestampMillis = clock.millis(), amountMl = -removed))
            onDataChanged()
        }
        removed
    }

    fun observeMeasurements(): Flow<List<BodyMeasurement>> =
        measurementDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Salva la misura del giorno. I campi `null` mantengono il valore già registrato quella data
     * (una riga per giorno). Serve almeno un valore.
     */
    suspend fun saveMeasurement(m: BodyMeasurement) = withContext(ioDispatcher) {
        require(m.weightKg != null || m.waistCm != null || m.thighCm != null) { "Inserisci almeno una misura." }
        require(!m.date.isAfter(today())) { "La data non può essere nel futuro." }
        val existing = measurementDao.observeAll().first().firstOrNull { it.dateEpochDay == m.date.toEpochDay() }
        val merged = m.copy(
            weightKg = m.weightKg ?: existing?.weightKg,
            waistCm = m.waistCm ?: existing?.waistCm,
            thighCm = m.thighCm ?: existing?.thighCm,
            note = m.note ?: existing?.note,
        )
        measurementDao.upsert(merged.toEntity(clock.millis()))
        onDataChanged()
    }

    suspend fun deleteMeasurement(date: LocalDate) = withContext(ioDispatcher) {
        measurementDao.delete(date.toEpochDay())
        onDataChanged()
    }
}
