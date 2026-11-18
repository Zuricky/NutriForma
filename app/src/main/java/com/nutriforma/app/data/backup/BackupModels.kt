package com.nutriforma.app.data.backup

import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import com.nutriforma.app.data.local.entity.PlannedMealEntity
import com.nutriforma.app.data.local.entity.ShoppingCheckEntity
import com.nutriforma.app.data.local.entity.WaterLogEntity
import com.nutriforma.app.data.local.entity.WeeklyPlanEntity
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.MealType
import com.nutriforma.app.domain.model.PlannedComponent
import com.nutriforma.app.domain.model.ReminderSettings
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.TurnoLungoDay
import com.nutriforma.app.domain.model.TurnoLungoOption
import com.nutriforma.app.domain.model.UserProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Schema del file di backup. */
const val BACKUP_SCHEMA_VERSION = 1

@Serializable
data class BackupFile(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val appVersionName: String = "",
    val exportedAtMillis: Long = 0,
    val profile: BackupProfile = BackupProfile(),
    val weeklyPlans: List<BackupWeeklyPlan> = emptyList(),
    val bodyMeasurements: List<BackupBodyMeasurement> = emptyList(),
    val waterLogs: List<BackupWaterLog> = emptyList(),
    val shoppingChecks: List<BackupShoppingCheck> = emptyList(),
)

@Serializable
data class BackupReminders(
    val mealRemindersEnabled: Boolean = false,
    val mealTimes: Map<MealSlot, Int> = emptyMap(),
    val waterRemindersEnabled: Boolean = false,
    val waterIntervalMinutes: Int = 120,
    val waterStartMinutes: Int = 480,
    val waterEndMinutes: Int = 1260,
    val dailyTipEnabled: Boolean = true,
    val dailyTipMinutes: Int = 540,
    val weighInReminderEnabled: Boolean = false,
    val weighInDayOfWeek: Int = 1,
    val weighInMinutes: Int = 480,
)

@Serializable
data class BackupProfile(
    val onboardingCompleted: Boolean = false,
    val goal: Goal = Goal.EQUILIBRIO,
    val sportLevel: SportLevel = SportLevel.MOLTO_INTENSO,
    val sesso: Sesso? = null,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val dislikedFoodIds: Set<String> = emptySet(),
    val cheatMealsPerWeek: Int = 1,
    val turnoLungoDefaultDays: Set<Int> = emptySet(),
    val turnoLungoDefaultOption: TurnoLungoOption = TurnoLungoOption.FRUTTA_E_SECCA,
    val turnoLungoDefaultSlot: MealSlot = MealSlot.META_POMERIGGIO,
    val waterGoalMl: Int = 1700,
    val reminders: BackupReminders = BackupReminders(),
    val autoBackupEnabled: Boolean = false,
)

@Serializable
data class BackupMeal(
    val id: Long,
    val dayIndex: Int,
    val slot: MealSlot,
    val type: MealType,
    val isUserModified: Boolean = false,
    val components: List<PlannedComponent> = emptyList(),
)

@Serializable
data class BackupWeeklyPlan(
    val id: Long,
    val weekStartEpochDay: Long,
    val seed: Long,
    val goal: Goal,
    val sportLevel: SportLevel,
    val cheatMealsPerWeek: Int,
    val turnoLungoDays: List<TurnoLungoDay> = emptyList(),
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val meals: List<BackupMeal> = emptyList(),
)

@Serializable
data class BackupBodyMeasurement(
    val dateEpochDay: Long,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val thighCm: Double? = null,
    val note: String? = null,
    val updatedAtMillis: Long = 0,
)

@Serializable
data class BackupWaterLog(val id: Long, val dateEpochDay: Long, val timestampMillis: Long, val amountMl: Int)

@Serializable
data class BackupShoppingCheck(val planId: Long, val foodId: String, val isChecked: Boolean, val updatedAtMillis: Long = 0)

/** Errore con messaggio già in italiano, mostrabile all'utente. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Legge e valida un backup; lancia [BackupException] con messaggio italiano se non è utilizzabile. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("Il file selezionato non è un backup di NutriForma valido.", e)
        }
        if (file.schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw BackupException("Backup creato con una versione più recente dell'app.")
        }
        if (file.schemaVersion < 1) throw BackupException("Il file selezionato non è un backup di NutriForma valido.")
        return file
    }
}

// ---- Mapping: dominio / entità ⇄ DTO -------------------------------------------------------------------------

fun UserProfile.toBackup() = BackupProfile(
    onboardingCompleted = onboardingCompleted, goal = goal, sportLevel = sportLevel, sesso = sesso,
    birthYear = birthYear, heightCm = heightCm, dislikedFoodIds = dislikedFoodIds, cheatMealsPerWeek = cheatMealsPerWeek,
    turnoLungoDefaultDays = turnoLungoDefaultDays, turnoLungoDefaultOption = turnoLungoDefaultOption,
    turnoLungoDefaultSlot = turnoLungoDefaultSlot, waterGoalMl = waterGoalMl,
    reminders = BackupReminders(
        mealRemindersEnabled = reminders.mealRemindersEnabled, mealTimes = reminders.mealTimes,
        waterRemindersEnabled = reminders.waterRemindersEnabled, waterIntervalMinutes = reminders.waterIntervalMinutes,
        waterStartMinutes = reminders.waterStartMinutes, waterEndMinutes = reminders.waterEndMinutes,
        dailyTipEnabled = reminders.dailyTipEnabled, dailyTipMinutes = reminders.dailyTipMinutes,
        weighInReminderEnabled = reminders.weighInReminderEnabled, weighInDayOfWeek = reminders.weighInDayOfWeek,
        weighInMinutes = reminders.weighInMinutes,
    ),
    autoBackupEnabled = autoBackupEnabled,
)

/**
 * Riporta il backup sul profilo. I campi legati al dispositivo ([UserProfile.backupFolderUri],
 * [UserProfile.lastBackupAtMillis], [UserProfile.foodSeedVersion]) restano quelli di [current]; il backup
 * automatico resta attivo solo se esiste ancora una cartella scelta.
 */
fun BackupProfile.toProfile(current: UserProfile): UserProfile {
    val defaults = ReminderSettings()
    return current.copy(
        onboardingCompleted = onboardingCompleted, goal = goal, sportLevel = sportLevel, sesso = sesso,
        birthYear = birthYear, heightCm = heightCm, dislikedFoodIds = dislikedFoodIds,
        cheatMealsPerWeek = cheatMealsPerWeek.coerceIn(1, 2), turnoLungoDefaultDays = turnoLungoDefaultDays,
        turnoLungoDefaultOption = turnoLungoDefaultOption, turnoLungoDefaultSlot = turnoLungoDefaultSlot,
        waterGoalMl = waterGoalMl.coerceIn(1700, 4000),
        reminders = ReminderSettings(
            mealRemindersEnabled = reminders.mealRemindersEnabled,
            mealTimes = defaults.mealTimes + reminders.mealTimes,
            waterRemindersEnabled = reminders.waterRemindersEnabled, waterIntervalMinutes = reminders.waterIntervalMinutes,
            waterStartMinutes = reminders.waterStartMinutes, waterEndMinutes = reminders.waterEndMinutes,
            dailyTipEnabled = reminders.dailyTipEnabled, dailyTipMinutes = reminders.dailyTipMinutes,
            weighInReminderEnabled = reminders.weighInReminderEnabled, weighInDayOfWeek = reminders.weighInDayOfWeek,
            weighInMinutes = reminders.weighInMinutes,
        ),
        autoBackupEnabled = autoBackupEnabled && current.backupFolderUri != null,
    )
}

fun WeeklyPlanEntity.toBackup(meals: List<PlannedMealEntity>) = BackupWeeklyPlan(
    id = id, weekStartEpochDay = weekStartEpochDay, seed = seed, goal = goal, sportLevel = sportLevel,
    cheatMealsPerWeek = cheatMealsPerWeek, turnoLungoDays = turnoLungoDays,
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
    meals = meals.sortedWith(compareBy({ it.dayIndex }, { it.slot.order })).map {
        BackupMeal(it.id, it.dayIndex, it.slot, it.type, it.isUserModified, it.components)
    },
)

fun BackupWeeklyPlan.toEntity() = WeeklyPlanEntity(
    id = id, weekStartEpochDay = weekStartEpochDay, seed = seed, goal = goal, sportLevel = sportLevel,
    cheatMealsPerWeek = cheatMealsPerWeek, turnoLungoDays = turnoLungoDays,
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

fun BackupWeeklyPlan.mealEntities(): List<PlannedMealEntity> = meals.map {
    PlannedMealEntity(
        id = it.id, planId = id, dayIndex = it.dayIndex, slot = it.slot, type = it.type,
        components = it.components, isUserModified = it.isUserModified,
    )
}

fun BodyMeasurementEntity.toBackup() = BackupBodyMeasurement(dateEpochDay, weightKg, waistCm, thighCm, note, updatedAtMillis)
fun BackupBodyMeasurement.toEntity() = BodyMeasurementEntity(dateEpochDay, weightKg, waistCm, thighCm, note, updatedAtMillis)

fun WaterLogEntity.toBackup() = BackupWaterLog(id, dateEpochDay, timestampMillis, amountMl)
fun BackupWaterLog.toEntity() = WaterLogEntity(id, dateEpochDay, timestampMillis, amountMl)

fun ShoppingCheckEntity.toBackup() = BackupShoppingCheck(planId, foodId, isChecked, updatedAtMillis)
fun BackupShoppingCheck.toEntity() = ShoppingCheckEntity(planId, foodId, isChecked, updatedAtMillis)
