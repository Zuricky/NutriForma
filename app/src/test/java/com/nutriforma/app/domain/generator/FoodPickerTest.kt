package com.nutriforma.app.domain.generator

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.MealRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FoodPickerTest {

    private val role = MealRole.PRANZO_ANTIPASTO
    private val veg = TestFoods.all.filter { it.category == FoodCategory.VERDURE && role in it.roles }

    @Test
    fun emptyCandidatesGiveNull() {
        assertNull(FoodPicker(Random(1)).pick(role, emptyList(), 0))
    }

    @Test
    fun sameSeedSameSequence() {
        fun run(seed: Int): List<String> {
            val p = FoodPicker(Random(seed))
            return (0..6).map { p.pick(role, veg, it)!!.id }
        }
        assertEquals(run(7), run(7))
    }

    @Test
    fun neverRepeatsTheSameFoodOnConsecutiveDays() {
        repeat(30) { seed ->
            val p = FoodPicker(Random(seed))
            val picks = (0..6).map { p.pick(role, veg, it)!!.id }
            picks.zipWithNext().forEach { (a, b) -> assertTrue("seed $seed: $a poi $b", a != b) }
        }
    }

    @Test
    fun spreadsUsageBeforeRepeating() {
        val p = FoodPicker(Random(3))
        // Giorni non consecutivi: nessun filtro "giorno prima", conta solo l'uso.
        val picks = veg.indices.map { p.pick(role, veg, it * 2)!!.id }
        assertEquals("ogni verdura una volta prima di ripeterne una", veg.size, picks.toSet().size)
    }

    @Test
    fun preferredFoodsWeighDouble() {
        val preferred = TestFoods.byId.getValue("pane_integrale")
        val plain = TestFoods.byId.getValue("pane_comune")
        val p = FoodPicker(Random(1))
        p.record(MealRole.PRANZO_ACCOMPAGNAMENTO, preferred.id, 0)
        p.record(MealRole.PRANZO_ACCOMPAGNAMENTO, plain.id, 0)
        // Un uso ciascuno: integrale = 1/2, comune = 1 → vince l'integrale (giorno 5, nessun filtro del giorno prima).
        assertEquals(preferred.id, p.pick(MealRole.PRANZO_ACCOMPAGNAMENTO, listOf(preferred, plain), 5)!!.id)
    }

    @Test
    fun previousDayFoodIsDroppedOnlyIfAnotherRemains() {
        val one = listOf(veg.first())
        val p = FoodPicker(Random(1))
        assertEquals(one.first().id, p.pick(role, one, 0)!!.id)
        // Unico candidato, usato ieri: resta comunque.
        assertEquals(one.first().id, p.pick(role, one, 1)!!.id)
    }

    @Test
    fun avoidFoodIsSoft() {
        val two = veg.take(2)
        repeat(20) { seed ->
            val chosen = FoodPicker(Random(seed)).pick(role, two, 0, avoidFoodId = two[0].id)!!
            assertEquals(two[1].id, chosen.id)
        }
        val single = listOf(two[0])
        assertEquals(two[0].id, FoodPicker(Random(1)).pick(role, single, 0, avoidFoodId = two[0].id)!!.id)
    }
}
