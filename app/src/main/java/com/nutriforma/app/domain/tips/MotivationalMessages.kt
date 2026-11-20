package com.nutriforma.app.domain.tips

import com.nutriforma.app.domain.model.BodyMeasurement
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.tracking.MeasurementMetric
import com.nutriforma.app.domain.tracking.formatDelta
import com.nutriforma.app.domain.tracking.seriesOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Traguardi per cui l'app fa un complimento. */
enum class MotivationTrigger {
    WATER_GOAL,
    DAY_COMPLETED,
    WEEK_STRONG,
    GOAL_PROGRESS,
    TRACKING_STREAK,
}

/** Un complimento: [text] è la frase, [detail] un dato concreto opzionale (es. "−2,1 kg dall'inizio"). */
data class MotivationalMessage(val trigger: MotivationTrigger, val text: String, val detail: String? = null)

/**
 * Frasi di incoraggiamento e regole per decidere quando mostrarle. Tutto puro e deterministico: la frase dipende solo
 * dal traguardo e dalla data, quindi non cambia da una ricomposizione all'altra e resta la stessa per tutta la giornata.
 */
object MotivationalMessages {

    /** Settimana "solida": almeno questa percentuale dei pasti completata e nessuna regola superata. */
    const val STRONG_WEEK_PERCENT = 85

    /** Finestra e soglia per il complimento sulla costanza delle misure. */
    const val TRACKING_WINDOW_DAYS = 14
    const val TRACKING_MIN_DAYS = 3

    /** Per l'obiettivo "peso stabile" servono due misure distanti almeno questi giorni, altrimenti non dice nulla. */
    const val STABLE_MIN_SPAN_DAYS = 14L

    /** Oscillazione massima (kg) per considerare il peso stabile. */
    const val STABLE_TOLERANCE_KG = 1.0

    val WATER_PHRASES: List<String> = listOf(
        "Obiettivo acqua raggiunto: ottimo lavoro!",
        "Hai bevuto tutta l'acqua di oggi: il tuo corpo ringrazia.",
        "Idratazione completata, continua così!",
        "Acqua di oggi tutta nel bicchiere: complimenti per la costanza!",
        "Traguardo raggiunto: oggi l'idratazione è al top.",
    )

    val DAY_PHRASES: List<String> = listOf(
        "Giornata completata: tutti i pasti sono fatti, complimenti!",
        "Tutti i pasti di oggi sono fatti: ottimo lavoro!",
        "Oggi hai seguito l'intero piano: ottimo lavoro!",
        "Giornata perfetta, continua così!",
        "Pasti completati: ogni giorno è un passo verso il tuo obiettivo.",
    )

    val WEEK_PHRASES: List<String> = listOf(
        "Settimana solida: hai seguito il piano e rispettato le regole!",
        "Che settimana! Costanza e regole rispettate: complimenti.",
        "Stai andando alla grande: la settimana è quasi un en plein.",
        "Piano seguito con regolarità e nessuna regola superata: complimenti!",
        "Una settimana da incorniciare: continua con questo ritmo.",
    )

    val LOSS_PHRASES: List<String> = listOf(
        "Stai andando nella direzione giusta: il peso scende!",
        "I risultati si vedono: continua così!",
        "Il tuo impegno sta pagando, passo dopo passo.",
        "Ottimo progresso verso il tuo obiettivo!",
        "Complimenti, la costanza sta dando i suoi frutti.",
    )

    val STABLE_PHRASES: List<String> = listOf(
        "Peso stabile: stai mantenendo il tuo equilibrio!",
        "Ottima costanza: il peso si mantiene stabile.",
        "Equilibrio raggiunto, continua con questo ritmo.",
        "Il tuo peso è stabile: segno che stai lavorando bene.",
        "Stabilità e regolarità: è la strada giusta.",
    )

    val WAIST_PHRASES: List<String> = listOf(
        "La vita si sta riducendo: ottimo segnale!",
        "Centimetri in meno sulla vita: il lavoro sta pagando!",
        "Bel risultato: le misure stanno migliorando.",
        "I progressi si vedono anche in vita: complimenti!",
        "Continua così: la vita sta rispondendo bene.",
    )

    val TRACKING_PHRASES: List<String> = listOf(
        "Stai registrando le misure con costanza: è così che si vedono i progressi!",
        "Misure regolari: ottima abitudine, continua così.",
        "Monitorare i progressi è metà del lavoro: ottima abitudine!",
        "Che costanza con le misure: complimenti.",
        "Tenere traccia dei risultati ti aiuta a restare sulla rotta.",
    )

    /** Frase scelta in base alla data: stessa data e stesso traguardo, stessa frase. */
    fun pick(phrases: List<String>, trigger: MotivationTrigger, date: LocalDate): String {
        require(phrases.isNotEmpty()) { "Servono delle frasi" }
        val index = Math.floorMod(date.toEpochDay() + trigger.ordinal, phrases.size.toLong()).toInt()
        return phrases[index]
    }

    /** Acqua di oggi >= obiettivo. */
    fun forWater(waterMl: Int, goalMl: Int, date: LocalDate): MotivationalMessage? =
        if (goalMl > 0 && waterMl >= goalMl) {
            MotivationalMessage(MotivationTrigger.WATER_GOAL, pick(WATER_PHRASES, MotivationTrigger.WATER_GOAL, date))
        } else {
            null
        }

    /** Tutti i pasti da completare di oggi sono completati ([completable] > 0). */
    fun forDay(completable: Int, completed: Int, date: LocalDate): MotivationalMessage? =
        if (completable > 0 && completed >= completable) {
            MotivationalMessage(MotivationTrigger.DAY_COMPLETED, pick(DAY_PHRASES, MotivationTrigger.DAY_COMPLETED, date))
        } else {
            null
        }

    /** Settimana in corso con almeno [STRONG_WEEK_PERCENT]% dei pasti completati e nessuna regola superata. */
    fun forWeek(completable: Int, completed: Int, hasViolations: Boolean, date: LocalDate): MotivationalMessage? =
        if (completable > 0 && !hasViolations && completed * 100 >= completable * STRONG_WEEK_PERCENT) {
            MotivationalMessage(MotivationTrigger.WEEK_STRONG, pick(WEEK_PHRASES, MotivationTrigger.WEEK_STRONG, date))
        } else {
            null
        }

    /**
     * Progresso verso l'obiettivo, in ordine di priorità:
     * 1. Dimagrimento: peso più basso della prima misura (poi, in alternativa, vita più bassa);
     *    Equilibrio / Energia: peso stabile (±[STABLE_TOLERANCE_KG] kg) su almeno [STABLE_MIN_SPAN_DAYS] giorni.
     * 2. Costanza: misure in almeno [TRACKING_MIN_DAYS] giorni diversi negli ultimi [TRACKING_WINDOW_DAYS] giorni.
     */
    fun forProgress(goal: Goal, measurements: List<BodyMeasurement>, today: LocalDate): MotivationalMessage? {
        val weights = seriesOf(measurements, MeasurementMetric.PESO)
        if (weights.size >= 2) {
            val first = weights.first()
            val last = weights.last()
            val delta = round1(last.value - first.value)
            when (goal) {
                Goal.DIMAGRIMENTO -> if (delta < 0) {
                    return MotivationalMessage(
                        MotivationTrigger.GOAL_PROGRESS,
                        pick(LOSS_PHRASES, MotivationTrigger.GOAL_PROGRESS, today),
                        formatDelta(delta, "kg") + SINCE_START,
                    )
                }
                Goal.EQUILIBRIO, Goal.ENERGIA -> {
                    val span = ChronoUnit.DAYS.between(first.date, last.date)
                    if (span >= STABLE_MIN_SPAN_DAYS && abs(delta) <= STABLE_TOLERANCE_KG) {
                        return MotivationalMessage(
                            MotivationTrigger.GOAL_PROGRESS,
                            pick(STABLE_PHRASES, MotivationTrigger.GOAL_PROGRESS, today),
                            formatDelta(delta, "kg") + SINCE_START,
                        )
                    }
                }
            }
        }
        if (goal == Goal.DIMAGRIMENTO) {
            val waists = seriesOf(measurements, MeasurementMetric.VITA)
            if (waists.size >= 2) {
                val delta = round1(waists.last().value - waists.first().value)
                if (delta < 0) {
                    return MotivationalMessage(
                        MotivationTrigger.GOAL_PROGRESS,
                        pick(WAIST_PHRASES, MotivationTrigger.GOAL_PROGRESS, today),
                        formatDelta(delta, "cm") + SINCE_START,
                    )
                }
            }
        }
        val windowStart = today.minusDays(TRACKING_WINDOW_DAYS - 1L)
        val recentDays = measurements.map { it.date }.filter { !it.isBefore(windowStart) && !it.isAfter(today) }.toSet().size
        if (recentDays >= TRACKING_MIN_DAYS) {
            return MotivationalMessage(MotivationTrigger.TRACKING_STREAK, pick(TRACKING_PHRASES, MotivationTrigger.TRACKING_STREAK, today))
        }
        return null
    }

    private const val SINCE_START = " dall'inizio"

    private fun round1(value: Double): Double = Math.round(value * 10) / 10.0
}
