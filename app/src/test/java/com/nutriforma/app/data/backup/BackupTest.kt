package com.nutriforma.app.data.backup

import com.nutriforma.app.data.AppFixture
import com.nutriforma.app.data.MutableClock
import com.nutriforma.app.data.local.TransactionRunner
import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import com.nutriforma.app.data.local.entity.PlannedMealEntity
import com.nutriforma.app.data.local.entity.ShoppingCheckEntity
import com.nutriforma.app.data.local.entity.WaterLogEntity
import com.nutriforma.app.data.local.entity.WeeklyPlanEntity
import com.nutriforma.app.data.repository.ShoppingExtra
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealRole
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.PortionUnit
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate

class BackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = MutableClock(Instant.parse("2026-11-17T08:00:00Z"))
    private lateinit var f: AppFixture

    /** Lunedì della settimana "corrente" per [clock] (17 novembre 2026 è un martedì). */
    private val currentMonday = LocalDate.of(2026, 11, 16)

    private var weekEnsured = 0
    private var remindersSynced = 0

    @Before
    fun setUp() {
        f = AppFixture(tmp.root, clock)
    }

    @After
    fun tearDown() = f.close()

    private fun newService(
        transactions: TransactionRunner = f.transactions,
        ensureCurrentWeek: suspend () -> Unit = {},
        syncReminders: suspend () -> Unit = {},
    ) = BackupService(
        planDao = f.planDao, mealDao = f.mealDao, measurementDao = f.measurementDao, waterDao = f.waterDao,
        checkDao = f.checkDao, transactions = transactions, profileRepository = f.profileRepository,
        completionRepository = f.completionRepository, extrasRepository = f.extrasRepository,
        clock = clock, appVersionName = "1.0.1", ensureCurrentWeek = ensureCurrentWeek, syncReminders = syncReminders,
    )

    /** Servizio che, come nell'app, ricrea la settimana corrente e conta i riallineamenti dei promemoria. */
    private fun appLikeService() = newService(
        ensureCurrentWeek = {
            weekEnsured++
            f.planRepository.getOrCreateWeek(LocalDate.now(clock))
        },
        syncReminders = { remindersSynced++ },
    )

    private suspend fun seedData(weekStartEpochDay: Long = 20774): Long {
        val planId = f.planDao.insert(
            WeeklyPlanEntity(
                weekStartEpochDay = weekStartEpochDay, seed = weekStartEpochDay, goal = Goal.ENERGIA, sportLevel = SportLevel.INTENSO,
                cheatMealsPerWeek = 2, turnoLungoDays = listOf(TurnoLungoDay(2, TurnoLungoOption.BARRETTA, MealSlot.META_MATTINA)),
                createdAtMillis = 10, updatedAtMillis = 20,
            ),
        )
        f.mealDao.insertAll(
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
        f.checkDao.upsert(ShoppingCheckEntity(planId, "petto_pollo", true, 5))
        f.measurementDao.upsert(BodyMeasurementEntity(20774, 78.4, 84.0, null, "ok", 1))
        f.waterDao.insert(WaterLogEntity(dateEpochDay = 20774, timestampMillis = 99, amountMl = 250))
        f.profileRepository.update {
            it.copy(
                goal = Goal.ENERGIA, sportLevel = SportLevel.INTENSO, dislikedFoodIds = setOf("avocado"), cheatMealsPerWeek = 2,
                backupFolderUri = "content://tree/x", autoBackupEnabled = true,
                reminders = it.reminders.copy(mealRemindersEnabled = true, waterIntervalMinutes = 90),
            )
        }
        return planId
    }

    // ---- Esportazione / round-trip ---------------------------------------------------------------------------

    @Test
    fun exportRoundTripsThroughJson() = runBlocking {
        seedData()
        val file = newService().buildBackup()
        assertEquals(1, file.schemaVersion)
        assertEquals("1.0.1", file.appVersionName)
        assertEquals(clock.millis(), file.exportedAtMillis)
        assertEquals(1, file.weeklyPlans.size)
        assertEquals(2, file.weeklyPlans.single().meals.size)
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
    }

    @Test
    fun exportIncludesMealCompletionsAndShoppingExtras() = runBlocking {
        seedData()
        val mealId = f.mealDao.getAll().first().id
        f.completionRepository.setCompleted(mealId, true)
        f.extrasRepository.add("Detersivo")
        f.extrasRepository.setChecked(f.extrasRepository.current().single().id, true)

        val file = newService().buildBackup()

        assertEquals(listOf(mealId), file.completedMealIds)
        assertEquals(listOf("Detersivo"), file.shoppingExtras.map { it.name })
        assertTrue(file.shoppingExtras.single().isChecked)
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
    }

    @Test
    fun completionsAndExtrasSurviveAFullRoundTripOnAnotherDevice() = runBlocking {
        seedData()
        val meals = f.mealDao.getAll().map { it.id }
        f.completionRepository.setCompleted(meals[0], true)
        f.extrasRepository.add("Carta da forno")
        val extraId = f.extrasRepository.current().single().id
        val json = BackupCodec.encode(newService().buildBackup())

        // "Nuovo telefono": stato locale azzerato con valori diversi.
        f.completionRepository.replaceAll(setOf(9999L))
        f.extrasRepository.replaceAll(listOf(ShoppingExtra("altro", "Da scartare")))

        newService().restore(BackupCodec.decode(json))

        assertEquals(setOf(meals[0]), f.completionRepository.current())
        assertEquals(listOf(ShoppingExtra(extraId, "Carta da forno", false)), f.extrasRepository.current())
    }

    // ---- Ripristino ------------------------------------------------------------------------------------------

    @Test
    fun restoreReplacesEverythingKeepingIdsAndDeviceFields() = runBlocking {
        seedData()
        val file = BackupCodec.decode(BackupCodec.encode(newService().buildBackup()))

        // altro stato "sporco" che il ripristino deve sostituire
        f.measurementDao.upsert(BodyMeasurementEntity(1, 50.0, null, null, null, 1))
        f.waterDao.insert(WaterLogEntity(dateEpochDay = 1, timestampMillis = 1, amountMl = 500))
        f.profileRepository.update { it.copy(goal = Goal.DIMAGRIMENTO, dislikedFoodIds = emptySet(), backupFolderUri = "content://tree/nuova") }

        newService().restore(file)

        val plans = f.planDao.getAll()
        assertEquals(listOf(file.weeklyPlans.single().id), plans.map { it.id })
        assertEquals(file.weeklyPlans.single().meals.map { it.id }.toSet(), f.mealDao.getAll().map { it.id }.toSet())
        assertEquals(listOf(20774L), f.measurementDao.getAll().map { it.dateEpochDay })
        assertEquals(listOf(250), f.waterDao.getAll().map { it.amountMl })
        assertEquals(listOf("petto_pollo"), f.checkDao.getAll().map { it.foodId })
        val profile = f.profileRepository.current()
        assertEquals(Goal.ENERGIA, profile.goal)
        assertEquals(setOf("avocado"), profile.dislikedFoodIds)
        assertEquals(90, profile.reminders.waterIntervalMinutes)
        assertEquals("il percorso della cartella è del dispositivo e non si ripristina", "content://tree/nuova", profile.backupFolderUri)
    }

    @Test
    fun restoreDoesNotTriggerTheAutoBackupItself() = runBlocking {
        seedData()
        val file = newService().buildBackup()
        val before = f.changes
        newService().restore(file)
        assertEquals("il ripristino scrive in silenzio: il backup lo pianifica il gestore", before, f.changes)
    }

    @Test
    fun legacyV1BackupWithoutCompletionsAndExtrasRestoresAsEmpty() = runBlocking {
        val legacy = """
        {
          "schemaVersion": 1, "appVersionName": "1.0.0", "exportedAtMillis": 1794000000000,
          "profile": { "goal": "EQUILIBRIO", "sportLevel": "MODERATO" },
          "weeklyPlans": [
            { "id": 3, "weekStartEpochDay": 20773, "seed": 20773, "goal": "EQUILIBRIO", "sportLevel": "MODERATO",
              "cheatMealsPerWeek": 1,
              "meals": [ { "id": 41, "dayIndex": 0, "slot": "PRANZO", "type": "PRANZO_STANDARD", "components": [] } ] }
          ],
          "shoppingChecks": [ { "planId": 3, "foodId": "petto_pollo", "isChecked": true } ]
        }
        """.trimIndent()
        val file = BackupCodec.decode(legacy)
        assertTrue(file.completedMealIds.isEmpty())
        assertTrue(file.shoppingExtras.isEmpty())

        // Stato locale con spunte e extra che il ripristino sostituisce (gli id dei pasti non sono più validi).
        f.completionRepository.setCompleted(41, true)
        f.completionRepository.setCompleted(77, true)
        f.extrasRepository.add("Vecchio extra")

        newService().restore(file)

        assertEquals(emptySet<Long>(), f.completionRepository.current())
        assertTrue(f.extrasRepository.current().isEmpty())
        assertEquals(listOf(3L), f.planDao.getAll().map { it.id })
        assertEquals(SportLevel.MODERATO, f.profileRepository.current().sportLevel)
    }

    @Test
    fun completedMealIdsOfUnknownMealsAreDropped() = runBlocking {
        seedData()
        val real = f.mealDao.getAll().first().id
        val file = newService().buildBackup().copy(completedMealIds = listOf(real, 424242L))

        newService().restore(file)

        assertEquals(setOf(real), f.completionRepository.current())
    }

    @Test
    fun restoreWithoutTheCurrentWeekRecreatesItAndSyncsReminders() = runBlocking {
        f.seedFoods()
        seedData(weekStartEpochDay = currentMonday.minusWeeks(2).toEpochDay())
        val file = appLikeService().buildBackup()
        assertTrue(file.weeklyPlans.none { it.weekStartEpochDay == currentMonday.toEpochDay() })

        val result = appLikeService().restore(file)

        assertTrue(result.warnings.isEmpty())
        assertEquals(1, weekEnsured)
        assertEquals(1, remindersSynced)
        val current = f.planDao.getByWeekStart(currentMonday.toEpochDay())
        assertNotNull("la settimana corrente deve esistere subito dopo il ripristino", current)
        assertEquals(35, f.mealDao.getForPlan(current!!.id).size)
        assertEquals("anche la settimana del backup è stata ripristinata", 2, f.planDao.getAll().size)
        // settimana corrente creata con il profilo ripristinato
        assertEquals(Goal.ENERGIA, current.goal)
    }

    @Test
    fun restoreKeepsTheCurrentWeekFromTheBackupWithoutDuplicatingIt() = runBlocking {
        f.seedFoods()
        seedData(weekStartEpochDay = currentMonday.toEpochDay())
        val file = appLikeService().buildBackup()
        val planId = file.weeklyPlans.single().id

        appLikeService().restore(file)

        assertEquals(listOf(planId), f.planDao.getAll().map { it.id })
        assertEquals(2, f.mealDao.getAll().size)
    }

    @Test
    fun restoreOfAnEmptyBackupStillLeavesAUsableWeek() = runBlocking {
        f.seedFoods()
        seedData()

        appLikeService().restore(BackupFile())

        val weeks = f.planDao.getAll()
        assertEquals(listOf(currentMonday.toEpochDay()), weeks.map { it.weekStartEpochDay })
        assertTrue(f.waterDao.getAll().isEmpty())
    }

    @Test
    fun failingFollowUpStepsAreReportedAsWarningsNotAsFailedRestore() = runBlocking {
        seedData()
        val file = newService().buildBackup()
        val service = newService(
            ensureCurrentWeek = { error("catalogo non pronto") },
            syncReminders = { error("WorkManager non disponibile") },
        )

        val result = service.restore(file)

        assertEquals(2, result.warnings.size)
        assertEquals("i dati sono comunque stati ripristinati", listOf(20774L), f.planDao.getAll().map { it.weekStartEpochDay })
    }

    // ---- Errori e validazione --------------------------------------------------------------------------------

    @Test
    fun inconsistentBackupIsRejectedWithoutTouchingData() = runBlocking {
        seedData()
        f.completionRepository.setCompleted(1, true)
        val good = newService().buildBackup()
        val twin = good.weeklyPlans.single()
        val bad = good.copy(weeklyPlans = listOf(twin, twin))
        assertThrows(BackupException::class.java) { runBlocking { newService().restore(bad) } }
        assertEquals(1, f.planDao.getAll().size)
        assertEquals(setOf(1L), f.completionRepository.current())
    }

    @Test
    fun duplicateShoppingExtraIdsAreRejected() = runBlocking {
        val extra = BackupShoppingExtra("a", "Sale")
        val bad = BackupFile(shoppingExtras = listOf(extra, extra))
        val error = assertThrows(BackupException::class.java) { runBlocking { newService().restore(bad) } }
        assertTrue(error.message!!.contains("incoerenti"))
    }

    @Test
    fun databaseFailureKeepsCurrentDataAndExplainsIt() = runBlocking {
        seedData()
        f.completionRepository.setCompleted(1, true)
        val file = newService().buildBackup()
        val failing = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = throw IllegalStateException("disco pieno")
        }

        val error = assertThrows(BackupException::class.java) { runBlocking { newService(transactions = failing).restore(file) } }

        assertEquals("Ripristino non riuscito: i dati attuali non sono stati modificati.", error.message)
        assertEquals(1, f.planDao.getAll().size)
        assertEquals(setOf(1L), f.completionRepository.current())
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
        assertTrue(file.completedMealIds.isEmpty())
        assertTrue(file.shoppingExtras.isEmpty())
    }

    @Test
    fun newerSchemaAndGarbageAreRejectedInItalian() {
        val newer = assertThrows(BackupException::class.java) { BackupCodec.decode("""{"schemaVersion": 2}""") }
        assertEquals("Backup creato con una versione più recente dell'app.", newer.message)
        val garbage = assertThrows(BackupException::class.java) { BackupCodec.decode("non è json") }
        assertTrue(garbage.message!!.contains("non è un backup"))
        assertFalse(BackupCodec.encode(BackupFile()).isBlank())
    }

    @Test
    fun invalidSchemaVersionsAreRejected() {
        val notValid = "Il file selezionato non è un backup di NutriForma valido."
        listOf(
            """{"schemaVersion": 0}""",
            """{"schemaVersion": -3}""",
            """{"schemaVersion": "1"}""",
            """{"schemaVersion": null}""",
            """{"schemaVersion": 1.5}""",
            """{}""", // senza versione non è un backup: ripristinarlo cancellerebbe tutti i dati
            """[]""",
            """{"profile": {}, "weeklyPlans": []}""",
        ).forEach { text ->
            val error = assertThrows(text, BackupException::class.java) { BackupCodec.decode(text) }
            assertEquals(text, notValid, error.message)
        }
    }

    @Test
    fun aMuchNewerFileIsReportedAsNewerEvenIfItsStructureIsUnknown() {
        val text = """{"schemaVersion": 7, "profile": "formato nuovo", "weeklyPlans": 12}"""
        val error = assertThrows(BackupException::class.java) { BackupCodec.decode(text) }
        assertEquals("Backup creato con una versione più recente dell'app.", error.message)
    }

    @Test
    fun malformedBodyOfAV1FileIsRejected() {
        val error = assertThrows(BackupException::class.java) { BackupCodec.decode("""{"schemaVersion": 1, "weeklyPlans": "no"}""") }
        assertTrue(error.message!!.contains("non è un backup"))
        assertNull(error.message!!.takeIf { it.contains("più recente") })
    }
}
