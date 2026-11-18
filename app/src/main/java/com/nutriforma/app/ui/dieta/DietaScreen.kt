package com.nutriforma.app.ui.dieta

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriforma.app.R
import com.nutriforma.app.domain.model.ComplianceStatus
import com.nutriforma.app.ui.theme.ComplianceOk
import com.nutriforma.app.ui.theme.ComplianceViolation
import com.nutriforma.app.ui.theme.ComplianceWarningContainer
import com.nutriforma.app.ui.theme.ComplianceWarningText

@Composable
fun DietaScreen(viewModel: DietaViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val overlay by viewModel.overlayState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHost.showSnackbar(it) }
    }

    DietaScaffold(
        state = state,
        isBusy = overlay.isBusy,
        snackbarHost = snackbarHost,
        onGenerate = viewModel::requestRegenerate,
        onRetry = viewModel::ensurePlan,
        onSelectDay = viewModel::selectDay,
        onTurnoLungo = viewModel::setTurnoLungo,
        onPranzoLibero = viewModel::setPranzoLibero,
        onCompleted = viewModel::setCompleted,
        onChange = viewModel::openSwap,
        modifier = modifier,
    )

    if (overlay.confirmRegenerate) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRegenerate,
            title = { Text(stringResource(R.string.dieta_generate_dialog_title)) },
            text = { Text(stringResource(R.string.dieta_generate_dialog_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmRegenerate) { Text(stringResource(R.string.dieta_generate_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissRegenerate) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    }

    overlay.swap?.let { swap ->
        SwapBottomSheet(state = swap, onSelect = viewModel::applySwap, onDismiss = viewModel::closeSwap)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DietaScaffold(
    state: DietaUiState,
    isBusy: Boolean,
    snackbarHost: SnackbarHostState,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onTurnoLungo: (Int, Boolean) -> Unit,
    onPranzoLibero: (Long, Boolean) -> Unit,
    onCompleted: (Long, Boolean) -> Unit,
    onChange: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(stringResource(R.string.dieta_title), fontWeight = FontWeight.Bold)
                        val ready = state as? DietaUiState.Ready
                        if (ready != null) {
                            Text(
                                text = "${ready.content.dayName}: ${ready.content.dayKcalText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = onGenerate,
                        enabled = state is DietaUiState.Ready && !isBusy,
                        modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.dieta_generate_week), modifier = Modifier.padding(start = 6.dp))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                DietaUiState.Loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.dieta_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                is DietaUiState.Error -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                is DietaUiState.Ready -> DietaContentList(
                    content = state.content,
                    onSelectDay = onSelectDay,
                    onTurnoLungo = onTurnoLungo,
                    onPranzoLibero = onPranzoLibero,
                    onCompleted = onCompleted,
                    onChange = onChange,
                )
            }
            if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DietaContentList(
    content: DietaContent,
    onSelectDay: (Int) -> Unit,
    onTurnoLungo: (Int, Boolean) -> Unit,
    onPranzoLibero: (Long, Boolean) -> Unit,
    onCompleted: (Long, Boolean) -> Unit,
    onChange: (Long, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "rules") { RulesSummaryCard(content) }
        item(key = "days") { DaySelector(content, onSelectDay) }
        item(key = "controls") { DayControlsCard(content, onTurnoLungo, onPranzoLibero) }
        item(key = "totals") {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(
                    "${content.dayName} · ${content.dayKcalText}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${content.dayMacrosText}  ·  ${content.weekAverageText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(content.meals, key = { it.mealId }) { meal ->
            MealCard(
                meal = meal,
                onCompleted = { onCompleted(meal.mealId, it) },
                onChange = { onChange(meal.mealId, "${meal.title} · ${content.dayName}") },
            )
        }
    }
}

// ---- Riepilogo regole -----------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesSummaryCard(content: DietaContent) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val toggleLabel = stringResource(if (expanded) R.string.dieta_rules_collapse else R.string.dieta_rules_expand)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = toggleLabel) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.dieta_rules_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = toggleLabel,
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        content.pills.forEach { CompliancePill(it) }
                    }
                    if (content.hasViolations) {
                        Text(
                            stringResource(R.string.dieta_rules_violation),
                            style = MaterialTheme.typography.bodySmall,
                            color = ComplianceViolation,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompliancePill(pill: CompliancePillUi) {
    val (container, content) = when (pill.status) {
        ComplianceStatus.OK -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ComplianceStatus.ATTENZIONE -> ComplianceWarningContainer to ComplianceWarningText
        ComplianceStatus.VIOLAZIONE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    val dot = when (pill.status) {
        ComplianceStatus.OK -> ComplianceOk
        ComplianceStatus.ATTENZIONE -> ComplianceWarningText
        ComplianceStatus.VIOLAZIONE -> ComplianceViolation
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(pill.text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---- Selettore giorni -----------------------------------------------------------------------------------------

@Composable
private fun DaySelector(content: DietaContent, onSelectDay: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(content.selectedDay) { listState.animateScrollToItem((content.selectedDay - 1).coerceAtLeast(0)) }
    val description = stringResource(R.string.dieta_day_selector)
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(content.days, key = { it.dayIndex }) { day ->
            val selected = day.dayIndex == content.selectedDay
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (selected) null else BorderStroke(1.dp, if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .size(width = 60.dp, height = 68.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelectDay(day.dayIndex) }),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(day.shortName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(5.dp)
                            .background(if (day.isToday) (if (selected) Color.White else MaterialTheme.colorScheme.primary) else Color.Transparent, CircleShape),
                    )
                }
            }
        }
    }
}

// ---- Turno lungo / pasto libero -------------------------------------------------------------------------------

@Composable
private fun DayControlsCard(
    content: DietaContent,
    onTurnoLungo: (Int, Boolean) -> Unit,
    onPranzoLibero: (Long, Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            SwitchRow(
                title = stringResource(R.string.dieta_turno_lungo),
                subtitle = stringResource(R.string.dieta_turno_lungo_hint),
                checked = content.isTurnoLungo,
                enabled = true,
                onCheckedChange = { onTurnoLungo(content.selectedDay, it) },
            )
            SwitchRow(
                title = stringResource(R.string.dieta_pasto_libero),
                subtitle = content.pranzoLiberoHint,
                checked = content.isPranzoLibero,
                enabled = content.canTogglePranzoLibero && content.pranzoMealId != null,
                onCheckedChange = { content.pranzoMealId?.let { id -> onPranzoLibero(id, it) } },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

// ---- Meal card ------------------------------------------------------------------------------------------------

@Composable
private fun MealCard(meal: MealCardUi, onCompleted: (Boolean) -> Unit, onChange: () -> Unit) {
    val container = when {
        meal.isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        meal.isMerged -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(meal.timeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(meal.summary, style = MaterialTheme.typography.bodyLarge)

            if (meal.kcalBadge != null) {
                FlowBadges(listOf(meal.kcalBadge) + meal.macroBadges)
            }

            if (!meal.isMerged) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .toggleable(value = meal.isCompleted, role = Role.Checkbox, onValueChange = onCompleted),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = meal.isCompleted, onCheckedChange = null)
                        Text(
                            stringResource(R.string.dieta_completed),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (meal.canSwap) {
                        val changeDescription = stringResource(R.string.dieta_change_description, meal.title)
                        OutlinedButton(
                            onClick = onChange,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = changeDescription },
                        ) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.dieta_change), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowBadges(badges: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        badges.forEachIndexed { index, text ->
            val first = index == 0
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (first) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (first) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (first) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
