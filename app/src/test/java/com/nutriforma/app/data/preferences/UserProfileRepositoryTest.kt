package com.nutriforma.app.data.preferences

import com.nutriforma.app.data.TestDataStore
import com.nutriforma.app.domain.model.Goal
import com.nutriforma.app.domain.model.MealSlot
import com.nutriforma.app.domain.model.Sesso
import com.nutriforma.app.domain.model.SportLevel
import com.nutriforma.app.domain.model.UserProfile
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserProfileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: TestDataStore
    private lateinit var repository: UserProfileRepository

    @Before
    fun setUp() {
        store = TestDataStore(tmp.root)
        repository = UserProfileRepository(store.dataStore)
    }

    @After
    fun tearDown() {
        store.scope.cancel()
    }

    @Test
    fun emptyStoreGivesDefaultProfile() = runBlocking {
        assertEquals(UserProfile(), repository.current())
    }

    @Test
    fun updatePersistsAndEmitsTheNewProfile() = runBlocking {
        repository.update {
            it.copy(
                onboardingCompleted = true, goal = Goal.ENERGIA, sportLevel = SportLevel.INTENSO, sesso = Sesso.MASCHIO,
                birthYear = 1990, heightCm = 180, dislikedFoodIds = setOf("bresaola", "avocado"), cheatMealsPerWeek = 2,
                waterGoalMl = 2200, turnoLungoDefaultDays = setOf(2, 4),
                backupFolderUri = "content://tree/backup", lastBackupAtMillis = 123L,
            )
        }
        val profile = repository.current()
        assertEquals(Goal.ENERGIA, profile.goal)
        assertEquals(SportLevel.INTENSO, profile.sportLevel)
        assertEquals(Sesso.MASCHIO, profile.sesso)
        assertEquals(setOf("bresaola", "avocado"), profile.dislikedFoodIds)
        assertEquals(2, profile.cheatMealsPerWeek)
        assertEquals(2200, profile.waterGoalMl)
        assertEquals(setOf(2, 4), profile.turnoLungoDefaultDays)
        assertEquals("content://tree/backup", profile.backupFolderUri)
        assertEquals(123L, profile.lastBackupAtMillis)
    }

    @Test
    fun updateCanClearOptionalValuesAndDislikes() = runBlocking {
        repository.update { it.copy(sesso = Sesso.FEMMINA, backupFolderUri = "content://x", dislikedFoodIds = setOf("uovo")) }
        repository.update { it.copy(sesso = null, backupFolderUri = null, dislikedFoodIds = emptySet()) }
        val profile = repository.current()
        assertNull(profile.sesso)
        assertNull(profile.backupFolderUri)
        assertEquals(emptySet<String>(), profile.dislikedFoodIds)
    }

    @Test
    fun updateOnlyChangesWhatTheTransformChanges() = runBlocking {
        repository.update { it.copy(goal = Goal.DIMAGRIMENTO, foodSeedVersion = 1) }
        repository.update { it.copy(waterGoalMl = 3000) }
        val profile = repository.current()
        assertEquals(Goal.DIMAGRIMENTO, profile.goal)
        assertEquals(1, profile.foodSeedVersion)
        assertEquals(3000, profile.waterGoalMl)
    }

    @Test
    fun outOfRangeValuesAreClampedWhenStored() = runBlocking {
        repository.update { it.copy(cheatMealsPerWeek = 5, waterGoalMl = 1000) }
        val profile = repository.current()
        assertEquals(2, profile.cheatMealsPerWeek)
        assertEquals(1700, profile.waterGoalMl)
    }

    @Test
    fun replaceAllOverwritesEverything() = runBlocking {
        repository.update { it.copy(sesso = Sesso.MASCHIO, dislikedFoodIds = setOf("uovo"), backupFolderUri = "content://old") }
        val restored = UserProfile(
            onboardingCompleted = true,
            goal = Goal.DIMAGRIMENTO,
            reminders = UserProfile().reminders.copy(
                mealRemindersEnabled = true,
                mealTimes = UserProfile().reminders.mealTimes + (MealSlot.CENA to 1200),
            ),
        )
        repository.replaceAll(restored)
        assertEquals(restored, repository.current())
    }
}
