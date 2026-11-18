package com.nutriforma.app.data.preferences

import com.nutriforma.app.data.preferences.UserProfileKeys as K
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.ReminderSettings
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.UserProfile

/**
 * Pure mapping between [UserProfile] and the flat `name → value` view of the DataStore preferences.
 *
 * Values are only ever `Boolean`, `Int`, `Long`, `String`, `Set<String>` or `null` (null = key absent).
 * Missing, mistyped or invalid values fall back to the [UserProfile] defaults; ranges are clamped.
 */
object UserProfileMapper {

    fun fromMap(map: Map<String, Any?>): UserProfile {
        val d = UserProfile()
        val dr = d.reminders
        return UserProfile(
            onboardingCompleted = map.bool(K.ONBOARDING_COMPLETED, d.onboardingCompleted),
            goal = map.enum(K.GOAL, d.goal),
            sportLevel = map.enum(K.SPORT_LEVEL, d.sportLevel),
            sesso = map.enumOrNull<Sesso>(K.SESSO),
            birthYear = map.int(K.BIRTH_YEAR),
            heightCm = map.int(K.HEIGHT_CM),
            dislikedFoodIds = map.stringSet(K.DISLIKED_FOOD_IDS) ?: d.dislikedFoodIds,
            cheatMealsPerWeek = (map.int(K.CHEAT_MEALS_PER_WEEK) ?: d.cheatMealsPerWeek)
                .coerceIn(K.MIN_CHEAT_MEALS, K.MAX_CHEAT_MEALS),
            turnoLungoDefaultDays = map.stringSet(K.TURNO_LUNGO_DEFAULT_DAYS)
                ?.mapNotNull { it.toIntOrNull() }?.filter { it in 0..6 }?.toSet()
                ?: d.turnoLungoDefaultDays,
            turnoLungoDefaultOption = map.enum(K.TURNO_LUNGO_DEFAULT_OPTION, d.turnoLungoDefaultOption),
            turnoLungoDefaultSlot = map.enum(K.TURNO_LUNGO_DEFAULT_SLOT, d.turnoLungoDefaultSlot),
            waterGoalMl = (map.int(K.WATER_GOAL_ML) ?: d.waterGoalMl)
                .coerceIn(K.MIN_WATER_GOAL_ML, K.MAX_WATER_GOAL_ML),
            reminders = ReminderSettings(
                mealRemindersEnabled = map.bool(K.REMINDER_MEALS_ENABLED, dr.mealRemindersEnabled),
                mealTimes = MealSlot.entries.associateWith { slot ->
                    map.int(K.mealTime(slot)) ?: dr.mealTimes.getValue(slot)
                },
                waterRemindersEnabled = map.bool(K.REMINDER_WATER_ENABLED, dr.waterRemindersEnabled),
                waterIntervalMinutes = map.int(K.REMINDER_WATER_INTERVAL) ?: dr.waterIntervalMinutes,
                waterStartMinutes = map.int(K.REMINDER_WATER_START) ?: dr.waterStartMinutes,
                waterEndMinutes = map.int(K.REMINDER_WATER_END) ?: dr.waterEndMinutes,
                dailyTipEnabled = map.bool(K.REMINDER_TIP_ENABLED, dr.dailyTipEnabled),
                dailyTipMinutes = map.int(K.REMINDER_TIP_TIME) ?: dr.dailyTipMinutes,
                weighInReminderEnabled = map.bool(K.REMINDER_WEIGHIN_ENABLED, dr.weighInReminderEnabled),
                weighInDayOfWeek = map.int(K.REMINDER_WEIGHIN_DAY) ?: dr.weighInDayOfWeek,
                weighInMinutes = map.int(K.REMINDER_WEIGHIN_TIME) ?: dr.weighInMinutes,
            ),
            backupFolderUri = map[K.BACKUP_FOLDER_URI] as? String,
            autoBackupEnabled = map.bool(K.AUTO_BACKUP_ENABLED, d.autoBackupEnabled),
            lastBackupAtMillis = map[K.LAST_BACKUP_AT] as? Long,
            foodSeedVersion = map.int(K.FOOD_SEED_VERSION) ?: d.foodSeedVersion,
        )
    }

    /** Every key is present in the result; optional values that are absent map to `null` (= remove the key). */
    fun toMap(profile: UserProfile): Map<String, Any?> {
        val r = profile.reminders
        val map = linkedMapOf<String, Any?>(
            K.ONBOARDING_COMPLETED to profile.onboardingCompleted,
            K.GOAL to profile.goal.name,
            K.SPORT_LEVEL to profile.sportLevel.name,
            K.SESSO to profile.sesso?.name,
            K.BIRTH_YEAR to profile.birthYear,
            K.HEIGHT_CM to profile.heightCm,
            K.DISLIKED_FOOD_IDS to profile.dislikedFoodIds,
            K.CHEAT_MEALS_PER_WEEK to profile.cheatMealsPerWeek.coerceIn(K.MIN_CHEAT_MEALS, K.MAX_CHEAT_MEALS),
            K.TURNO_LUNGO_DEFAULT_DAYS to profile.turnoLungoDefaultDays.map { it.toString() }.toSet(),
            K.TURNO_LUNGO_DEFAULT_OPTION to profile.turnoLungoDefaultOption.name,
            K.TURNO_LUNGO_DEFAULT_SLOT to profile.turnoLungoDefaultSlot.name,
            K.WATER_GOAL_ML to profile.waterGoalMl.coerceIn(K.MIN_WATER_GOAL_ML, K.MAX_WATER_GOAL_ML),
            K.REMINDER_MEALS_ENABLED to r.mealRemindersEnabled,
            K.REMINDER_WATER_ENABLED to r.waterRemindersEnabled,
            K.REMINDER_WATER_INTERVAL to r.waterIntervalMinutes,
            K.REMINDER_WATER_START to r.waterStartMinutes,
            K.REMINDER_WATER_END to r.waterEndMinutes,
            K.REMINDER_TIP_ENABLED to r.dailyTipEnabled,
            K.REMINDER_TIP_TIME to r.dailyTipMinutes,
            K.REMINDER_WEIGHIN_ENABLED to r.weighInReminderEnabled,
            K.REMINDER_WEIGHIN_DAY to r.weighInDayOfWeek,
            K.REMINDER_WEIGHIN_TIME to r.weighInMinutes,
            K.BACKUP_FOLDER_URI to profile.backupFolderUri,
            K.AUTO_BACKUP_ENABLED to profile.autoBackupEnabled,
            K.LAST_BACKUP_AT to profile.lastBackupAtMillis,
            K.FOOD_SEED_VERSION to profile.foodSeedVersion,
        )
        MealSlot.entries.forEach { slot ->
            map[K.mealTime(slot)] = r.mealTimes[slot] ?: slot.defaultTimeMinutes
        }
        return map
    }

    private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean = this[key] as? Boolean ?: default

    private fun Map<String, Any?>.int(key: String): Int? = this[key] as? Int

    private fun Map<String, Any?>.stringSet(key: String): Set<String>? =
        (this[key] as? Set<*>)?.filterIsInstance<String>()?.toSet()

    private inline fun <reified E : Enum<E>> Map<String, Any?>.enumOrNull(key: String): E? {
        val name = this[key] as? String ?: return null
        return enumValues<E>().firstOrNull { it.name == name }
    }

    private inline fun <reified E : Enum<E>> Map<String, Any?>.enum(key: String, default: E): E =
        enumOrNull<E>(key) ?: default
}
