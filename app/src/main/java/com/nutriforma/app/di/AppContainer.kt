package com.nutriforma.app.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.nutriforma.app.data.backup.BackupManager
import com.nutriforma.app.data.backup.BackupService
import com.nutriforma.app.data.local.NutriFormaDatabase
import com.nutriforma.app.data.local.RoomTransactionRunner
import com.nutriforma.app.data.preferences.UserProfileKeys
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.data.repository.FoodRepository
import com.nutriforma.app.data.repository.MealCompletionRepository
import com.nutriforma.app.data.repository.PlanRepository
import com.nutriforma.app.data.repository.ShoppingExtrasRepository
import com.nutriforma.app.data.repository.ShoppingRepository
import com.nutriforma.app.data.repository.TrackingRepository
import com.nutriforma.app.notifications.ReminderScheduler
import com.nutriforma.app.domain.shopping.ShoppingListGenerator
import com.nutriforma.app.data.seed.FoodSeeder
import com.nutriforma.app.domain.generator.WeeklyPlanGenerator
import com.nutriforma.app.domain.nutrition.EnergyTargetCalculator
import com.nutriforma.app.domain.nutrition.NutritionCalculator
import com.nutriforma.app.domain.nutrition.PortionCalculator
import com.nutriforma.app.domain.rules.DietRuleEngine
import com.nutriforma.app.domain.rules.WeeklyComplianceCalculator
import com.nutriforma.app.domain.swap.MealSwapUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.time.Clock

/**
 * Manual dependency container.
 *
 * Members are only ever added — never remove or rename them.
 */
interface AppContainer {
    /** Process-lifetime scope for fire-and-forget work (seeding, backup). Never cancelled. */
    val applicationScope: CoroutineScope

    /** Dispatcher for Room/DataStore/SAF I/O. Swappable in tests. */
    val ioDispatcher: CoroutineDispatcher

    /** Single shared JSON configuration for assets seeding and backup. */
    val json: Json

    // Foundation
    val database: NutriFormaDatabase
    val userProfileRepository: UserProfileRepository
    val foodRepository: FoodRepository
    val foodSeeder: FoodSeeder

    // Core
    val clock: Clock
    val portionCalculator: PortionCalculator
    val nutritionCalculator: NutritionCalculator
    val energyTargetCalculator: EnergyTargetCalculator
    val dietRuleEngine: DietRuleEngine
    val weeklyPlanGenerator: WeeklyPlanGenerator
    val weeklyComplianceCalculator: WeeklyComplianceCalculator
    val mealSwapUseCase: MealSwapUseCase
    val planRepository: PlanRepository

    // Dieta screen
    val mealCompletionRepository: MealCompletionRepository

    // Meal swap validation is covered by `mealSwapUseCase`
    // Spesa
    val shoppingListGenerator: ShoppingListGenerator
    val shoppingRepository: ShoppingRepository
    val shoppingExtrasRepository: ShoppingExtrasRepository

    // Progressi + promemoria
    val trackingRepository: TrackingRepository
    val reminderScheduler: ReminderScheduler

    // Profilo + backup
    val backupService: BackupService
    val backupManager: BackupManager

}

class DefaultAppContainer(
    private val appContext: Context,
) : AppContainer {

    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override val json: Json = NutriFormaJson

    override val database: NutriFormaDatabase by lazy {
        Room.databaseBuilder(appContext, NutriFormaDatabase::class.java, NutriFormaDatabase.NAME).build()
    }

    override val userProfileRepository: UserProfileRepository by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { appContext.preferencesDataStoreFile(UserProfileKeys.DATASTORE_NAME) },
        )
        UserProfileRepository(dataStore)
    }

    override val foodRepository: FoodRepository by lazy { FoodRepository(database.foodDao()) }

    override val foodSeeder: FoodSeeder by lazy {
        FoodSeeder(
            foodDao = database.foodDao(),
            userProfileRepository = userProfileRepository,
            json = json,
            ioDispatcher = ioDispatcher,
            openSeedFile = { appContext.assets.open(FOOD_SEED_ASSET) },
        )
    }

    override val clock: Clock = Clock.systemDefaultZone()

    override val portionCalculator: PortionCalculator = PortionCalculator()
    override val nutritionCalculator: NutritionCalculator = NutritionCalculator()
    override val energyTargetCalculator: EnergyTargetCalculator = EnergyTargetCalculator()
    override val dietRuleEngine: DietRuleEngine = DietRuleEngine()
    override val weeklyPlanGenerator: WeeklyPlanGenerator = WeeklyPlanGenerator(portionCalculator, dietRuleEngine)
    override val weeklyComplianceCalculator: WeeklyComplianceCalculator =
        WeeklyComplianceCalculator(dietRuleEngine, nutritionCalculator)
    override val mealSwapUseCase: MealSwapUseCase =
        MealSwapUseCase(weeklyPlanGenerator, dietRuleEngine, portionCalculator, nutritionCalculator)

    override val planRepository: PlanRepository by lazy {
        PlanRepository(
            planDao = database.weeklyPlanDao(),
            mealDao = database.plannedMealDao(),
            shoppingCheckDao = database.shoppingCheckDao(),
            transactions = RoomTransactionRunner(database),
            foodRepository = foodRepository,
            userProfileRepository = userProfileRepository,
            generator = weeklyPlanGenerator,
            mealSwapUseCase = mealSwapUseCase,
            portionCalculator = portionCalculator,
            clock = clock,
            ioDispatcher = ioDispatcher,
            onDataChanged = { backupManager.scheduleAutoBackup() },
        )
    }

    override val mealCompletionRepository: MealCompletionRepository by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { appContext.preferencesDataStoreFile(MealCompletionRepository.DATASTORE_NAME) },
        )
        MealCompletionRepository(dataStore)
    }

    override val shoppingListGenerator: ShoppingListGenerator = ShoppingListGenerator()

    override val shoppingRepository: ShoppingRepository by lazy {
        ShoppingRepository(database.shoppingCheckDao(), clock, ioDispatcher)
    }

    override val shoppingExtrasRepository: ShoppingExtrasRepository by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { appContext.preferencesDataStoreFile(ShoppingExtrasRepository.DATASTORE_NAME) },
        )
        ShoppingExtrasRepository(dataStore)
    }

    override val trackingRepository: TrackingRepository by lazy {
        TrackingRepository(
            database.waterLogDao(), database.bodyMeasurementDao(), clock, ioDispatcher,
            onDataChanged = { backupManager.scheduleAutoBackup() },
        )
    }

    override val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(appContext, userProfileRepository, clock)
    }

    override val backupService: BackupService by lazy {
        BackupService(
            planDao = database.weeklyPlanDao(),
            mealDao = database.plannedMealDao(),
            measurementDao = database.bodyMeasurementDao(),
            waterDao = database.waterLogDao(),
            checkDao = database.shoppingCheckDao(),
            transactions = RoomTransactionRunner(database),
            profileRepository = userProfileRepository,
            clock = clock,
            appVersionName = runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            }.getOrNull() ?: "",
        )
    }

    override val backupManager: BackupManager by lazy {
        BackupManager(appContext, backupService, userProfileRepository, reminderScheduler, applicationScope, clock, ioDispatcher)
    }

    private companion object {
        const val FOOD_SEED_ASSET = "foods_it.json"
    }
}

/** Locked JSON config. Backups must stay readable across app versions → lenient on unknown keys. */
val NutriFormaJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = true
    isLenient = false
}
