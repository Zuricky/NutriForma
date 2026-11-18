package com.nutriforma.app.ui.profilo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriforma.app.R
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.ui.promemoria.RemindersSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfiloScreen(viewModel: ProfiloViewModel, onOpenEsclusioni: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbarHost.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        viewModel.exportBackup(it)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.importPicked(it) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        viewModel.chooseAutoBackupFolder(it)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(stringResource(R.string.profilo_title), fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { GoalCard(state, viewModel::setGoal) }
                item { SportCard(state, viewModel::setSportLevel) }
                item { PersonalCard(state, viewModel::setSesso, viewModel::setBirthYear, viewModel::setHeight) }
                item { FoodsCard(state, onOpenEsclusioni) }
                item { CheatCard(state, viewModel::setCheatMeals) }
                item {
                    SectionCard(stringResource(R.string.profilo_reminders)) {
                        RemindersSection(state.reminders, viewModel::setReminder, viewModel::notifyPermissionDenied)
                    }
                }
                item {
                    BackupCard(
                        state,
                        onExport = { exportLauncher.launch(viewModel.suggestedBackupName()) },
                        onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                        onChooseFolder = { folderLauncher.launch(null) },
                        onAutoEnabled = viewModel::setAutoBackupEnabled,
                    )
                }
                item {
                    SectionCard(stringResource(R.string.profilo_info)) {
                        Text(stringResource(R.string.profilo_disclaimer), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    state.pendingRestore?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_body, summary.exportedAtText, summary.plans, summary.measurements)) },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text(stringResource(R.string.backup_restore_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(Modifier.padding(top = 8.dp)) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(state: ProfiloUiState, onGoal: (Goal) -> Unit) {
    SectionCard(stringResource(R.string.profilo_goal)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.entries.forEach { goal ->
                FilterChip(selected = state.goal == goal, onClick = { onGoal(goal) }, label = { Text(goal.labelIt) })
            }
        }
        Text(
            stringResource(R.string.profilo_goal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SportCard(state: ProfiloUiState, onLevel: (SportLevel) -> Unit) {
    SectionCard(stringResource(R.string.profilo_sport)) {
        SportLevel.entries.forEach { level ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(role = Role.RadioButton) { onLevel(level) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.sportLevel == level, onClick = null, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(level.labelIt, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        portionScaleText(level),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val target = state.energyTarget
        Text(
            if (target != null) {
                stringResource(R.string.profilo_kcal_target, formatKcal(target.kcal), target.proteinG, target.carbsG, target.fatG)
            } else {
                stringResource(R.string.profilo_kcal_missing)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (target != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (target != null) FontWeight.Bold else null,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalCard(state: ProfiloUiState, onSesso: (Sesso) -> Unit, onBirthYear: (String) -> Unit, onHeight: (String) -> Unit) {
    SectionCard(stringResource(R.string.profilo_personal)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sesso.entries.forEach { s ->
                FilterChip(selected = state.sesso == s, onClick = { onSesso(s) }, label = { Text(s.labelIt) })
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(state.birthYear, stringResource(R.string.profilo_birth_year), onBirthYear, Modifier.weight(1f))
            NumberField(state.heightCm, stringResource(R.string.profilo_height), onHeight, Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.profilo_personal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun NumberField(value: Int?, label: String, onCommit: (String) -> Unit, modifier: Modifier) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit).take(4) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = modifier,
    )
}

@Composable
private fun FoodsCard(state: ProfiloUiState, onOpen: () -> Unit) {
    SectionCard(stringResource(R.string.profilo_foods)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.profilo_dislikes), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (state.excludedCount == 0) stringResource(R.string.profilo_dislikes_none)
                    else stringResource(R.string.profilo_dislikes_count, state.excludedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun CheatCard(state: ProfiloUiState, onChange: (Int) -> Unit) {
    SectionCard(stringResource(R.string.profilo_cheat)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2).forEach { n ->
                FilterChip(
                    selected = state.cheatMealsPerWeek == n,
                    onClick = { onChange(n) },
                    label = { Text(if (n == 1) "1 a settimana" else "2 a settimana") },
                )
            }
        }
        Text(
            stringResource(R.string.profilo_cheat_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun BackupCard(
    state: ProfiloUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onChooseFolder: () -> Unit,
    onAutoEnabled: (Boolean) -> Unit,
) {
    SectionCard(stringResource(R.string.backup_title)) {
        Text(
            stringResource(R.string.backup_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onExport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_export))
        }
        OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_restore))
        }
        OutlinedButton(onClick = onChooseFolder, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_choose_folder))
        }
        Text(
            state.backupFolderName?.let { stringResource(R.string.backup_folder, it) } ?: stringResource(R.string.backup_no_folder),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.backup_auto), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.backup_auto_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.autoBackupEnabled, onCheckedChange = onAutoEnabled, enabled = state.backupFolderName != null && !state.busy)
        }
        state.autoBackupError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Text(
            state.lastBackupText?.let { stringResource(R.string.backup_last, it) } ?: stringResource(R.string.backup_never),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
