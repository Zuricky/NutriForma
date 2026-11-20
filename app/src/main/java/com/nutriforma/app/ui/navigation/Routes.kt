package com.nutriforma.app.ui.navigation

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** Type-safe routes of the 4 bottom-bar destinations. */
@Serializable data object Dieta
@Serializable data object Spesa
@Serializable data object Progressi
@Serializable data object Profilo

/** Sotto-schermata del Profilo (non è una scheda della barra inferiore). */
@Serializable data object Esclusioni

/** Sotto-schermata del Profilo, aperta dall'icona a forma di ingranaggio in alto a destra (non è una scheda della barra inferiore). */
@Serializable data object Impostazioni

/** Sotto-schermate raggiungibili dal Profilo: mentre sono aperte resta evidenziata la scheda Profilo. */
val ProfiloSubRoutes: Set<KClass<*>> = setOf(Esclusioni::class, Impostazioni::class)
