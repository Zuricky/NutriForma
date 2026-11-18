package com.nutriforma.app.data

import com.nutriforma.app.data.local.dao.BodyMeasurementDao
import com.nutriforma.app.data.local.dao.DailyWaterTotalRow
import com.nutriforma.app.data.local.dao.WaterLogDao
import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import com.nutriforma.app.data.local.entity.WaterLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWaterLogDao : WaterLogDao {
    val logs = MutableStateFlow<List<WaterLogEntity>>(emptyList())
    private var next = 1L
    override suspend fun insert(log: WaterLogEntity): Long {
        val id = if (log.id != 0L) log.id else next++
        if (id >= next) next = id + 1
        logs.value = logs.value + log.copy(id = id)
        return id
    }
    override suspend fun deleteById(id: Long) { logs.value = logs.value.filterNot { it.id == id } }
    override fun observeForDay(epochDay: Long): Flow<List<WaterLogEntity>> = logs.map { l -> l.filter { it.dateEpochDay == epochDay } }
    override fun observeTotalForDay(epochDay: Long): Flow<Int> = logs.map { l -> l.filter { it.dateEpochDay == epochDay }.sumOf { it.amountMl } }
    override suspend fun totalForDay(epochDay: Long) = logs.value.filter { it.dateEpochDay == epochDay }.sumOf { it.amountMl }
    override fun observeDailyTotals(fromEpochDay: Long): Flow<List<DailyWaterTotalRow>> = logs.map { emptyList() }
    override suspend fun getAll() = logs.value
    override suspend fun insertAll(items: List<WaterLogEntity>) { items.forEach { insert(it) } }
    override suspend fun deleteAll() { logs.value = emptyList() }
}

class FakeBodyMeasurementDao : BodyMeasurementDao {
    val rows = MutableStateFlow<List<BodyMeasurementEntity>>(emptyList())
    override suspend fun upsert(m: BodyMeasurementEntity) { rows.value = rows.value.filterNot { it.dateEpochDay == m.dateEpochDay } + m }
    override suspend fun delete(epochDay: Long) { rows.value = rows.value.filterNot { it.dateEpochDay == epochDay } }
    override fun observeAll(): Flow<List<BodyMeasurementEntity>> = rows.map { r -> r.sortedBy { it.dateEpochDay } }
    override fun observeLatestWeight(): Flow<BodyMeasurementEntity?> =
        rows.map { r -> r.filter { it.weightKg != null }.maxByOrNull { it.dateEpochDay } }
    override suspend fun getAll() = rows.value
    override suspend fun insertAll(items: List<BodyMeasurementEntity>) { items.forEach { upsert(it) } }
    override suspend fun deleteAll() { rows.value = emptyList() }
}
