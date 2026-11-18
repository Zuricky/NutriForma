package com.nutriforma.app.data.repository

import com.nutriforma.app.data.TestDataStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MealCompletionRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: TestDataStore
    private lateinit var repository: MealCompletionRepository

    @Before
    fun setUp() {
        store = TestDataStore(tmp.root, "meal_completion_test")
        repository = MealCompletionRepository(store.dataStore)
    }

    @After
    fun tearDown() = store.scope.cancel()

    @Test
    fun startsEmptyAndTracksTicks() = runBlocking {
        assertEquals(emptySet<Long>(), repository.completedMealIds.first())
        repository.setCompleted(7, true)
        repository.setCompleted(9, true)
        assertEquals(setOf(7L, 9L), repository.completedMealIds.first())
        repository.setCompleted(7, false)
        assertEquals(setOf(9L), repository.completedMealIds.first())
    }
}
