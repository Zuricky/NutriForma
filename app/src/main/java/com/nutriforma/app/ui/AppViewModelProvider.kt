package com.nutriforma.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.nutriforma.app.NutriFormaApplication
import com.nutriforma.app.ui.dieta.DietaViewModel
import com.nutriforma.app.ui.impostazioni.ImpostazioniViewModel
import com.nutriforma.app.ui.profilo.EsclusioniViewModel
import com.nutriforma.app.ui.profilo.ProfiloViewModel
import com.nutriforma.app.ui.progressi.ProgressiViewModel
import com.nutriforma.app.ui.spesa.SpesaViewModel

/** The only way ViewModels are created (manual DI, no Hilt). */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val container = nutriFormaApplication().container
            DietaViewModel(
                planRepository = container.planRepository,
                foodRepository = container.foodRepository,
                userProfileRepository = container.userProfileRepository,
                completionRepository = container.mealCompletionRepository,
                complianceCalculator = container.weeklyComplianceCalculator,
                nutritionCalculator = container.nutritionCalculator,
                clock = container.clock,
                savedStateHandle = createSavedStateHandle(),
            )
        }
        initializer {
            val container = nutriFormaApplication().container
            SpesaViewModel(
                planRepository = container.planRepository,
                foodRepository = container.foodRepository,
                shoppingRepository = container.shoppingRepository,
                extrasRepository = container.shoppingExtrasRepository,
                generator = container.shoppingListGenerator,
                clock = container.clock,
            )
        }
        initializer {
            val container = nutriFormaApplication().container
            ProgressiViewModel(
                trackingRepository = container.trackingRepository,
                userProfileRepository = container.userProfileRepository,
                clock = container.clock,
            )
        }
        initializer {
            val container = nutriFormaApplication().container
            ProfiloViewModel(
                userProfileRepository = container.userProfileRepository,
                planRepository = container.planRepository,
                trackingRepository = container.trackingRepository,
                energyTargetCalculator = container.energyTargetCalculator,
                restoreCount = container.backupManager.restoreCount,
                clock = container.clock,
            )
        }
        initializer {
            val container = nutriFormaApplication().container
            ImpostazioniViewModel(
                userProfileRepository = container.userProfileRepository,
                reminderSync = container.reminderScheduler,
                backup = container.backupManager,
                clock = container.clock,
            )
        }
        initializer {
            val container = nutriFormaApplication().container
            EsclusioniViewModel(
                userProfileRepository = container.userProfileRepository,
                foodRepository = container.foodRepository,
                planRepository = container.planRepository,
                appScope = container.applicationScope,
                clock = container.clock,
            )
        }
    }
}

private fun CreationExtras.nutriFormaApplication(): NutriFormaApplication =
    this[APPLICATION_KEY] as NutriFormaApplication
