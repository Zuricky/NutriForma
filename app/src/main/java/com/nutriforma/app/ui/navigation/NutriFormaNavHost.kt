package com.nutriforma.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nutriforma.app.ui.AppViewModelProvider
import com.nutriforma.app.ui.dieta.DietaScreen
import com.nutriforma.app.ui.profilo.EsclusioniScreen
import com.nutriforma.app.ui.profilo.ProfiloScreen
import com.nutriforma.app.ui.progressi.ProgressiScreen
import com.nutriforma.app.ui.spesa.SpesaScreen

@Composable
fun NutriFormaNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Dieta, modifier = modifier) {
        composable<Dieta> {
            DietaScreen(viewModel = viewModel(factory = AppViewModelProvider.Factory))
        }
        composable<Spesa> {
            SpesaScreen(viewModel = viewModel(factory = AppViewModelProvider.Factory))
        }
        composable<Progressi> {
            ProgressiScreen(viewModel = viewModel(factory = AppViewModelProvider.Factory))
        }
        composable<Profilo> {
            ProfiloScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onOpenEsclusioni = { navController.navigate(Esclusioni) },
            )
        }
        composable<Esclusioni> {
            EsclusioniScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
