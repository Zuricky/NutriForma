package com.nutriforma.app.data.preferences

import com.nutriforma.app.domain.model.MealSlot

/**
 * Exact DataStore key names. Kept as plain strings so [UserProfileMapper] stays JVM-testable;
 * [UserProfileRepository] turns them into typed `Preferences.Key`s according to the value type.
 */
object UserProfileKeys {
    const val DATASTORE_NAME = "user_profile"

    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val GOAL = "goal"
    const val SPORT_LEVEL = "sport_level"
    const val SESSO = "sesso"
    const val BIRTH_YEAR = "birth_year"
    const val HEIGHT_CM = "height_cm"
    const val DISLIKED_FOOD_IDS = "disliked_food_ids"
    const val CHEAT_MEALS_PER_WEEK = "cheat_meals_per_week"
    const val TURNO_LUNGO_DEFAULT_DAYS = "turno_lungo_default_days"
    const val TURNO_LUNGO_DEFAULT_OPTION = "turno_lungo_default_option"
    const val TURNO_LUNGO_DEFAULT_SLOT = "turno_lungo_default_slot"
    const val WATER_GOAL_ML = "water_goal_ml"
    const val REMINDER_MEALS_ENABLED = "reminder_meals_enabled"
    const val REMINDER_WATER_ENABLED = "reminder_water_enabled"
    const val REMINDER_WATER_INTERVAL = "reminder_water_interval"
    const val REMINDER_WATER_START = "reminder_water_start"
    const val REMINDER_WATER_END = "reminder_water_end"
    const val REMINDER_TIP_ENABLED = "reminder_tip_enabled"
    const val REMINDER_TIP_TIME = "reminder_tip_time"
    const val REMINDER_WEIGHIN_ENABLED = "reminder_weighin_enabled"
    const val REMINDER_WEIGHIN_DAY = "reminder_weighin_day"
    const val REMINDER_WEIGHIN_TIME = "reminder_weighin_time"
    const val BACKUP_FOLDER_URI = "backup_folder_uri"
    const val AUTO_BACKUP_ENABLED = "auto_backup_enabled"
    const val LAST_BACKUP_AT = "last_backup_at"
    const val FOOD_SEED_VERSION = "food_seed_version"

    /** `reminder_time_` + slot name lowercase, e.g. `reminder_time_meta_mattina`. */
    fun mealTime(slot: MealSlot): String = "reminder_time_" + slot.name.lowercase()

    const val MIN_CHEAT_MEALS = 1
    const val MAX_CHEAT_MEALS = 2
    const val MIN_WATER_GOAL_ML = 1700
    const val MAX_WATER_GOAL_ML = 4000
}
