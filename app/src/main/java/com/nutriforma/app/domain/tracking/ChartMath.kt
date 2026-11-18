package com.nutriforma.app.domain.tracking

import com.nutriforma.app.domain.model.BodyMeasurement
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Un punto di una serie (una misura in una data). */
data class SeriesPoint(val date: LocalDate, val value: Double)

/** Intervallo verticale del grafico (già con un po' di margine). */
data class YRange(val min: Double, val max: Double) {
    val span: Double get() = max - min
}

/** Punto normalizzato 0..1 (x da sinistra, y dall'alto verso il basso). */
data class UnitPoint(val x: Float, val y: Float)

enum class MeasurementMetric(val labelIt: String, val unit: String, val min: Double, val max: Double) {
    PESO("Peso", "kg", 30.0, 250.0),
    VITA("Vita", "cm", 40.0, 200.0),
    COSCE("Cosce", "cm", 20.0, 120.0),
    ;

    fun valueOf(m: BodyMeasurement): Double? = when (this) {
        PESO -> m.weightKg
        VITA -> m.waistCm
        COSCE -> m.thighCm
    }
}

/** Serie ordinata per data della metrica scelta (ignora le misure senza quel valore). */
fun seriesOf(measurements: List<BodyMeasurement>, metric: MeasurementMetric): List<SeriesPoint> =
    measurements.mapNotNull { m -> metric.valueOf(m)?.let { SeriesPoint(m.date, it) } }.sortedBy { it.date }

/**
 * Intervallo Y "pulito": margine del 15% e estremi arrotondati. Se tutti i valori sono uguali (o c'è un solo valore)
 * si allarga di ±1 così la linea non è schiacciata.
 */
fun computeYRange(values: List<Double>): YRange {
    require(values.isNotEmpty()) { "Serve almeno un valore" }
    val lo = values.min()
    val hi = values.max()
    val span = hi - lo
    val pad = if (span < 1e-9) 1.0 else span * 0.15
    val step = if (span + 2 * pad > 10) 1.0 else 0.5
    val min = floor((lo - pad) / step) * step
    val max = ceil((hi + pad) / step) * step
    return YRange(min, max)
}

/** Posizioni normalizzate: x proporzionale alla data (un solo punto → al centro), y secondo [range]. */
fun toPoints(series: List<SeriesPoint>, range: YRange): List<UnitPoint> {
    if (series.isEmpty()) return emptyList()
    val first = series.first().date.toEpochDay()
    val last = series.last().date.toEpochDay()
    val xSpan = (last - first).toFloat()
    return series.map { p ->
        val x = if (xSpan <= 0f) 0.5f else (p.date.toEpochDay() - first) / xSpan
        val y = if (range.span <= 0.0) 0.5f else (1.0 - (p.value - range.min) / range.span).toFloat()
        UnitPoint(x, y)
    }
}

/** Indici del valore minimo e massimo; `null` se la serie ha meno di 2 punti o i valori coincidono. */
fun extremaIndexes(series: List<SeriesPoint>): Pair<Int, Int>? {
    if (series.size < 2) return null
    val minI = series.indices.minBy { series[it].value }
    val maxI = series.indices.maxBy { series[it].value }
    return if (series[minI].value == series[maxI].value) null else minI to maxI
}

/** Differenza tra l'ultima misura e la precedente; `null` con meno di 2 punti. */
fun latestDelta(series: List<SeriesPoint>): Double? =
    if (series.size < 2) null else series.last().value - series[series.size - 2].value

/** "78,4 kg" (virgola decimale). */
fun formatValue(value: Double, unit: String): String = String.format(Locale.ITALY, "%.1f %s", value, unit)

/** "−1,6 kg" / "+0,4 kg" / "0,0 kg" (meno tipografico U+2212). */
fun formatDelta(delta: Double, unit: String): String {
    val rounded = Math.round(delta * 10) / 10.0
    val sign = when {
        rounded > 0 -> "+"
        rounded < 0 -> "−"
        else -> ""
    }
    return sign + String.format(Locale.ITALY, "%.1f %s", abs(rounded), unit)
}

/** Esito della validazione dei campi del dialog "Registra misure". */
sealed interface MeasurementInputResult {
    data class Valid(val weightKg: Double?, val waistCm: Double?, val thighCm: Double?) : MeasurementInputResult
    data class Invalid(val message: String) : MeasurementInputResult
}

/** Valida i testi digitati (virgola o punto decimali); almeno un campo è obbligatorio. */
fun parseMeasurementInput(weight: String, waist: String, thigh: String): MeasurementInputResult {
    val values = mutableListOf<Double?>()
    for (metric in MeasurementMetric.entries) {
        val text = when (metric) {
            MeasurementMetric.PESO -> weight
            MeasurementMetric.VITA -> waist
            MeasurementMetric.COSCE -> thigh
        }.trim().replace(',', '.')
        if (text.isEmpty()) {
            values += null
            continue
        }
        val number = text.toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: return MeasurementInputResult.Invalid("${metric.labelIt}: valore non valido.")
        if (number < metric.min || number > metric.max) {
            return MeasurementInputResult.Invalid(
                "${metric.labelIt}: inserisci un valore tra ${metric.min.toInt()} e ${metric.max.toInt()} ${metric.unit}.",
            )
        }
        values += number
    }
    if (values.all { it == null }) return MeasurementInputResult.Invalid("Inserisci almeno una misura.")
    return MeasurementInputResult.Valid(values[0], values[1], values[2])
}
