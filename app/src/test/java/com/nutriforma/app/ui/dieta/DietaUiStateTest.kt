package com.nutriforma.app.ui.dieta

import com.nutriforma.app.domain.MONDAY
import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.generate
import com.nutriforma.app.domain.model.ComplianceStatus
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.RuleId
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import com.nutriforma.app.domain.model.UserProfile
import com.nutriforma.app.domain.model.WeekPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DietaUiStateTest {

    private val profile = UserProfile() // Equilibrio, molto intenso, 1 pasto libero

    private fun plan(turno: List<TurnoLungoDay> = emptyList(), profile: UserProfile = this.profile): WeekPlan {
        val meals = generate(profile = profile, turnoLungo = turno).meals.mapIndexed { i, m -> m.copy(id = i + 1L, planId = 1) }
        return WeekPlan(1, MONDAY, MONDAY.toEpochDay(), profile.goal, profile.sportLevel, profile.cheatMealsPerWeek, turno, meals, 0, 0)
    }

    private fun content(
        day: Int = 0, plan: WeekPlan = plan(), completed: Set<Long> = emptySet(), profile: UserProfile = this.profile,
    ) = buildDietaContent(plan, TestFoods.all, profile, completed, day, today = MONDAY.plusDays(1))

    @Test
    fun fiveMealCardsInSlotOrderWithItalianTimes() {
        val c = content(day = 0)
        assertEquals(
            listOf(MealSlot.COLAZIONE, MealSlot.META_MATTINA, MealSlot.PRANZO, MealSlot.META_POMERIGGIO, MealSlot.CENA),
            c.meals.map { it.slot },
        )
        assertEquals("07:30", c.meals[0].timeLabel)
        assertEquals("14:00–16:00", c.meals[2].timeLabel)
        assertEquals("Metà mattina", c.meals[1].title)
        assertEquals("Metà pomeriggio", c.meals[3].title)
    }

    @Test
    fun lunchSummaryListsFoodsWithSportyGramsAndOliveOil() {
        val lunch = content(day = 0).meals[2]
        assertTrue(lunch.summary, " + " in lunch.summary)
        assertTrue(lunch.summary, lunch.summary.endsWith("Olio EVO (3 cucchiaini)"))
        // Secondo + accompagnamento carry grams, the vegetable starter does not ("Verdure" a volontà).
        assertEquals(2, Regex("""\(\d+ g\)""").findAll(lunch.summary).count())
        assertNull(lunch.summary.takeIf { "200 g" in it })
    }

    @Test
    fun componentFormattingFollowsTheSpec() {
        val foods = TestFoods.byId
        fun c(role: MealRole, id: String, q: Double, unit: PortionUnit, g: Double) = PlannedComponent(role, id, q, unit, g)
        assertEquals("Petto di pollo (180 g)", componentText(c(MealRole.PRANZO_SECONDO, "petto_pollo", 180.0, PortionUnit.GRAMMI, 180.0), foods))
        assertEquals("Patate (150 g)", componentText(c(MealRole.PRANZO_ACCOMPAGNAMENTO, "patate", 150.0, PortionUnit.GRAMMI, 150.0), foods))
        assertEquals("Zucchine", componentText(c(MealRole.PRANZO_ANTIPASTO, "zucchine", 200.0, PortionUnit.GRAMMI, 200.0), foods))
        assertEquals("Olio EVO (3 cucchiaini)", componentText(c(MealRole.PRANZO_CONDIMENTO, "olio_evo", 3.0, PortionUnit.CUCCHIAINI, 15.0), foods))
        assertEquals("2 uova", componentText(c(MealRole.PRANZO_SECONDO, "uovo", 2.0, PortionUnit.PEZZI, 110.0), foods))
        assertEquals("Alimento non disponibile", componentText(c(MealRole.PRANZO_SECONDO, "sconosciuto", 1.0, PortionUnit.GRAMMI, 1.0), foods))
    }

    @Test
    fun mealCardsShowKcalAndMacroBadges() {
        val breakfast = content(day = 0).meals[0]
        assertTrue(breakfast.kcalBadge!!.endsWith(" kcal"))
        assertEquals(listOf("P", "C", "G"), breakfast.macroBadges.map { it.substringBefore(' ') })
    }

    @Test
    fun elevenCompliancePillsInTheRequestedOrder() {
        val pills = content().pills
        assertEquals(
            listOf(
                RuleId.CARNE, RuleId.CARNE_ROSSA, RuleId.PESCE, RuleId.UOVA_TOTALE, RuleId.TONNO,
                RuleId.AFFETTATO, RuleId.PATATE, RuleId.CENA_PRIMO_VERDURE, RuleId.CENA_PRIMO_PROTEICO,
                RuleId.COLAZIONE_DOLCE, RuleId.PASTO_LIBERO,
            ),
            pills.map { it.ruleId },
        )
        assertEquals(
            listOf(
                "Carne", "Carne rossa", "Pesce", "Uova", "Tonno", "Affettato", "Patate",
                "Pasta con verdure", "Pasta con proteine", "Colazione dolce", "Pasti liberi",
            ),
            pills.map { it.text.substringBefore(" (").dropLastWhile { ch -> ch.isDigit() || ch == ' ' } },
        )
        assertTrue("settimana generata: nessuna violazione", pills.none { it.status == ComplianceStatus.VIOLAZIONE })
        assertFalse(content().hasViolations)
        assertTrue(pills.first { it.ruleId == RuleId.PASTO_LIBERO }.text.contains("(1)"))
    }

    @Test
    fun freeLunchDayHasNoKcalAndIsFlagged() {
        val c = content(day = 5) // sabato
        val lunch = c.meals[2]
        assertTrue(c.isPranzoLibero)
        assertTrue(lunch.isFree)
        assertNull(lunch.kcalBadge)
        assertFalse(lunch.canSwap)
        assertEquals("Pasto libero, goditelo!", lunch.summary)
        assertTrue(c.dayKcalText.endsWith("(pasto libero escluso)"))
        assertTrue("può essere annullato", c.canTogglePranzoLibero)
    }

    @Test
    fun freeLunchQuotaBlocksTheToggleOnOtherDays() {
        val monday = content(day = 0)
        assertFalse(monday.isPranzoLibero)
        assertFalse(monday.canTogglePranzoLibero)
        assertTrue(monday.pranzoLiberoHint!!.contains("pasto libero"))

        val twoFree = profile.copy(cheatMealsPerWeek = 2)
        val withTwo = content(day = 0, plan = plan(profile = twoFree), profile = twoFree)
        assertEquals(false, withTwo.canTogglePranzoLibero) // Mer + Sab già usati
        val c3 = content(day = 2, plan = plan(profile = twoFree), profile = twoFree)
        assertTrue(c3.isPranzoLibero && c3.canTogglePranzoLibero)
    }

    @Test
    fun turnoLungoDayMergesTheSnacksAndShowsTheSwitchOn() {
        val turno = listOf(TurnoLungoDay(1, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_POMERIGGIO))
        val c = content(day = 1, plan = plan(turno))
        assertTrue(c.isTurnoLungo)
        val morning = c.meals.first { it.slot == MealSlot.META_MATTINA }
        val afternoon = c.meals.first { it.slot == MealSlot.META_POMERIGGIO }
        assertTrue(morning.isMerged)
        assertEquals("Accorpato nello spuntino unico del turno lungo", morning.summary)
        assertNull(morning.kcalBadge)
        assertEquals("Metà pomeriggio · Turno lungo", afternoon.title)
        assertTrue(afternoon.canSwap)
        assertFalse(content(day = 0, plan = plan(turno)).isTurnoLungo)
    }

    @Test
    fun completedMealsAreFlaggedAndDaysMarkToday() {
        val p = plan()
        val first = p.meals.first { it.dayIndex == 0 && it.slot == MealSlot.COLAZIONE }
        val c = content(day = 0, plan = p, completed = setOf(first.id))
        assertEquals(listOf(true, false, false, false, false), c.meals.map { it.isCompleted })
        assertEquals(listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"), c.days.map { it.shortName })
        assertEquals(listOf(false, true, false, false, false, false, false), c.days.map { it.isToday })
        assertEquals(16, c.days.first().dayOfMonth)
    }

    @Test
    fun sportyPortionsAreLargerThanSedentaryOnes() {
        val sporty = content(plan = plan(profile = profile.copy(sportLevel = SportLevel.MOLTO_INTENSO, goal = Goal.ENERGIA)))
        val sedentary = profile.copy(sportLevel = SportLevel.SEDENTARIO, goal = Goal.ENERGIA)
        val calm = content(plan = plan(profile = sedentary), profile = sedentary)
        val parse = { s: String -> s.substringBefore(" kcal").replace(".", "").toInt() }
        assertTrue(parse(sporty.meals[2].kcalBadge!!) > parse(calm.meals[2].kcalBadge!!))
    }

    @Test
    fun formattingHelpersUseItalianConventions() {
        assertEquals("+35 kcal", formatKcalDelta(35))
        assertEquals("−120 kcal", formatKcalDelta(-120))
        assertEquals("Stesse kcal", formatKcalDelta(0))
        assertEquals("1.850 kcal", formatKcal(1849.6))
        assertEquals("10:30", timeLabel(MealSlot.META_MATTINA))
        assertEquals("17:30", timeLabel(MealSlot.META_POMERIGGIO))
        assertEquals("20:30", timeLabel(MealSlot.CENA))
    }
}
