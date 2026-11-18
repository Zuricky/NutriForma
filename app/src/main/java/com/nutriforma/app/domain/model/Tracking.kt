package com.nutriforma.app.domain.model

import java.time.LocalDate

data class BodyMeasurement(
    val date: LocalDate,
    val weightKg: Double?,
    val waistCm: Double?,
    val thighCm: Double?,
    val note: String?,
)

data class WaterLog(val id: Long, val date: LocalDate, val timestampMillis: Long, val amountMl: Int)

data class DailyWaterTotal(val date: LocalDate, val totalMl: Int)
