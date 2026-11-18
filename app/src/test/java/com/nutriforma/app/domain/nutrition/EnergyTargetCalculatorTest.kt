package com.nutriforma.app.domain.nutrition

import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EnergyTargetCalculatorTest {

    private val calc = EnergyTargetCalculator()
    private val complete = UserProfile(
        sesso = Sesso.MASCHIO, birthYear = 1996, heightCm = 180,
        sportLevel = SportLevel.MOLTO_INTENSO, goal = Goal.EQUILIBRIO,
    )

    @Test
    fun maleThirtyYearsEightyKgVerySporty() {
        // BMR = 800 + 1125 − 150 + 5 = 1780; × 1.9 = 3382 → 3380.
        val t = calc.calculate(complete, 80.0, currentYear = 2026)
        assertNotNull(t)
        assertEquals(3380, t!!.kcal)
        assertEquals(144, t.proteinG) // 1,8 g/kg
        assertEquals(113, t.fatG) // 30% kcal / 9
        assertEquals(448, t.carbsG) // (3380 − 576 − 1014) / 4
    }

    @Test
    fun femaleUsesMinus161() {
        val p = complete.copy(sesso = Sesso.FEMMINA, sportLevel = SportLevel.SEDENTARIO)
        // BMR = 600 + 1125 − 150 − 161 = 1414 (w = 60): × 1.2 = 1696.8 → 1700.
        assertEquals(1700, calc.calculate(p, 60.0, 2026)!!.kcal)
    }

    @Test
    fun goalFactorsApply() {
        val base = calc.calculate(complete, 80.0, 2026)!!.kcal // 3382 → 3380
        assertEquals(3380 * 0.85, calc.calculate(complete.copy(goal = Goal.DIMAGRIMENTO), 80.0, 2026)!!.kcal.toDouble(), 10.0)
        assertEquals(3380 * 1.10, calc.calculate(complete.copy(goal = Goal.ENERGIA), 80.0, 2026)!!.kcal.toDouble(), 10.0)
        assertEquals(3380, base)
    }

    @Test
    fun missingDataGivesNull() {
        assertNull(calc.calculate(complete, null, 2026))
        assertNull(calc.calculate(complete.copy(sesso = null), 80.0, 2026))
        assertNull(calc.calculate(complete.copy(birthYear = null), 80.0, 2026))
        assertNull(calc.calculate(complete.copy(heightCm = null), 80.0, 2026))
    }

    @Test
    fun targetIsRoundedToTenKcal() {
        val t = calc.calculate(complete.copy(sportLevel = SportLevel.MODERATO), 71.3, 2026)!!
        assertEquals(0, t.kcal % 10)
    }
}
