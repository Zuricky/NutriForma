package com.nutriforma.app.ui.progressi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutriforma.app.domain.tracking.SeriesPoint
import com.nutriforma.app.domain.tracking.computeYRange
import com.nutriforma.app.domain.tracking.extremaIndexes
import com.nutriforma.app.domain.tracking.toPoints
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ITALY)

/** Grafico a linee disegnato a mano su Canvas (nessuna libreria esterna). */
@Composable
fun LineChart(
    series: List<SeriesPoint>,
    unit: String,
    lineColor: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val bg = MaterialTheme.colorScheme.surface
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 11.sp) }
    val valueStyle = remember(lineColor) { TextStyle(color = lineColor, fontSize = 11.sp) }
    val range = remember(series) { computeYRange(series.map { it.value }) }
    val points = remember(series, range) { toPoints(series, range) }
    val extrema = remember(series) { extremaIndexes(series) }

    Canvas(modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description }) {
        val leftPad = 44.dp.toPx()
        val rightPad = 20.dp.toPx()
        val topPad = 16.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val w = size.width - leftPad - rightPad
        val h = size.height - topPad - bottomPad
        fun x(p: Float) = leftPad + p * w
        fun y(p: Float) = topPad + p * h

        // 4 linee orizzontali con etichette
        for (i in 0..3) {
            val frac = i / 3f
            val gy = y(frac)
            drawLine(gridColor, Offset(leftPad, gy), Offset(leftPad + w, gy), strokeWidth = 1.dp.toPx())
            val value = range.max - frac * range.span
            val text = measurer.measure(String.format(Locale.ITALY, "%.1f", value), labelStyle)
            drawText(text, topLeft = Offset(leftPad - text.size.width - 6.dp.toPx(), gy - text.size.height / 2f))
        }

        // date: prima e ultima
        val first = measurer.measure(series.first().date.format(dateFormat), labelStyle)
        drawText(first, topLeft = Offset(leftPad, size.height - first.size.height))
        if (series.size > 1) {
            val last = measurer.measure(series.last().date.format(dateFormat), labelStyle)
            drawText(last, topLeft = Offset(leftPad + w - last.size.width + rightPad / 2f, size.height - last.size.height))
        }

        // linea
        val offsets = points.map { Offset(x(it.x), y(it.y)) }
        if (offsets.size > 1) {
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        offsets.forEach { drawCircle(lineColor, 4.dp.toPx(), it) }

        // marcatori minimo / massimo (anello + valore). Con un solo punto si etichetta quel punto.
        val marked = extrema?.let { listOf(it.first, it.second) } ?: if (series.size == 1) listOf(0) else emptyList()
        marked.forEach { i ->
            val c = offsets[i]
            drawCircle(bg, 7.dp.toPx(), c)
            drawCircle(lineColor, 7.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            drawCircle(lineColor, 3.dp.toPx(), c)
            val label = measurer.measure(String.format(Locale.ITALY, "%.1f %s", series[i].value, unit), valueStyle)
            val above = extrema == null || i == extrema.second
            val ty = if (above) c.y - label.size.height - 8.dp.toPx() else c.y + 8.dp.toPx()
            val tx = (c.x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(tx, ty.coerceIn(0f, size.height - label.size.height)))
        }
    }
}
