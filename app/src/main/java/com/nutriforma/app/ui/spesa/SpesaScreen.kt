package com.nutriforma.app.ui.spesa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriforma.app.R
import com.nutriforma.app.ui.food.FoodIconImage
import com.nutriforma.app.ui.RefreshTodayEffect
import com.nutriforma.app.data.repository.ShoppingExtrasRepository
import com.nutriforma.app.domain.model.DayNames

@Composable
fun SpesaScreen(viewModel: SpesaViewModel, modifier: Modifier = Modifier) {
    RefreshTodayEffect(viewModel::refreshToday)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbarHost.showSnackbar(it) } }

    SpesaScaffold(
        state = state,
        snackbarHost = snackbarHost,
        onWholeWeek = viewModel::setWholeWeek,
        onToggleDay = viewModel::toggleDay,
        onChecked = viewModel::setChecked,
        onAddExtra = viewModel::addExtra,
        onRemoveExtra = viewModel::removeExtra,
        onClear = viewModel::requestClear,
        onRetry = viewModel::ensurePlan,
        modifier = modifier,
    )

    if ((state as? SpesaUiState.Ready)?.confirmClear == true) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClear,
            title = { Text(stringResource(R.string.spesa_clear_dialog_title)) },
            text = { Text(stringResource(R.string.spesa_clear_dialog_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmClear) { Text(stringResource(R.string.spesa_clear_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissClear) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpesaScaffold(
    state: SpesaUiState,
    snackbarHost: SnackbarHostState,
    onWholeWeek: (Boolean) -> Unit,
    onToggleDay: (Int) -> Unit,
    onChecked: (String, Boolean) -> Unit,
    onAddExtra: (String) -> Unit,
    onRemoveExtra: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
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
                title = { Text(stringResource(R.string.spesa_title), fontWeight = FontWeight.Bold) },
                actions = {
                    if (state is SpesaUiState.Ready) {
                        TextButton(onClick = onClear) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                            Text(stringResource(R.string.spesa_clear_all), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                SpesaUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SpesaUiState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                is SpesaUiState.Ready -> SpesaList(state, onWholeWeek, onToggleDay, onChecked, onAddExtra, onRemoveExtra)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpesaList(
    state: SpesaUiState.Ready,
    onWholeWeek: (Boolean) -> Unit,
    onToggleDay: (Int) -> Unit,
    onChecked: (String, Boolean) -> Unit,
    onAddExtra: (String) -> Unit,
    onRemoveExtra: (String) -> Unit,
) {
    var purchasedOpen by rememberSaveable { mutableStateOf(false) }
    val content = state.content

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.wholeWeek,
                        onClick = { onWholeWeek(true) },
                        label = { Text(stringResource(R.string.spesa_scope_week)) },
                    )
                    FilterChip(
                        selected = !state.wholeWeek,
                        onClick = { onWholeWeek(false) },
                        label = { Text(stringResource(R.string.spesa_scope_days)) },
                    )
                }
                if (!state.wholeWeek) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayNames.short.forEachIndexed { index, name ->
                            FilterChip(
                                selected = index in state.selectedDays,
                                onClick = { onToggleDay(index) },
                                label = { Text(name) },
                            )
                        }
                    }
                    if (state.selectedDays.isEmpty()) {
                        Text(
                            stringResource(R.string.spesa_pick_days),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ExtraInput(onAddExtra)
            }
        }

        item {
            Text(
                stringResource(R.string.spesa_to_buy, content.toBuyCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            if (content.toBuy.isEmpty()) {
                Text(
                    stringResource(if (content.isEmpty) R.string.spesa_empty else R.string.spesa_all_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        sectionItems(content.toBuy, onChecked, onRemoveExtra)

        if (content.purchasedCount > 0) {
            item {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 16.dp)
                        .clickable(role = Role.Button) { purchasedOpen = !purchasedOpen },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.spesa_purchased, content.purchasedCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (purchasedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (purchasedOpen) R.string.spesa_purchased_collapse else R.string.spesa_purchased_expand,
                        ),
                    )
                }
            }
            if (purchasedOpen) sectionItems(content.purchased, onChecked, onRemoveExtra)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionItems(
    sections: List<SpesaSectionUi>,
    onChecked: (String, Boolean) -> Unit,
    onRemoveExtra: (String) -> Unit,
) {
    sections.forEach { section ->
        item(key = "h:${section.label}:${section.rows.firstOrNull()?.isChecked}") {
            Text(
                section.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        section.rows.forEach { row ->
            item(key = row.key) { SpesaRowView(row, onChecked, onRemoveExtra) }
        }
    }
}

@Composable
private fun SpesaRowView(row: SpesaRow, onChecked: (String, Boolean) -> Unit, onRemoveExtra: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { onChecked(row.key, !row.isChecked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.isChecked, onCheckedChange = null, modifier = Modifier.padding(end = 4.dp))
        FoodIconImage(row.icon, size = 24.dp, modifier = Modifier.padding(end = 12.dp))
        Text(
            row.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (row.isChecked) TextDecoration.LineThrough else null,
            color = if (row.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (row.isExtra) {
            IconButton(onClick = { onRemoveExtra(row.key) }) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.spesa_remove_extra, row.title))
            }
        }
    }
}

@Composable
private fun ExtraInput(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(text)
        text = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(ShoppingExtrasRepository.MAX_NAME_LENGTH) },
            label = { Text(stringResource(R.string.spesa_extra_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = submit, enabled = text.isNotBlank()) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.spesa_extra_add))
        }
    }
}
