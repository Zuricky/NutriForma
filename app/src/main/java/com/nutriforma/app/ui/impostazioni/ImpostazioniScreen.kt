package com.nutriforma.app.ui.impostazioni

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriforma.app.R
import com.nutriforma.app.ui.SectionCard
import com.nutriforma.app.ui.promemoria.RemindersSection

/** Impostazioni dell'app: promemoria e notifiche, backup e ripristino, informazioni. Si apre dall'ingranaggio del Profilo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpostazioniScreen(viewModel: ImpostazioniViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbarHost.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        viewModel.exportBackup(it?.toString())
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.importPicked(it?.toString()) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        viewModel.chooseAutoBackupFolder(it?.toString())
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(stringResource(R.string.impostazioni_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
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
                item(key = "reminders") {
                    SectionCard(stringResource(R.string.impostazioni_reminders)) {
                        RemindersSection(state.reminders, viewModel::setReminder, viewModel::notifyPermissionDenied)
                    }
                }
                item(key = "backup") {
                    BackupCard(
                        state,
                        onExport = { exportLauncher.launch(viewModel.suggestedBackupName()) },
                        onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                        onChooseFolder = { folderLauncher.launch(null) },
                        onAutoEnabled = viewModel::setAutoBackupEnabled,
                    )
                }
                item(key = "info") {
                    SectionCard(stringResource(R.string.impostazioni_info)) {
                        val context = LocalContext.current
                        val versionName = remember(context) {
                            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                        }
                        if (versionName.isNotEmpty()) {
                            Text(
                                stringResource(R.string.impostazioni_version, versionName),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Text(stringResource(R.string.impostazioni_disclaimer), style = MaterialTheme.typography.bodyMedium)
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
private fun BackupCard(
    state: ImpostazioniUiState,
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
        state.backupStatus?.let {
            Text(
                it.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            state.lastBackupText?.let { stringResource(R.string.backup_last, it) } ?: stringResource(R.string.backup_never),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
