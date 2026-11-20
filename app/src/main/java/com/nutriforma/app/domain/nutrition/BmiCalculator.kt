package com.nutriforma.app.domain.nutrition

/** Fasce dell'indice di massa corporea (IMC) con l'etichetta italiana mostrata all'utente. */
enum class BmiCategory(val labelIt: String) {
    SOTTOPESO("Sottopeso"),
    NORMOPESO("Normopeso"),
    SOVRAPPESO("Sovrappeso"),
    OBESITA("Obesità"),
}

/** [value] è già arrotondato a un decimale (quello che si legge a schermo); [category] è calcolata su quel valore. */
data class BmiResult(val value: Double, val category: BmiCategory)

/**
 * IMC = peso (kg) / altezza (m)². Fasce: sotto 18,5 sottopeso · 18,5–24,9 normopeso · 25,0–29,9 sovrappeso ·
 * 30,0 e oltre obesità. Indicativo: non distingue massa muscolare e massa grassa.
 */
object BmiCalculator {
    const val UNDERWEIGHT_BELOW = 18.5
    const val OVERWEIGHT_FROM = 25.0
    const val OBESITY_FROM = 30.0

    /** `null` se peso o altezza mancano o non sono positivi. */
    fun calculate(weightKg: Double?, heightCm: Int?): BmiResult? {
        if (weightKg == null || heightCm == null) return null
        if (!weightKg.isFinite() || weightKg <= 0.0 || heightCm <= 0) return null
        val heightM = heightCm / 100.0
        val rounded = Math.round(weightKg / (heightM * heightM) * 10) / 10.0
        return BmiResult(rounded, classify(rounded))
    }

    fun classify(bmi: Double): BmiCategory = when {
        bmi < UNDERWEIGHT_BELOW -> BmiCategory.SOTTOPESO
        bmi < OVERWEIGHT_FROM -> BmiCategory.NORMOPESO
        bmi < OBESITY_FROM -> BmiCategory.SOVRAPPESO
        else -> BmiCategory.OBESITA
    }
}
