package com.nutriforma.app.data

import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import com.nutriforma.app.data.mapper.toEntity
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.data.repository.FoodRepository
import com.nutriforma.app.data.repository.MealCompletionRepository
import com.nutriforma.app.data.repository.PlanRepository
import com.nutriforma.app.data.repository.ShoppingExtrasRepository
import com.nutriforma.app.data.repository.ShoppingRepository
import com.nutriforma.app.data.repository.TrackingRepository
import com.nutriforma.app.domain.TestFoods
import com.nutriforma.app.domain.generator.WeeklyPlanGenerator
import com.nutriforma.app.domain.nutrition.PortionCalculator
import com.nutriforma.app.domain.swap.MealSwapUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

/** Orologio di test con istante modificabile (per simulare il passaggio della mezzanotte). */
class MutableClock(var current: Instant, private val zoneId: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
    override fun instant(): Instant = current
}

/**
 * Repository reali su DAO finti e DataStore su file temporanei (niente Android, niente Mockito).
 * Ogni modifica dell'utente incrementa [changes], come farebbe il backup automatico.
 */
class AppFixture(dir: File, val clock: MutableClock) {
    private val profileStore = TestDataStore(dir, "profile_fixture")
    private val completionStore = TestDataStore(dir, "completion_fixture")
    private val extrasStore = TestDataStore(dir, "extras_fixture")

    private val changeCounter = AtomicInteger()

    /** Quante volte è scattata la notifica "dati cambiati" (backup automatico). */
    val changes: Int get() = changeCounter.get()
    private val onChanged: () -> Unit = { changeCounter.incrementAndGet() }

    val planDao = FakeWeeklyPlanDao()
    val mealDao = FakePlannedMealDao()
    val checkDao = FakeShoppingCheckDao()
    val foodDao = FakeFoodDao()
    val waterDao = FakeWaterLogDao()
    val measurementDao = FakeBodyMeasurementDao()
    val transactions = CountingTransactionRunner()

    val profileRepository = UserProfileRepository(profileStore.dataStore, onChanged)
    val completionRepository = MealCompletionRepository(completionStore.dataStore, onChanged)
    val extrasRepository = ShoppingExtrasRepository(extrasStore.dataStore, onChanged)
    val foodRepository = FoodRepository(foodDao)
    val shoppingRepository = ShoppingRepository(checkDao, clock, Dispatchers.Unconfined, onChanged)
    val trackingRepository = TrackingRepository(waterDao, measurementDao, clock, Dispatchers.Unconfined, onChanged)
    val planRepository = PlanRepository(
        planDao = planDao, mealDao = mealDao, shoppingCheckDao = checkDao, transactions = transactions,
        foodRepository = foodRepository, userProfileRepository = profileRepository,
        generator = WeeklyPlanGenerator(), mealSwapUseCase = MealSwapUseCase(), portionCalculator = PortionCalculator(),
        clock = clock, ioDispatcher = Dispatchers.Unconfined, onDataChanged = onChanged,
    )

    suspend fun seedFoods() {
        foodDao.upsertAll(TestFoods.all.mapIndexed { i, f -> f.toEntity(i) })
    }

    suspend fun addWeight(epochDay: Long, kg: Double) {
        measurementDao.upsert(BodyMeasurementEntity(epochDay, kg, null, null, null, 1))
    }

    fun close() {
        profileStore.scope.cancel()
        completionStore.scope.cancel()
        extrasStore.scope.cancel()
    }
}
