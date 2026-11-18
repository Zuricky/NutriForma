package com.nutriforma.app.domain.nutrition

import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PortionUnit
import kotlin.math.roundToInt

/** Italian quantity strings (SPEC §6.1). */
object PortionFormatter {

    fun format(component: PlannedComponent): String = format(component.quantity, component.unit, component.foodId)

    fun format(quantity: Double, unit: PortionUnit, foodId: String? = null): String {
        val q = quantity.roundToInt()
        return when (unit) {
            PortionUnit.GRAMMI -> "$q g"
            PortionUnit.PEZZI -> if (foodId == "uovo") (if (q == 1) "1 uovo" else "$q uova") else "$q pz"
            PortionUnit.PORZIONE -> ""
            else -> "$q ${if (q == 1) unit.singularIt else unit.pluralIt}"
        }
    }
}
