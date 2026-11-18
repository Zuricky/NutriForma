package com.nutriforma.app.domain.tracking

import com.nutriforma.app.domain.model.BodyMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartMathTest {

    private fun p(day: Int, v: Double) = SeriesPoint(LocalDate.of(2026, 11, day), v)

    @Test
    fun yRangeContainsAllValuesWithMargin() {
        val r = computeYRange(listOf(78.4, 77.0, 79.2))
        assertTrue(r.min < 77.0 && r.max > 79.2)
    }

    @Test
    fun yRangeOfSingleOrFlatSeriesIsNotDegenerate() {
        assertTrue(computeYRange(listOf(80.0)).span >= 2.0)
        assertTrue(computeYRange(listOf(80.0, 80.0)).span >= 2.0)
    }

    @Test
    fun pointsAreProportionalToDateAndValue() {
        val series = listOf(p(1, 80.0), p(11, 78.0), p(21, 76.0))
        val range = YRange(75.0, 81.0)
        val pts = toPoints(series, range)
        assertEquals(0f, pts[0].x, 1e-6f)
        assertEquals(0.5f, pts[1].x, 1e-6f)
        assertEquals(1f, pts[2].x, 1e-6f)
        assertTrue("valore più alto = y più piccola", pts[0].y < pts[2].y)
        assertEquals((1.0 - 5.0 / 6.0).toFloat(), pts[0].y, 1e-6f)
    }

    @Test
    fun singlePointIsCentered() {
        val pts = toPoints(listOf(p(1, 80.0)), YRange(79.0, 81.0))
        assertEquals(0.5f, pts.single().x, 1e-6f)
        assertEquals(0.5f, pts.single().y, 1e-6f)
    }

    @Test
    fun extremaAndDelta() {
        val s = listOf(p(1, 80.0), p(8, 77.5), p(15, 79.0))
        assertEquals(1 to 0, extremaIndexes(s))
        assertEquals(1.5, latestDelta(s)!!, 1e-9)
        assertNull(extremaIndexes(listOf(p(1, 80.0))))
        assertNull(extremaIndexes(listOf(p(1, 80.0), p(2, 80.0))))
        assertNull(latestDelta(listOf(p(1, 80.0))))
    }

    @Test
    fun formattingUsesItalianConventions() {
        assertEquals("78,4 kg", formatValue(78.4, "kg"))
        assertEquals("−1,6 kg", formatDelta(-1.6, "kg"))
        assertEquals("+0,4 cm", formatDelta(0.4, "cm"))
        assertEquals("0,0 cm", formatDelta(0.0, "cm"))
    }

    @Test
    fun seriesSkipsMissingValuesAndSortsByDate() {
        val ms = listOf(
            BodyMeasurement(LocalDate.of(2026, 11, 9), 79.0, null, 55.0, null),
            BodyMeasurement(LocalDate.of(2026, 11, 2), 80.0, 85.0, null, null),
        )
        assertEquals(listOf(80.0, 79.0), seriesOf(ms, MeasurementMetric.PESO).map { it.value })
        assertEquals(listOf(85.0), seriesOf(ms, MeasurementMetric.VITA).map { it.value })
        assertEquals(listOf(55.0), seriesOf(ms, MeasurementMetric.COSCE).map { it.value })
    }

    @Test
    fun inputParsingAcceptsCommaAndRejectsBadValues() {
        val ok = parseMeasurementInput("78,4", "", " 55.5 ") as MeasurementInputResult.Valid
        assertEquals(78.4, ok.weightKg!!, 1e-9)
        assertNull(ok.waistCm)
        assertEquals(55.5, ok.thighCm!!, 1e-9)
        assertTrue(parseMeasurementInput("", "", "") is MeasurementInputResult.Invalid)
        assertTrue(parseMeasurementInput("abc", "", "") is MeasurementInputResult.Invalid)
        assertTrue(parseMeasurementInput("10", "", "") is MeasurementInputResult.Invalid)
        assertTrue(parseMeasurementInput("", "300", "") is MeasurementInputResult.Invalid)
    }
}
