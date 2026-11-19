package com.nutriforma.app.ui.promemoria

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutriforma.app.R
import com.nutriforma.app.domain.model.ReminderSettings
import com.nutriforma.app.notifications.hasNotificationPermission
import com.nutriforma.app.notifications.needsNotificationPermission

enum class ReminderKind { MEALS, WATER, TIP, WEIGH_IN }

/**
 * Interruttori dei promemoria. Su Android 13+ accendere un promemoria chiede prima il permesso `POST_NOTIFICATIONS`:
 * se viene negato l'interruttore resta spento e si chiama [onPermissionDenied].
 */
@Composable
fun RemindersSection(
    reminders: ReminderSettings,
    onChange: (ReminderKind, Boolean) -> Unit,
    onPermissionDenied: () -> Unit,
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ReminderKind?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pending
        pending = null
        if (kind != null) {
            if (granted) onChange(kind, true) else onPermissionDenied()
        }
    }
    val toggle = { kind: ReminderKind, enabled: Boolean ->
        if (enabled && needsNotificationPermission && !context.hasNotificationPermission()) {
            pending = kind
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onChange(kind, enabled)
        }
    }
    Column {
        ReminderRow(
            stringResource(R.string.progressi_reminder_meals), stringResource(R.string.progressi_reminder_meals_hint),
            reminders.mealRemindersEnabled,
        ) { toggle(ReminderKind.MEALS, it) }
        ReminderRow(
            stringResource(R.string.progressi_reminder_water),
            stringResource(R.string.progressi_reminder_water_hint, reminders.waterIntervalMinutes / 60),
            reminders.waterRemindersEnabled,
        ) { toggle(ReminderKind.WATER, it) }
        ReminderRow(
            stringResource(R.string.progressi_reminder_tip), stringResource(R.string.progressi_reminder_tip_hint),
            reminders.dailyTipEnabled,
        ) { toggle(ReminderKind.TIP, it) }
        ReminderRow(
            stringResource(R.string.progressi_reminder_weighin), stringResource(R.string.progressi_reminder_weighin_hint),
            reminders.weighInReminderEnabled,
        ) { toggle(ReminderKind.WEIGH_IN, it) }
    }
}

@Composable
private fun ReminderRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
