package com.nutriforma.app.domain.model

data class RuleCheck(
    val ruleId: RuleId,
    val labelIt: String,
    val value: Int,
    val min: Int,
    /** null = ∞ */
    val max: Int?,
    val status: ComplianceStatus,
    val messageIt: String,
    val limitedByExclusions: Boolean = false,
)

data class ComplianceReport(val checks: List<RuleCheck>) {
    val hasViolations: Boolean get() = checks.any { it.status == ComplianceStatus.VIOLAZIONE }
    val warningCount: Int get() = checks.count { it.status == ComplianceStatus.ATTENZIONE }
}

data class GenerationWarning(val code: WarningCode, val messageIt: String)

data class NutritionTotals(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
) {
    operator fun plus(other: NutritionTotals) = NutritionTotals(
        kcal + other.kcal, proteinG + other.proteinG, carbsG + other.carbsG, fatG + other.fatG, fiberG + other.fiberG,
    )

    companion object {
        val ZERO = NutritionTotals(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

data class EnergyTarget(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)
