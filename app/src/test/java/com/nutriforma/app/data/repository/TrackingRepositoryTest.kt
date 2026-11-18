package com.nutriforma.app.data.repository

import com.nutriforma.app.data.FakeBodyMeasurementDao
import com.nutriforma.app.data.FakeWaterLogDao
import com.nutriforma.app.domain.model.BodyMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TrackingRepositoryTest {
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC)
    private val water = FakeWaterLogDao()
    private val measurements = FakeBodyMeasurementDao()
    private val repo = TrackingRepository(water, measurements, clock, Dispatchers.Unconfined)

    @Test
    fun waterAddAndSubtractNeverGoBelowZero() = runTest {
        repo.addWater(250)
        repo.addWater(500)
        assertEquals(750, repo.observeWaterToday().first())
        assertEquals(250, repo.subtractWater(250))
        assertEquals(500, repo.observeWaterToday().first())
        repo.subtractWater(250)
        repo.subtractWater(250)
        assertEquals(0, repo.subtractWater(250))
        assertEquals(0, repo.observeWaterToday().first())
    }

    @Test
    fun measurementsMergeSameDay() = runTest {
        repo.saveMeasurement(BodyMeasurement(today, 78.0, null, null, null))
        repo.saveMeasurement(BodyMeasurement(today, null, 84.0, null, null))
        val saved = repo.observeMeasurements().first().single()
        assertEquals(78.0, saved.weightKg!!, 0.0)
        assertEquals(84.0, saved.waistCm!!, 0.0)
        assertNull(saved.thighCm)

        repo.deleteMeasurement(today)
        assertEquals(0, repo.observeMeasurements().first().size)
    }

    @Test
    fun invalidMeasurementsAreRejected() = runTest {
        val empty = runCatching { repo.saveMeasurement(BodyMeasurement(today, null, null, null, null)) }
        assertTrue(empty.exceptionOrNull() is IllegalArgumentException)
        val future = runCatching { repo.saveMeasurement(BodyMeasurement(today.plusDays(1), 70.0, null, null, null)) }
        assertTrue(future.exceptionOrNull() is IllegalArgumentException)
    }
}
