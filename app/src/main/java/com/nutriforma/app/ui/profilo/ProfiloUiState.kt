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

/** Esito dell'ultima azione di backup/ripristino, sempre visibile nella scheda (oltre allo snackbar). */
data class BackupStatus(val message: String, val isError: Boolean)

/** Bozza dei dati personali: un campo `null` non è stato toccato e mostra il valore salvato. */
data class PersonalDraft(
    val sesso: Sesso? = null,
    val birthYearText: String? = null,
    val heightText: String? = null,
    /** Dopo un tentativo di salvataggio non valido gli errori restano visibili finché i campi non sono corretti. */
    val showErrors: Boolean = false,
)

/** Valore letto da un campo di testo: [value] `null` con campo vuoto, [error] in italiano se il testo non è valido. */
data class FieldResult(val value: Int?, val error: String?)

/** Anno di nascita: vuoto = nessun dato, altrimenti tra 1920 e (anno corrente − 10). */
fun parseBirthYear(text: String, currentYear: Int): FieldResult {
    val clean = text.trim()
    if (clean.isEmpty()) return FieldResult(null, null)
    val max = currentYear - 10
    val year = clean.toIntOrNull()
    return if (year == null || year !in 1920..max) FieldResult(null, "Anno di nascita non valido (tra 1920 e $max).") else FieldResult(year, null)
}

/** Altezza: vuoto = nessun dato, altrimenti tra 100 e 230 cm. */
fun parseHeight(text: String): FieldResult {
    val clean = text.trim()
    if (clean.isEmpty()) return FieldResult(null, null)
    val cm = clean.toIntOrNull()
    return if (cm == null || cm !in 100..230) FieldResult(null, "Altezza non valida (tra 100 e 230 cm).") else FieldResult(cm, null)
}

data class ProfiloUiState(
    val goal: Goal = Goal.EQUILIBRIO,
    val sportLevel: SportLevel = SportLevel.MOLTO_INTENSO,
    val cheatMealsPerWeek: Int = 1,
    /** Valore mostrato (bozza se presente, altrimenti salvato). */
    val sesso: Sesso? = null,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val birthYearText: String = "",
    val heightText: String = "",
    val birthYearError: String? = null,
    val heightError: String? = null,
    /** `true` se i dati personali mostrati differiscono da quelli salvati (serve premere "Salva"). */
    val personalDirty: Boolean = false,
    val energyTarget: EnergyTarget? = null,
    val excludedCount: Int = 0,
    val reminders: ReminderSettings = ReminderSettings(),
    val backupFolderName: String? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupError: String? = null,
    val lastBackupText: String? = null,
    val busy: Boolean = false,
    val pendingRestore: RestoreSummary? = null,
    val backupStatus: BackupStatus? = null,
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
