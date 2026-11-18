package com.nutriforma.app.ui.profilo

import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.model.SportLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfiloUiStateTest {

    @Test
    fun portionScaleTextMatchesSportLevel() {
        assertEquals("Porzioni standard", portionScaleText(SportLevel.SEDENTARIO))
        assertEquals("Porzioni +40%", portionScaleText(SportLevel.MOLTO_INTENSO))
    }

    @Test
    fun kcalUsesItalianThousandsSeparator() {
        assertEquals("2.650 kcal", formatKcal(2650))
    }

    @Test
    fun folderNameIsReadableFromTreeUri() {
        assertEquals("Documents/DietaBackup", folderDisplayName("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDietaBackup"))
        assertEquals("content://altro", folderDisplayName("content://altro"))
    }

    @Test
    fun exclusionSearchIgnoresCaseAndAccentsAndGroupsByCategory() {
        val all = buildExclusionGroups(TestFoods.all, emptySet(), "")
        assertTrue(all.size > 10)
        assertTrue("il pasto libero non è escludibile", all.flatMap { it.rows }.none { it.id == "pasto_libero" })

        val found = buildExclusionGroups(TestFoods.all, setOf("caffe_espresso"), "CAFFÈ")
        val rows = found.flatMap { it.rows }
        assertTrue(rows.any { it.id == "caffe_espresso" && it.excluded })
        assertTrue(rows.none { it.id == "mela" })

        assertTrue(buildExclusionGroups(TestFoods.all, emptySet(), "zzzz").isEmpty())
        // la ricerca per categoria funziona
        assertTrue(buildExclusionGroups(TestFoods.all, emptySet(), "pesce").flatMap { it.rows }.any { it.id == "orata" })
    }
}
