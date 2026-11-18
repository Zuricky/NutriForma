package com.nutriforma.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.nutriforma.app.R

data class BottomBarItem(
    val route: Any,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

/** Dieta · Spesa · Progressi · Profilo. */
val BottomBarItems: List<BottomBarItem> = listOf(
    BottomBarItem(Dieta, R.string.nav_dieta, Icons.Outlined.Restaurant),
    BottomBarItem(Spesa, R.string.nav_spesa, Icons.Outlined.ShoppingCart),
    BottomBarItem(Progressi, R.string.nav_progressi, Icons.Outlined.MonitorWeight),
    BottomBarItem(Profilo, R.string.nav_profilo, Icons.Outlined.Person),
)
