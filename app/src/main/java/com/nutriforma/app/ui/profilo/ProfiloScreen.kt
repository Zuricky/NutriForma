package com.nutriforma.app.ui.profilo

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriforma.app.R
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.nutrition.BmiCategory
import com.nutriforma.app.domain.nutrition.BmiResult
import com.nutriforma.app.ui.SectionCard
import com.nutriforma.app.ui.theme.ComplianceWarningContainer
import com.nutriforma.app.ui.theme.ComplianceWarningText
import com.nutriforma.app.ui.theme.EggplantPurpleContainer
import com.nutriforma.app.ui.theme.HerbGreenContainer
import com.nutriforma.app.ui.theme.OnEggplantPurpleContainer
import com.nutriforma.app.ui.theme.OnHerbGreenContainer
import com.nutriforma.app.ui.theme.OnTomatoRedContainer
import com.nutriforma.app.ui.theme.TomatoRedContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfiloScreen(
    viewModel: ProfiloViewModel,
    onOpenEsclusioni: () -> Unit,
    onOpenImpostazioni: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                title = { Text(stringResource(R.string.profilo_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenImpostazioni, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.impostazioni_open))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "personal") {
                PersonalCard(
                    state = state,
                    onEdit = viewModel::startEdit,
                    onCancel = viewModel::cancelEdit,
                    onSave = viewModel::savePersonal,
                    onFirstName = viewModel::onFirstNameChange,
                    onLastName = viewModel::onLastNameChange,
                    onSesso = viewModel::onSessoChange,
                    onBirthYear = viewModel::onBirthYearChange,
                    onHeight = viewModel::onHeightChange,
                    onWeight = viewModel::onWeightChange,
                )
            }
            item(key = "bmi") { BmiCard(state.bmi) }
            item(key = "goal") { GoalCard(state, viewModel::setGoal) }
            item(key = "sport") { SportCard(state, viewModel::setSportLevel) }
            item(key = "foods") { FoodsCard(state, onOpenEsclusioni) }
            item(key = "cheat") { CheatCard(state, viewModel::setCheatMeals) }
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

// ---- Dati personali: sola lettura oppure modifica ----------------------------------------------------------------

@Composable
private fun PersonalCard(
    state: ProfiloUiState,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onFirstName: (String) -> Unit,
    onLastName: (String) -> Unit,
    onSesso: (Sesso) -> Unit,
    onBirthYear: (String) -> Unit,
    onHeight: (String) -> Unit,
    onWeight: (String) -> Unit,
) {
    SectionCard(stringResource(R.string.profilo_personal)) {
        if (state.isEditing) {
            PersonalEditContent(state, onCancel, onSave, onFirstName, onLastName, onSesso, onBirthYear, onHeight, onWeight)
        } else {
            PersonalReadContent(state, onEdit)
        }
    }
}

@Composable
private fun PersonalReadContent(state: ProfiloUiState, onEdit: () -> Unit) {
    val notSet = stringResource(R.string.profilo_not_set)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadRow(stringResource(R.string.profilo_first_name), state.firstName.ifBlank { notSet })
        ReadRow(stringResource(R.string.profilo_last_name), state.lastName.ifBlank { notSet })
        ReadRow(stringResource(R.string.profilo_sex), state.sesso?.labelIt ?: notSet)
        ReadRow(
            stringResource(R.string.profilo_birth_year),
            state.birthYear?.let { year ->
                state.ageYears?.let { age -> stringResource(R.string.profilo_age_value, year, age) } ?: year.toString()
            } ?: notSet,
        )
        ReadRow(stringResource(R.string.profilo_height), state.heightCm?.toString() ?: notSet)
        ReadRow(stringResource(R.string.profilo_weight), state.weightKg?.let(::formatWeightInput) ?: notSet)
    }
    val editDescription = stringResource(R.string.profilo_edit_description)
    OutlinedButton(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).semantics { contentDescription = editDescription },
    ) {
        Text(stringResource(R.string.profilo_edit))
    }
}

@Composable
private fun ReadRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalEditContent(
    state: ProfiloUiState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onFirstName: (String) -> Unit,
    onLastName: (String) -> Unit,
    onSesso: (Sesso) -> Unit,
    onBirthYear: (String) -> Unit,
    onHeight: (String) -> Unit,
    onWeight: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NameField(state.firstNameText, stringResource(R.string.profilo_first_name), onFirstName, Modifier.weight(1f))
            NameField(state.lastNameText, stringResource(R.string.profilo_last_name), onLastName, Modifier.weight(1f))
        }
        Text(stringResource(R.string.profilo_sex), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sesso.entries.forEach { s ->
                FilterChip(selected = state.sesso == s, onClick = { onSesso(s) }, label = { Text(s.labelIt) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(state.birthYearText, stringResource(R.string.profilo_birth_year), state.birthYearError, KeyboardType.Number, onBirthYear, Modifier.weight(1f))
            NumberField(state.heightText, stringResource(R.string.profilo_height), state.heightError, KeyboardType.Number, onHeight, Modifier.weight(1f))
        }
        NumberField(state.weightText, stringResource(R.string.profilo_weight), state.weightError, KeyboardType.Decimal, onWeight, Modifier.fillMaxWidth())
    }
    Text(
        stringResource(R.string.profilo_personal_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (state.personalDirty) stringResource(R.string.profilo_personal_unsaved) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
        // Senza dati salvati non c'è nulla a cui tornare: resta solo "Salva".
        if (state.hasSavedPersonalData) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel)) }
        }
        Button(onClick = onSave, enabled = state.personalDirty, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun NameField(text: String, label: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

@Composable
private fun NumberField(
    text: String,
    label: String,
    error: String?,
    keyboardType: KeyboardType,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        modifier = modifier,
    )
}

// ---- IMC ---------------------------------------------------------------------------------------------------------

@Composable
private fun BmiCard(bmi: BmiResult?) {
    SectionCard(stringResource(R.string.profilo_bmi_title)) {
        if (bmi == null) {
            Text(
                stringResource(R.string.profilo_bmi_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val (container, content) = bmiColors(bmi.category)
            val value = formatBmi(bmi.value)
            val description = stringResource(R.string.profilo_bmi_description, value, bmi.category.labelIt)
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = content)
                Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
                    Text(
                        bmi.category.labelIt,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.profilo_bmi_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Palette mediterranea: verde = normopeso, viola = sottopeso, ambra = sovrappeso, pomodoro = obesità. */
private fun bmiColors(category: BmiCategory): Pair<Color, Color> = when (category) {
    BmiCategory.SOTTOPESO -> EggplantPurpleContainer to OnEggplantPurpleContainer
    BmiCategory.NORMOPESO -> HerbGreenContainer to OnHerbGreenContainer
    BmiCategory.SOVRAPPESO -> ComplianceWarningContainer to ComplianceWarningText
    BmiCategory.OBESITA -> TomatoRedContainer to OnTomatoRedContainer
}

// ---- Alimenti / pasti liberi -------------------------------------------------------------------------------------

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
            listOf(1 to R.string.profilo_cheat_one, 2 to R.string.profilo_cheat_two).forEach { (n, label) ->
                FilterChip(
                    selected = state.cheatMealsPerWeek == n,
                    onClick = { onChange(n) },
                    label = { Text(stringResource(label)) },
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
