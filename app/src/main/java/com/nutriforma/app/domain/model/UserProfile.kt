package com.nutriforma.app.domain.model

data class ReminderSettings(
    val mealRemindersEnabled: Boolean = false,
    /** Minutes of day per slot. */
    val mealTimes: Map<MealSlot, Int> = MealSlot.entries.associateWith { it.defaultTimeMinutes },
    val waterRemindersEnabled: Boolean = false,
    val waterIntervalMinutes: Int = 120,
    val waterStartMinutes: Int = 480,
    val waterEndMinutes: Int = 1260,
    val dailyTipEnabled: Boolean = true,
    val dailyTipMinutes: Int = 540,
    val weighInReminderEnabled: Boolean = false,
    /** ISO day of week, 1 = Monday. */
    val weighInDayOfWeek: Int = 1,
    val weighInMinutes: Int = 480,
)

data class UserProfile(
    val onboardingCompleted: Boolean = false,
    val goal: Goal = Goal.EQUILIBRIO,
    val sportLevel: SportLevel = SportLevel.MOLTO_INTENSO,
    /** Vuoto = non indicato. */
    val firstName: String = "",
    /** Vuoto = non indicato. */
    val lastName: String = "",
    val sesso: Sesso? = null,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val dislikedFoodIds: Set<String> = emptySet(),
    /** 1..2 */
    val cheatMealsPerWeek: Int = 1,
    /** dayIndex 0..6 */
    val turnoLungoDefaultDays: Set<Int> = emptySet(),
    val turnoLungoDefaultOption: TurnoLungoOption = TurnoLungoOption.FRUTTA_E_SECCA,
    val turnoLungoDefaultSlot: MealSlot = MealSlot.META_POMERIGGIO,
    /** 1700..4000 */
    val waterGoalMl: Int = 1700,
    val reminders: ReminderSettings = ReminderSettings(),
    /** SAF tree URI. */
    val backupFolderUri: String? = null,
    val autoBackupEnabled: Boolean = false,
    val lastBackupAtMillis: Long? = null,
    val foodSeedVersion: Int = 0,
)
