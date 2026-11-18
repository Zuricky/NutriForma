package com.nutriforma.app.data.local

import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    @Test
    fun mealRoleSetRoundTrips() {
        val roles = setOf(MealRole.COLAZIONE_G1, MealRole.CENA_SECONDO, MealRole.PRANZO_SECONDO)
        val encoded = Converters.fromMealRoleSet(roles)
        assertEquals(roles, Converters.toMealRoleSet(encoded))
        assertEquals(MealRole.entries.toSet(), Converters.toMealRoleSet(Converters.fromMealRoleSet(MealRole.entries.toSet())))
        assertEquals(emptySet<MealRole>(), Converters.toMealRoleSet(Converters.fromMealRoleSet(emptySet())))
    }

    @Test
    fun mealRoleSetIsStoredAsAJsonArrayOfNames() {
        val encoded = Converters.fromMealRoleSet(setOf(MealRole.CENA_SECONDO, MealRole.COLAZIONE_G1))
        assertEquals("""["CENA_SECONDO","COLAZIONE_G1"]""", encoded)
    }

    @Test
    fun unknownRoleNamesAreDropped() {
        assertEquals(setOf(MealRole.COLAZIONE_G1), Converters.toMealRoleSet("""["COLAZIONE_G1","RUOLO_FUTURO"]"""))
    }

    @Test
    fun componentsRoundTrip() {
        val components = listOf(
            PlannedComponent(MealRole.PRANZO_ANTIPASTO, "zucchine", 200.0, PortionUnit.GRAMMI, 200.0),
            PlannedComponent(MealRole.PRANZO_SECONDO, "uovo", 2.0, PortionUnit.PEZZI, 110.0, isOptional = false),
            PlannedComponent(MealRole.COLAZIONE_BEVANDA, "caffe_espresso", 1.0, PortionUnit.TAZZINA, 30.0, isOptional = true),
            PlannedComponent(MealRole.PRANZO_LIBERO, "pasto_libero", 1.0, PortionUnit.PORZIONE, 0.0),
        )
        assertEquals(components, Converters.toComponents(Converters.fromComponents(components)))
        assertEquals(emptyList<PlannedComponent>(), Converters.toComponents(Converters.fromComponents(emptyList())))
    }

    @Test
    fun componentsJsonUsesTheDocumentedFieldNames() {
        val json = Converters.fromComponents(
            listOf(PlannedComponent(MealRole.PRANZO_SECONDO, "petto_pollo", 155.0, PortionUnit.GRAMMI, 155.0)),
        )
        listOf("role", "foodId", "quantity", "unit", "grams").forEach { assertTrue("manca $it in $json", "\"$it\"" in json) }
        assertTrue("PRANZO_SECONDO" in json && "GRAMMI" in json)
    }

    @Test
    fun turnoLungoDaysRoundTrip() {
        val days = listOf(
            TurnoLungoDay(1, TurnoLungoOption.FRUTTA_E_SECCA, MealSlot.META_POMERIGGIO),
            TurnoLungoDay(4, TurnoLungoOption.BARRETTA, MealSlot.META_MATTINA),
        )
        assertEquals(days, Converters.toTurnoLungoDays(Converters.fromTurnoLungoDays(days)))
        assertEquals(emptyList<TurnoLungoDay>(), Converters.toTurnoLungoDays(Converters.fromTurnoLungoDays(emptyList())))
    }

    @Test
    fun unknownKeysAreIgnoredWhenDecoding() {
        val json = """[{"dayIndex":2,"option":"BARRETTA","slot":"META_MATTINA","extra":true}]"""
        assertEquals(
            listOf(TurnoLungoDay(2, TurnoLungoOption.BARRETTA, MealSlot.META_MATTINA)),
            Converters.toTurnoLungoDays(json),
        )
    }
}
