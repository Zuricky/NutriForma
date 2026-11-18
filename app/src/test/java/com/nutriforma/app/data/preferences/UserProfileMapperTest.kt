package com.nutriforma.app.data.preferences

import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.ReminderSettings
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.TurnoLungoOption
import com.nutriforma.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileMapperTest {

    private val customProfile = UserProfile(
        onboardingCompleted = true,
        goal = Goal.DIMAGRIMENTO,
        sportLevel = SportLevel.MODERATO,
        sesso = Sesso.FEMMINA,
        birthYear = 1991,
        heightCm = 168,
        dislikedFoodIds = setOf("salmone_affumicato", "bresaola"),
        cheatMealsPerWeek = 2,
        turnoLungoDefaultDays = setOf(0, 3, 6),
        turnoLungoDefaultOption = TurnoLungoOption.BARRETTA,
        turnoLungoDefaultSlot = MealSlot.META_MATTINA,
        waterGoalMl = 2500,
        reminders = ReminderSettings(
            mealRemindersEnabled = true,
            mealTimes = mapOf(
                MealSlot.COLAZIONE to 420, MealSlot.META_MATTINA to 600, MealSlot.PRANZO to 800,
                MealSlot.META_POMERIGGIO to 1000, MealSlot.CENA to 1200,
            ),
            waterRemindersEnabled = true, waterIntervalMinutes = 90, waterStartMinutes = 500, waterEndMinutes = 1200,
            dailyTipEnabled = false, dailyTipMinutes = 600,
            weighInReminderEnabled = true, weighInDayOfWeek = 7, weighInMinutes = 540,
        ),
        backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ANutriForma",
        autoBackupEnabled = true,
        lastBackupAtMillis = 1_794_000_000_000L,
        foodSeedVersion = 1,
    )

    private fun roundTrip(profile: UserProfile) = UserProfileMapper.fromMap(UserProfileMapper.toMap(profile))

    @Test
    fun defaultProfileRoundTrips() {
        assertEquals(UserProfile(), roundTrip(UserProfile()))
    }

    @Test
    fun emptyMapGivesDefaults() {
        assertEquals(UserProfile(), UserProfileMapper.fromMap(emptyMap()))
    }

    @Test
    fun fullyCustomProfileRoundTrips() {
        assertEquals(customProfile, roundTrip(customProfile))
    }

    @Test
    fun absentOptionalValuesMapToNullKeys() {
        val map = UserProfileMapper.toMap(UserProfile())
        listOf(
            UserProfileKeys.SESSO, UserProfileKeys.BIRTH_YEAR, UserProfileKeys.HEIGHT_CM,
            UserProfileKeys.BACKUP_FOLDER_URI, UserProfileKeys.LAST_BACKUP_AT,
        ).forEach {
            assertTrue("chiave $it assente dalla mappa", map.containsKey(it))
            assertNull(map[it])
        }
        assertNull(roundTrip(UserProfile()).sesso)
    }

    @Test
    fun invalidEnumStringsFallBackToDefaults() {
        val map = mapOf<String, Any?>(
            UserProfileKeys.GOAL to "OBIETTIVO_INESISTENTE",
            UserProfileKeys.SPORT_LEVEL to "SUPERMAN",
            UserProfileKeys.SESSO to "ALTRO",
            UserProfileKeys.TURNO_LUNGO_DEFAULT_OPTION to "boh",
            UserProfileKeys.TURNO_LUNGO_DEFAULT_SLOT to "CENA_TARDI",
        )
        val profile = UserProfileMapper.fromMap(map)
        assertEquals(Goal.EQUILIBRIO, profile.goal)
        assertEquals(SportLevel.MOLTO_INTENSO, profile.sportLevel)
        assertNull(profile.sesso)
        assertEquals(TurnoLungoOption.FRUTTA_E_SECCA, profile.turnoLungoDefaultOption)
        assertEquals(MealSlot.META_POMERIGGIO, profile.turnoLungoDefaultSlot)
    }

    @Test
    fun wronglyTypedValuesFallBackToDefaults() {
        val map = mapOf<String, Any?>(
            UserProfileKeys.ONBOARDING_COMPLETED to "true",
            UserProfileKeys.GOAL to 3,
            UserProfileKeys.WATER_GOAL_ML to "2000",
            UserProfileKeys.DISLIKED_FOOD_IDS to "bresaola",
        )
        assertEquals(UserProfile(), UserProfileMapper.fromMap(map))
    }

    @Test
    fun cheatMealsAreClampedTo1To2() {
        fun cheat(value: Int) = UserProfileMapper.fromMap(mapOf(UserProfileKeys.CHEAT_MEALS_PER_WEEK to value)).cheatMealsPerWeek
        assertEquals(2, cheat(5))
        assertEquals(1, cheat(0))
        assertEquals(1, cheat(-3))
        assertEquals(2, cheat(2))
        assertEquals(2, UserProfileMapper.toMap(UserProfile(cheatMealsPerWeek = 9))[UserProfileKeys.CHEAT_MEALS_PER_WEEK])
    }

    @Test
    fun waterGoalIsClampedTo1700To4000() {
        fun water(value: Int) = UserProfileMapper.fromMap(mapOf(UserProfileKeys.WATER_GOAL_ML to value)).waterGoalMl
        assertEquals(1700, water(1000))
        assertEquals(4000, water(9000))
        assertEquals(1700, water(1700))
        assertEquals(3100, water(3100))
        assertEquals(1700, UserProfileMapper.toMap(UserProfile(waterGoalMl = 500))[UserProfileKeys.WATER_GOAL_ML])
    }

    @Test
    fun turnoLungoDaysKeepOnlyValidDayIndexes() {
        val map = mapOf<String, Any?>(UserProfileKeys.TURNO_LUNGO_DEFAULT_DAYS to setOf("0", "3", "7", "-1", "x", "6"))
        assertEquals(setOf(0, 3, 6), UserProfileMapper.fromMap(map).turnoLungoDefaultDays)
        val stored = UserProfileMapper.toMap(UserProfile(turnoLungoDefaultDays = setOf(2, 4)))
        assertEquals(setOf("2", "4"), stored[UserProfileKeys.TURNO_LUNGO_DEFAULT_DAYS])
    }

    @Test
    fun keyNamesMatchThePlan() {
        val expected = setOf(
            "onboarding_completed", "goal", "sport_level", "sesso", "birth_year", "height_cm", "disliked_food_ids",
            "cheat_meals_per_week", "turno_lungo_default_days", "turno_lungo_default_option", "turno_lungo_default_slot",
            "water_goal_ml", "reminder_meals_enabled",
            "reminder_time_colazione", "reminder_time_meta_mattina", "reminder_time_pranzo",
            "reminder_time_meta_pomeriggio", "reminder_time_cena",
            "reminder_water_enabled", "reminder_water_interval", "reminder_water_start", "reminder_water_end",
            "reminder_tip_enabled", "reminder_tip_time",
            "reminder_weighin_enabled", "reminder_weighin_day", "reminder_weighin_time",
            "backup_folder_uri", "auto_backup_enabled", "last_backup_at", "food_seed_version",
        )
        assertEquals(expected, UserProfileMapper.toMap(UserProfile()).keys)
        assertEquals("user_profile", UserProfileKeys.DATASTORE_NAME)
    }

    @Test
    fun mealTimesDefaultToTheSlotDefaultsWhenMissing() {
        val map = mapOf<String, Any?>(UserProfileKeys.mealTime(MealSlot.PRANZO) to 900)
        val times = UserProfileMapper.fromMap(map).reminders.mealTimes
        assertEquals(900, times[MealSlot.PRANZO])
        assertEquals(450, times[MealSlot.COLAZIONE])
        assertEquals(1230, times[MealSlot.CENA])
    }
}
