package com.nutriforma.app.domain.nutrition

import com.nutriforma.app.domain.model.EnergyTarget
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.UserProfile
import kotlin.math.max
import kotlin.math.roundToInt

/** SPEC §6.3: informative energy need (Mifflin-St Jeor); never changes the plan. */
class EnergyTargetCalculator {

    /** `null` when sesso, birthYear, heightCm or the weight are missing. */
    fun calculate(profile: UserProfile, weightKg: Double?, currentYear: Int): EnergyTarget? {
        val sesso = profile.sesso ?: return null
        val birthYear = profile.birthYear ?: return null
        val height = profile.heightCm ?: return null
        val weight = weightKg ?: return null

        val age = currentYear - birthYear
        val bmr = 10.0 * weight + 6.25 * height - 5.0 * age + (if (sesso == Sesso.MASCHIO) 5.0 else -161.0)
        val tdee = bmr * profile.sportLevel.activityFactor
        val goalFactor = when (profile.goal) {
            Goal.DIMAGRIMENTO -> 0.85
            Goal.EQUILIBRIO -> 1.00
            Goal.ENERGIA -> 1.10
        }
        val target = ((tdee * goalFactor) / 10.0).roundToInt() * 10

        val protein = profile.sportLevel.proteinGPerKg * weight
        val fatKcal = target * 3 / 10.0
        val fat = fatKcal / 9.0
        val carbs = max(0.0, (target - protein * 4 - fatKcal) / 4)
        return EnergyTarget(
            kcal = target,
            proteinG = protein.roundToInt(),
            carbsG = carbs.roundToInt(),
            fatG = fat.roundToInt(),
        )
    }
}
