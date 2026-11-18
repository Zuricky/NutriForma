package com.nutriforma.app.data.backup

import com.nutriforma.app.data.CountingTransactionRunner
import com.nutriforma.app.data.FakeBodyMeasurementDao
import com.nutriforma.app.data.FakePlannedMealDao
import com.nutriforma.app.data.FakeShoppingCheckDao
import com.nutriforma.app.data.FakeWaterLogDao
import com.nutriforma.app.data.FakeWeeklyPlanDao
import com.nutriforma.app.data.TestDataStore
import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import com.nutriforma.app.data.local.entity.ShoppingCheckEntity
import com.nutriforma.app.data.local.entity.WaterLogEntity
import com.nutriforma.app.data.local.entity.WeeklyPlanEntity
import com.nutriforma.app.data.local.entity.PlannedMealEntity
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: TestDataStore
    private lateinit var profileRepository: UserProfileRepository
    private val planDao = FakeWeeklyPlanDao()
    private val mealDao = FakePlannedMealDao()
    private val measurementDao = FakeBodyMeasurementDao()
    private val waterDao = FakeWaterLogDao()
    private val checkDao = FakeShoppingCheckDao()
    private val clock = Clock.fixed(Instant.parse("2026-11-17T08:00:00Z"), ZoneOffset.UTC)

    private lateinit var service: BackupService

    @Before
    fun setUp() {
        store = TestDataStore(tmp.root)
        profileRepository = UserProfileRepository(store.dataStore)
        service = BackupService(
            planDao, mealDao, measurementDao, waterDao, checkDao, CountingTransactionRunner(), profileRepository, clock, "1.0.0",
        )
    }

    @After
    fun tearDown() {
        store.scope.cancel()
    }

    private suspend fun seedData() {
        val planId = planDao.insert(
            WeeklyPlanEntity(
                weekStartEpochDay = 20774, seed = 20774, goal = Goal.ENERGIA, sportLevel = SportLevel.INTENSO,
                cheatMealsPerWeek = 2, turnoLungoDays = listOf(TurnoLungoDay(2, TurnoLungoOption.BARRETTA, MealSlot.META_MATTINA)),
                createdAtMillis = 10, updatedAtMillis = 20,
            ),
        )
        mealDao.insertAll(
            listOf(
                PlannedMealEntity(
                    planId = planId, dayIndex = 0, slot = MealSlot.PRANZO, type = MealType.PRANZO_STANDARD,
                    components = listOf(PlannedComponent(MealRole.PRANZO_SECONDO, "petto_pollo", 155.0, PortionUnit.GRAMMI, 155.0)),
                    isUserModified = true,
                ),
                PlannedMealEntity(
                    planId = planId, dayIndex = 0, slot = MealSlot.CENA, type = MealType.CENA_SECONDO,
                    components = listOf(PlannedComponent(MealRole.CENA_SECONDO, "uovo", 2.0, PortionUnit.PEZZI, 110.0)),
                ),
            ),
        )
        checkDao.upsert(ShoppingCheckEntity(planId, "petto_pollo", true, 5))
        measurementDao.upsert(BodyMeasurementEntity(20774, 78.4, 84.0, null, "ok", 1))
        waterDao.insert(WaterLogEntity(dateEpochDay = 20774, timestampMillis = 99, amountMl = 250))
        profileRepository.update {
            it.copy(
                goal = Goal.ENERGIA, sportLevel = SportLevel.INTENSO, dislikedFoodIds = setOf("avocado"), cheatMealsPerWeek = 2,
                backupFolderUri = "content://tree/x", autoBackupEnabled = true,
                reminders = it.reminders.copy(mealRemindersEnabled = true, waterIntervalMinutes = 90),
            )
        }
    }

    @Test
    fun exportRoundTripsThroughJson() = runBlocking {
        seedData()
        val file = service.buildBackup()
        assertEquals(1, file.schemaVersion)
        assertEquals("1.0.0", file.appVersionName)
        assertEquals(clock.millis(), file.exportedAtMillis)
        assertEquals(1, file.weeklyPlans.size)
        assertEquals(2, file.weeklyPlans.single().meals.size)
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
    }

    @Test
    fun restoreReplacesEverythingKeepingIdsAndDeviceFields() = runBlocking {
        seedData()
        val file = BackupCodec.decode(BackupCodec.encode(service.buildBackup()))

        // altro stato "sporco" che il ripristino deve sostituire
        measurementDao.upsert(BodyMeasurementEntity(1, 50.0, null, null, null, 1))
        waterDao.insert(WaterLogEntity(dateEpochDay = 1, timestampMillis = 1, amountMl = 500))
        profileRepository.update { it.copy(goal = Goal.DIMAGRIMENTO, dislikedFoodIds = emptySet(), backupFolderUri = "content://tree/nuova") }

        service.restore(file)

        val plans = planDao.getAll()
        assertEquals(listOf(file.weeklyPlans.single().id), plans.map { it.id })
        assertEquals(file.weeklyPlans.single().meals.map { it.id }.toSet(), mealDao.getAll().map { it.id }.toSet())
        assertEquals(listOf(20774L), measurementDao.getAll().map { it.dateEpochDay })
        assertEquals(listOf(250), waterDao.getAll().map { it.amountMl })
        assertEquals(listOf("petto_pollo"), checkDao.getAll().map { it.foodId })
        val profile = profileRepository.current()
        assertEquals(Goal.ENERGIA, profile.goal)
        assertEquals(setOf("avocado"), profile.dislikedFoodIds)
        assertEquals(90, profile.reminders.waterIntervalMinutes)
        assertEquals("il percorso della cartella è del dispositivo e non si ripristina", "content://tree/nuova", profile.backupFolderUri)
    }

    @Test
    fun inconsistentBackupIsRejectedWithoutTouchingData() = runBlocking {
        seedData()
        val good = service.buildBackup()
        val twin = good.weeklyPlans.single()
        val bad = good.copy(weeklyPlans = listOf(twin, twin))
        assertThrows(BackupException::class.java) { runBlocking { service.restore(bad) } }
        assertEquals(1, planDao.getAll().size)
    }

    @Test
    fun fixtureFromThePlanParsesAndUnknownKeysAreIgnored() {
        val json = """
        {
          "schemaVersion": 1, "appVersionName": "1.0", "exportedAtMillis": 1794000000000, "futuro": {"x": 1},
          "profile": {
            "onboardingCompleted": true, "goal": "EQUILIBRIO", "sportLevel": "MOLTO_INTENSO", "sesso": "MASCHIO",
            "birthYear": 1990, "heightCm": 178, "dislikedFoodIds": ["salmone_affumicato"], "cheatMealsPerWeek": 1,
            "turnoLungoDefaultDays": [2], "turnoLungoDefaultOption": "FRUTTA_E_SECCA", "turnoLungoDefaultSlot": "META_POMERIGGIO",
            "waterGoalMl": 1700,
            "reminders": { "mealRemindersEnabled": true, "mealTimes": {"COLAZIONE": 450, "META_MATTINA": 630, "PRANZO": 840, "META_POMERIGGIO": 1050, "CENA": 1230},
                           "waterRemindersEnabled": true, "waterIntervalMinutes": 120, "waterStartMinutes": 480, "waterEndMinutes": 1260,
                           "dailyTipEnabled": true, "dailyTipMinutes": 540, "weighInReminderEnabled": false, "weighInDayOfWeek": 1, "weighInMinutes": 480 },
            "autoBackupEnabled": true
          },
          "weeklyPlans": [
            { "id": 3, "weekStartEpochDay": 20774, "seed": 20774, "goal": "EQUILIBRIO", "sportLevel": "MOLTO_INTENSO",
              "cheatMealsPerWeek": 1, "turnoLungoDays": [{"dayIndex": 2, "option": "FRUTTA_E_SECCA", "slot": "META_POMERIGGIO"}],
              "createdAtMillis": 1794000000000, "updatedAtMillis": 1794000000000,
              "meals": [ { "id": 41, "dayIndex": 0, "slot": "PRANZO", "type": "PRANZO_STANDARD", "isUserModified": false,
                           "components": [ {"role": "PRANZO_SECONDO", "foodId": "petto_pollo", "quantity": 155.0, "unit": "GRAMMI", "grams": 155.0, "isOptional": false} ] } ] }
          ],
          "bodyMeasurements": [ { "dateEpochDay": 20774, "weightKg": 78.4, "waistCm": 84.0, "thighCm": 56.5, "note": null, "updatedAtMillis": 1794000000000 } ],
          "waterLogs": [ { "id": 7, "dateEpochDay": 20774, "timestampMillis": 1794000000000, "amountMl": 250 } ],
          "shoppingChecks": [ { "planId": 3, "foodId": "petto_pollo", "isChecked": true, "updatedAtMillis": 1794000000000 } ]
        }
        """.trimIndent()
        val file = BackupCodec.decode(json)
        assertEquals(3L, file.weeklyPlans.single().id)
        assertEquals("petto_pollo", file.weeklyPlans.single().meals.single().components.single().foodId)
        assertEquals(setOf("salmone_affumicato"), file.profile.dislikedFoodIds)
        assertEquals(1050, file.profile.reminders.mealTimes[MealSlot.META_POMERIGGIO])
        assertEquals(56.5, file.bodyMeasurements.single().thighCm!!, 0.0)
    }

    @Test
    fun newerSchemaAndGarbageAreRejectedInItalian() {
        val newer = assertThrows(BackupException::class.java) { BackupCodec.decode("""{"schemaVersion": 2}""") }
        assertEquals("Backup creato con una versione più recente dell'app.", newer.message)
        val garbage = assertThrows(BackupException::class.java) { BackupCodec.decode("non è json") }
        assertTrue(garbage.message!!.contains("non è un backup"))
        assertFalse(BackupCodec.encode(BackupFile()).isBlank())
    }
}
