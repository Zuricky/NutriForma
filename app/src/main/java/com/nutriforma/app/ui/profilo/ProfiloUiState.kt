package com.nutriforma.app.ui.profilo

import com.nutriforma.app.domain.model.EnergyTarget
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.nutrition.BmiResult
import com.nutriforma.app.domain.tracking.MeasurementMetric
import java.text.NumberFormat
import java.util.Locale

/** Lunghezza massima di nome e cognome. */
const val MAX_NAME_LENGTH = 30

/**
 * Bozza dei dati personali: un campo `null` non è stato toccato e mostra il valore salvato.
 * Il peso non fa parte del profilo: salvandolo diventa la misura di oggi (stessa fonte di Progressi).
 */
data class PersonalDraft(
    val firstName: String? = null,
    val lastName: String? = null,
    val sesso: Sesso? = null,
    val birthYearText: String? = null,
    val heightText: String? = null,
    val weightText: String? = null,
    /** Dopo un tentativo di salvataggio non valido gli errori restano visibili finché i campi non sono corretti. */
    val showErrors: Boolean = false,
)

/** Valore letto da un campo di testo: [value] `null` con campo vuoto, [error] in italiano se il testo non è valido. */
data class FieldResult(val value: Int?, val error: String?)

/** Come [FieldResult] ma per il peso in kg (con decimali). */
data class WeightResult(val kg: Double?, val error: String?)

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

/** Peso: vuoto = nessun dato, altrimenti tra 30 e 250 kg (virgola o punto decimali). */
fun parseWeight(text: String): WeightResult {
    val clean = text.trim().replace(',', '.')
    if (clean.isEmpty()) return WeightResult(null, null)
    val metric = MeasurementMetric.PESO
    val kg = clean.toDoubleOrNull()?.takeIf { it.isFinite() && it >= metric.min && it <= metric.max }
    return if (kg == null) {
        WeightResult(null, "Peso non valido (tra ${metric.min.toInt()} e ${metric.max.toInt()} kg).")
    } else {
        WeightResult(kg, null)
    }
}

/** Testo del campo peso: "78" se intero, altrimenti "78,5" (virgola decimale, un decimale). */
fun formatWeightInput(kg: Double): String = String.format(Locale.ITALY, "%.1f", kg).removeSuffix(",0")

/** Peso arrotondato a un decimale: serve a confrontare il valore digitato con quello salvato. */
fun roundWeight(kg: Double): Double = Math.round(kg * 10) / 10.0

/** Tiene solo cifre e un unico separatore decimale (',' o '.'), al massimo 6 caratteri. */
fun sanitizeWeightInput(text: String): String {
    var separatorSeen = false
    val out = StringBuilder()
    for (c in text) {
        when {
            c.isDigit() -> out.append(c)
            (c == ',' || c == '.') && !separatorSeen -> {
                separatorSeen = true
                out.append(c)
            }
        }
    }
    return out.toString().take(6)
}

/** "Mario Rossi", solo il nome, solo il cognome oppure stringa vuota. */
fun fullName(firstName: String, lastName: String): String = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")

data class ProfiloUiState(
    val goal: Goal = Goal.EQUILIBRIO,
    val sportLevel: SportLevel = SportLevel.MOLTO_INTENSO,
    val cheatMealsPerWeek: Int = 1,

    // ---- Dati personali salvati (modalità sola lettura) ----
    val firstName: String = "",
    val lastName: String = "",
    val birthYear: Int? = null,
    val ageYears: Int? = null,
    val heightCm: Int? = null,
    /** Ultimo peso registrato (stessa fonte di Progressi). */
    val weightKg: Double? = null,
    val bmi: BmiResult? = null,
    /** `true` se almeno un dato personale è stato salvato: da quel momento la scheda è bloccata finché non si preme "Modifica". */
    val hasSavedPersonalData: Boolean = false,

    // ---- Modifica ----
    /** Scheda in modifica: dopo "Modifica", oppure subito se non è ancora stato salvato nulla. */
    val isEditing: Boolean = true,
    /** Valore mostrato (bozza se presente, altrimenti salvato). */
    val sesso: Sesso? = null,
    val firstNameText: String = "",
    val lastNameText: String = "",
    val birthYearText: String = "",
    val heightText: String = "",
    val weightText: String = "",
    val birthYearError: String? = null,
    val heightError: String? = null,
    val weightError: String? = null,
    /** `true` se i dati mostrati in modifica differiscono da quelli salvati (serve premere "Salva"). */
    val personalDirty: Boolean = false,

    val energyTarget: EnergyTarget? = null,
    val excludedCount: Int = 0,
)

/** "Porzioni +40%" (fattore 1,40) oppure "Porzioni standard" (fattore 1,00). */
fun portionScaleText(level: SportLevel): String {
    val pct = Math.round((level.portionFactor - 1.0) * 100).toInt()
    return if (pct == 0) "Porzioni standard" else "Porzioni +$pct%"
}

/** "2.650 kcal" con separatore delle migliaia all'italiana. */
fun formatKcal(kcal: Int): String = NumberFormat.getIntegerInstance(Locale.ITALY).format(kcal) + " kcal"

/** "22,4" (virgola decimale). */
fun formatBmi(value: Double): String = String.format(Locale.ITALY, "%.1f", value)
