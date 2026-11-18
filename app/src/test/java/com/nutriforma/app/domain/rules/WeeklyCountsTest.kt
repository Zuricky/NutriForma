package com.nutriforma.app.domain.rules

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.blankWeek
import com.nutriforma.app.domain.countsOf
import com.nutriforma.app.domain.generate
import com.nutriforma.app.domain.model.MealRole.*
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.with
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyCountsTest {

    @Test
    fun emptyWeekOnlyCountsMealTypes() {
        val c = countsOf(blankWeek(cheat = 1))
        assertEquals(2, c.colazioniDolci)
        assertEquals(1, c.pastiLiberi)
        assertEquals(4, c.cenePrimo)
        assertEquals(2, c.cenePrimoVerdure)
        assertEquals(2, c.cenePrimoProteico)
        assertEquals(2, c.ceneSecondo)
        assertEquals(1, c.ceneMinestrone)
        assertEquals(0, c.carne + c.pesce + c.uovaOccasioni + c.uovaTotale + c.tonno + c.affettato + c.patate)
        assertEquals(2, countsOf(blankWeek(cheat = 2)).pastiLiberi)
    }

    @Test
    fun twoComponentsOfTheSameCategoryInOneMealCountOneOccasion() {
        val week = blankWeek().with(
            0, MealSlot.PRANZO,
            PRANZO_SECONDO to "petto_pollo", PRANZO_ACCOMPAGNAMENTO to "mela",
        ).with(
            1, MealSlot.CENA,
            CENA_SECONDO to "petto_tacchino", CENA_SECONDO_ACCOMPAGNAMENTO to "mela",
        )
        val c = countsOf(week)
        assertEquals(2, c.carne)
        assertEquals(2, c.carneBianca)
        assertEquals(0, c.carneRossa)

        // Bianca + rossa nello stesso pasto: una sola occasione di carne, ma una per tipo.
        val both = blankWeek().with(0, MealSlot.PRANZO, PRANZO_SECONDO to "petto_pollo", PRANZO_ACCOMPAGNAMENTO to "patate")
        val comps = both.first { it.dayIndex == 0 && it.slot == MealSlot.PRANZO }.components +
            PlannedComponent(CENA_SECONDO, "manzo_magro", 110.0, PortionUnit.GRAMMI, 110.0)
        val mixed = both.map { if (it.dayIndex == 0 && it.slot == MealSlot.PRANZO) it.copy(components = comps) else it }
        val m = countsOf(mixed)
        assertEquals(1, m.carne)
        assertEquals(1, m.carneBianca)
        assertEquals(1, m.carneRossa)
        assertEquals(1, m.patate)
    }

    @Test
    fun ragùTonnoAndSmokedSalmonDoNotCountAsMeatOrFish() {
        val week = blankWeek()
            .with(2, MealSlot.CENA, CENA_PRIMO_BASE to "pasta_integrale", CENA_PRIMO_CONDIMENTO_PROTEICO to "ragu_carne")
            .with(0, MealSlot.PRANZO, PRANZO_SECONDO to "tonno_sottolio")
            .with(1, MealSlot.COLAZIONE, COLAZIONE_G1 to "salmone_affumicato")
        val c = countsOf(week)
        assertEquals("ragù non è carne", 0, c.carne)
        assertEquals("tonno e salmone affumicato non sono pesce", 0, c.pesce)
        assertEquals(1, c.tonno)
    }

    @Test
    fun affettatoCountsAtBreakfastAndInMinestrone() {
        val week = blankWeek()
            .with(0, MealSlot.COLAZIONE, COLAZIONE_G1 to "prosciutto_crudo")
            .with(3, MealSlot.CENA, CENA_MINESTRONE_AGGIUNTA to "bresaola")
        assertEquals(2, countsOf(week).affettato)
    }

    @Test
    fun eggAccountingIsOneTwoTwoOneOne() {
        var week = blankWeek()
            .with(0, MealSlot.COLAZIONE, COLAZIONE_G1 to "uovo") // 1
            .with(1, MealSlot.PRANZO, PRANZO_SECONDO to "uovo") // 2
            .with(1, MealSlot.CENA, CENA_SECONDO to "uovo") // 2
            .with(2, MealSlot.CENA, CENA_PRIMO_CONDIMENTO_PROTEICO to "uovo") // 1
            .with(3, MealSlot.CENA, CENA_MINESTRONE_AGGIUNTA to "uovo") // 1
        val c = countsOf(week)
        assertEquals(7, c.uovaTotale)
        assertEquals(5, c.uovaOccasioni)
        week = blankWeek().with(0, MealSlot.COLAZIONE, COLAZIONE_G1 to "uovo")
        assertEquals(1, countsOf(week).uovaTotale)
    }

    @Test
    fun unknownFoodIdsAreIgnored() {
        val week = blankWeek().map {
            if (it.dayIndex == 0 && it.slot == MealSlot.PRANZO) {
                it.copy(components = listOf(PlannedComponent(PRANZO_SECONDO, "non_esiste", 100.0, PortionUnit.GRAMMI, 100.0)))
            } else it
        }
        assertEquals(0, countsOf(week).carne)
    }

    @Test
    fun pastoLiberoCountsNothingButItself() {
        val week = blankWeek().with(5, MealSlot.PRANZO, PRANZO_LIBERO to "pasto_libero")
        val c = countsOf(week)
        assertEquals(1, c.pastiLiberi)
        assertEquals(0, c.carne + c.pesce + c.uovaOccasioni + c.tonno + c.patate)
        assertEquals(MealType.PRANZO_LIBERO, week.first { it.dayIndex == 5 && it.slot == MealSlot.PRANZO }.type)
    }

    @Test
    fun defaultGeneratedWeekMatchesTheSpecCounts() {
        val c = countsOf(generate().meals)
        assertEquals(3, c.carne)
        assertEquals(1, c.carneRossa)
        assertEquals(3, c.pesce)
        assertEquals(3, c.uovaOccasioni)
        assertEquals(5, c.uovaTotale)
        assertEquals(1, c.patate)
        assertEquals(TestFoods.all.size, 85)
    }
}
