package com.nutriforma.app.ui.profilo

import com.nutriforma.app.domain.model.EnergyTarget
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.ReminderSettings
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import java.net.URLDecoder
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Riepilogo di un backup letto dal file, mostrato nella conferma del ripristino. */
data class RestoreSummary(val exportedAtText: String, val plans: Int, val measurements: Int)

data class ProfiloUiState(
    val goal: Goal = Goal.EQUILIBRIO,
    val sportLevel: SportLevel = SportLevel.MOLTO_INTENSO,
    val cheatMealsPerWeek: Int = 1,
    val sesso: Sesso? = null,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val energyTarget: EnergyTarget? = null,
    val excludedCount: Int = 0,
    val reminders: ReminderSettings = ReminderSettings(),
    val backupFolderName: String? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupError: String? = null,
    val lastBackupText: String? = null,
    val busy: Boolean = false,
    val pendingRestore: RestoreSummary? = null,
)

/** "Porzioni +40%" (fattore 1,40) oppure "Porzioni standard" (fattore 1,00). */
fun portionScaleText(level: SportLevel): String {
    val pct = Math.round((level.portionFactor - 1.0) * 100).toInt()
    return if (pct == 0) "Porzioni standard" else "Porzioni +$pct%"
}

/** "2.650 kcal" con separatore delle migliaia all'italiana. */
fun formatKcal(kcal: Int): String = NumberFormat.getIntegerInstance(Locale.ITALY).format(kcal) + " kcal"

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
