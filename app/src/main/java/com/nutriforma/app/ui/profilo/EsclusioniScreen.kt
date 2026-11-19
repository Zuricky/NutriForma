package com.nutriforma.app.ui.profilo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriforma.app.R
import com.nutriforma.app.ui.food.FoodIconImage
import com.nutriforma.app.ui.theme.ComplianceWarningContainer
import com.nutriforma.app.ui.theme.ComplianceWarningText

/** "Non mi piace / Intolleranze": elenco con ricerca, raggruppato per categoria. Spuntato = escluso dalla dieta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EsclusioniScreen(viewModel: EsclusioniViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
                title = { Text(stringResource(R.string.esclusioni_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.esclusioni_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.esclusioni_clear_search))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.esclusioni_hint, state.excludedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.warnings) { WarningBox(it) }
                    if (state.groups.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.esclusioni_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    }
                    state.groups.forEach { group ->
                        item(key = "h:${group.label}") {
                            Text(
                                group.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                            )
                        }
                        items(group.rows, key = { it.id }) { row ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable(role = Role.Checkbox) { viewModel.toggle(row.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = row.excluded, onCheckedChange = null, modifier = Modifier.padding(end = 4.dp))
                                FoodIconImage(row.icon, size = 24.dp, modifier = Modifier.padding(end = 12.dp))
                                Text(
                                    row.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (row.excluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningBox(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ComplianceWarningContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = ComplianceWarningText)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = ComplianceWarningText, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
