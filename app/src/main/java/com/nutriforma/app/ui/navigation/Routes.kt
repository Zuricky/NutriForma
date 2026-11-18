package com.nutriforma.app.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe routes of the 4 bottom-bar destinations. */
@Serializable data object Dieta
@Serializable data object Spesa
@Serializable data object Progressi
@Serializable data object Profilo

/** Sotto-schermata del Profilo (non è una scheda della barra inferiore). */
@Serializable data object Esclusioni
