package com.nutriforma.app.domain.nutrition

import com.nutriforma.app.domain.model.PortionUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class PortionFormatterTest {

    @Test
    fun formatsEveryUnitInItalian() {
        assertEquals("105 g", PortionFormatter.format(105.0, PortionUnit.GRAMMI))
        assertEquals("1 uovo", PortionFormatter.format(1.0, PortionUnit.PEZZI, "uovo"))
        assertEquals("2 uova", PortionFormatter.format(2.0, PortionUnit.PEZZI, "uovo"))
        assertEquals("3 pz", PortionFormatter.format(3.0, PortionUnit.PEZZI, "fette_biscottate"))
        assertEquals("3 cucchiaini", PortionFormatter.format(3.0, PortionUnit.CUCCHIAINI))
        assertEquals("1 cucchiaino", PortionFormatter.format(1.0, PortionUnit.CUCCHIAINI))
        assertEquals("1 cucchiaio", PortionFormatter.format(1.0, PortionUnit.CUCCHIAI))
        assertEquals("1 tazzina", PortionFormatter.format(1.0, PortionUnit.TAZZINA))
        assertEquals("1 tazza", PortionFormatter.format(1.0, PortionUnit.TAZZA))
        assertEquals("1 piatto", PortionFormatter.format(1.0, PortionUnit.PIATTO))
        assertEquals("", PortionFormatter.format(1.0, PortionUnit.PORZIONE))
    }
}
