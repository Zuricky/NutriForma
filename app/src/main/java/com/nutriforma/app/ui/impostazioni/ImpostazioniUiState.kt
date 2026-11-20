package com.nutriforma.app.ui.impostazioni

import com.nutriforma.app.domain.model.ReminderSettings
import java.net.URLDecoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Riepilogo di un backup letto dal file, mostrato nella conferma del ripristino. */
data class RestoreSummary(val exportedAtText: String, val plans: Int, val measurements: Int)

/** Esito dell'ultima azione di backup/ripristino, sempre visibile nella scheda (oltre allo snackbar). */
data class BackupStatus(val message: String, val isError: Boolean)

data class ImpostazioniUiState(
    val reminders: ReminderSettings = ReminderSettings(),
    val backupFolderName: String? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupError: String? = null,
    val lastBackupText: String? = null,
    val busy: Boolean = false,
    val pendingRestore: RestoreSummary? = null,
    val backupStatus: BackupStatus? = null,
)

private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMM yyyy 'alle' HH:mm", Locale.ITALY)

fun formatDateTime(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(dateTimeFormat)

/**
 * Nome leggibile della cartella scelta con il selettore (URI tipo `content://…/tree/primary%3ADocuments%2FDietaBackup`
 * → `Documents/DietaBackup`). Se non riconosciuto restituisce l'URI intero.
 */
fun folderDisplayName(uri: String): String {
    val tail = uri.substringAfterLast("/tree/", missingDelimiterValue = "")
    if (tail.isEmpty()) return uri
    val decoded = runCatching { URLDecoder.decode(tail, "UTF-8") }.getOrDefault(tail)
    return decoded.substringAfter(':').ifEmpty { decoded }
}
