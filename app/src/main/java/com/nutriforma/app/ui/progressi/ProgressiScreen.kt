package com.nutriforma.app.ui.progressi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriforma.app.R
import com.nutriforma.app.ui.MotivationBanner
import com.nutriforma.app.ui.RefreshTodayEffect
import com.nutriforma.app.domain.model.BodyMeasurement
import com.nutriforma.app.domain.tracking.MeasurementMetric
import com.nutriforma.app.domain.tracking.SeriesPoint
import com.nutriforma.app.domain.tracking.formatDelta
import com.nutriforma.app.domain.tracking.formatValue
import com.nutriforma.app.domain.tracking.latestDelta
import com.nutriforma.app.ui.theme.ComplianceOk
import com.nutriforma.app.ui.theme.ComplianceWarningContainer
import com.nutriforma.app.ui.theme.ComplianceWarningText
import com.nutriforma.app.ui.theme.EggplantPurple
import com.nutriforma.app.ui.theme.HerbGreen
import com.nutriforma.app.ui.theme.TomatoRed
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rowDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressiScreen(viewModel: ProgressiViewModel, modifier: Modifier = Modifier) {
    RefreshTodayEffect(viewModel::refreshToday)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbarHost.showSnackbar(it) } }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(stringResource(R.string.progressi_title), fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WaterCard(state, viewModel::addWater, viewModel::subtractWater) }
            state.waterCompliment?.let { message -> item(key = "water-compliment") { MotivationBanner(message) } }
            state.progressCompliment?.let { message -> item(key = "progress-compliment") { MotivationBanner(message) } }
            item { TipCard(state.tip) }
            item {
                Button(onClick = viewModel::openDialog, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.progressi_add_measures))
                }
            }
            items(MeasurementMetric.entries, key = { it.name }) { metric ->
                ChartCard(metric, state.series[metric].orEmpty())
            }
            item { RecentHeader(state.recent.isEmpty()) }
            items(state.recent, key = { it.date.toEpochDay() }) { m -> RecentRow(m, onDelete = { viewModel.deleteMeasurement(m.date) }) }
        }
    }

    if (state.dialogOpen) {
        MeasurementDialog(
            today = state.today,
            error = state.dialogError,
            onSave = viewModel::saveMeasurement,
            onDismiss = viewModel::closeDialog,
        )
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun WaterCard(state: ProgressiUiState, onAdd: (Int) -> Unit, onSubtract: (Int) -> Unit) {
    val goal = state.waterGoalMl.coerceAtLeast(1)
    val progress = (state.waterMl.toFloat() / goal).coerceIn(0f, 1f)
    val reached = state.waterMl >= goal
    SectionCard {
        Text(stringResource(R.string.progressi_water_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.progressi_water_amount, state.waterMl, formatLiters(goal)),
            style = MaterialTheme.typography.headlineSmall,
            color = if (reached) ComplianceOk else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        LinearProgressIndicator(
            progress = { progress },
            color = HerbGreen,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 10.dp),
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAdd(250) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.progressi_water_add_250)) }
            Button(onClick = { onAdd(500) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.progressi_water_add_500)) }
            OutlinedButton(
                onClick = { onSubtract(250) },
                enabled = state.waterMl > 0,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.progressi_water_sub_250)) }
        }
    }
}

/** 1700 → "1,7 L". */
private fun formatLiters(ml: Int): String = String.format(Locale.ITALY, "%.1f L", ml / 1000.0)

@Composable
private fun TipCard(tip: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.progressi_tip_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(tip, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ChartCard(metric: MeasurementMetric, series: List<SeriesPoint>) {
    val color = when (metric) {
        MeasurementMetric.PESO -> HerbGreen
        MeasurementMetric.VITA -> TomatoRed
        MeasurementMetric.COSCE -> EggplantPurple
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${metric.labelIt} (${metric.unit})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (series.isNotEmpty()) {
                    Text(
                        stringResource(R.string.progressi_latest, formatValue(series.last().value, metric.unit)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            latestDelta(series)?.let { DeltaBadge(it, metric.unit) }
        }
        if (series.isEmpty()) {
            Text(
                stringResource(R.string.progressi_chart_empty, metric.labelIt.lowercase()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LineChart(
                series = series,
                unit = metric.unit,
                lineColor = color,
                description = stringResource(
                    R.string.progressi_chart_description,
                    metric.labelIt.lowercase(),
                    pluralStringResource(R.plurals.measures_count, series.size, series.size),
                    formatValue(series.last().value, metric.unit),
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DeltaBadge(delta: Double, unit: String) {
    // Per peso/vita/cosce una diminuzione è il risultato atteso: verde se scende, ambra se sale.
    val down = delta < 0
    val flat = Math.round(delta * 10) == 0L
    Surface(
        shape = RoundedCornerShape(50),
        color = if (down || flat) MaterialTheme.colorScheme.primaryContainer else ComplianceWarningContainer,
    ) {
        Text(
            formatDelta(delta, unit),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (down || flat) MaterialTheme.colorScheme.onPrimaryContainer else ComplianceWarningText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun RecentHeader(empty: Boolean) {
    Column {
        Text(stringResource(R.string.progressi_recent_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (empty) {
            Text(
                stringResource(R.string.progressi_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentRow(m: BodyMeasurement, onDelete: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.date.format(rowDate), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                val parts = listOfNotNull(
                    m.weightKg?.let { stringResource(R.string.progressi_row_weight, formatValue(it, "kg")) },
                    m.waistCm?.let { stringResource(R.string.progressi_row_waist, formatValue(it, "cm")) },
                    m.thighCm?.let { stringResource(R.string.progressi_row_thigh, formatValue(it, "cm")) },
                )
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.progressi_delete_measure, m.date.format(rowDate)),
                    tint = TomatoRed,
                )
            }
        }
    }
}
