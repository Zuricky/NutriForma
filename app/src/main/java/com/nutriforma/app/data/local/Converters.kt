package com.nutriforma.app.data.local

import androidx.room.TypeConverter
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.TurnoLungoDay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// DECISION: the JSON state lives outside `Converters` with explicit types, because KSP (Room) fails with
// "references a type that is not present" on properties of the converter class whose type is only inferred.
private val converterJson: Json = Json { ignoreUnknownKeys = true }
private val rolesSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())
private val componentsSerializer: KSerializer<List<PlannedComponent>> = ListSerializer(PlannedComponent.serializer())
private val turnoLungoSerializer: KSerializer<List<TurnoLungoDay>> = ListSerializer(TurnoLungoDay.serializer())

/** Room type converters. Enums are handled by Room natively (TEXT by name); dates are stored as epoch days. */
object Converters {

    @TypeConverter
    fun fromMealRoleSet(value: Set<MealRole>): String =
        converterJson.encodeToString(rolesSerializer, value.map { it.name }.sorted())

    // DECISION: roles removed in a future app version are dropped instead of crashing the whole table read.
    @TypeConverter
    fun toMealRoleSet(value: String): Set<MealRole> =
        converterJson.decodeFromString(rolesSerializer, value)
            .mapNotNull { name -> MealRole.entries.firstOrNull { it.name == name } }
            .toSet()

    @TypeConverter
    fun fromComponents(value: List<PlannedComponent>): String =
        converterJson.encodeToString(componentsSerializer, value)

    @TypeConverter
    fun toComponents(value: String): List<PlannedComponent> =
        converterJson.decodeFromString(componentsSerializer, value)

    @TypeConverter
    fun fromTurnoLungoDays(value: List<TurnoLungoDay>): String =
        converterJson.encodeToString(turnoLungoSerializer, value)

    @TypeConverter
    fun toTurnoLungoDays(value: String): List<TurnoLungoDay> =
        converterJson.decodeFromString(turnoLungoSerializer, value)
}
