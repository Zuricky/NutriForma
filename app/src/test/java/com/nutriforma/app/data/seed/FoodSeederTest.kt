package com.nutriforma.app.data.seed

import com.nutriforma.app.data.FakeFoodDao
import com.nutriforma.app.data.TestDataStore
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.MealRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class FoodSeederTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: TestDataStore
    private lateinit var profileRepository: UserProfileRepository
    private lateinit var dao: FakeFoodDao
    private val logs = mutableListOf<String>()

    private val realSeedFile = File("src/main/assets/foods_it.json")

    @Before
    fun setUp() {
        store = TestDataStore(tmp.root)
        profileRepository = UserProfileRepository(store.dataStore)
        dao = FakeFoodDao()
        logs.clear()
    }

    @After
    fun tearDown() {
        store.scope.cancel()
    }

    private fun seeder(open: () -> java.io.InputStream = { realSeedFile.inputStream() }) = FoodSeeder(
        foodDao = dao,
        userProfileRepository = profileRepository,
        json = Json { ignoreUnknownKeys = true },
        ioDispatcher = Dispatchers.IO,
        openSeedFile = open,
        log = { logs += it },
    )

    private fun tinySeed(version: Int, vararg ids: String): () -> java.io.InputStream = {
        val foods = ids.joinToString(",") {
            """{"id":"$it","nameIt":"$it","category":"VERDURE","roles":["PRANZO_ANTIPASTO"],"kcal":20.0,
               "protein":1.0,"carbs":3.0,"fat":0.2,"fiber":2.0,"supermarket":"FRUTTA_VERDURA",
               "preferred":false,"excludable":true}"""
        }
        ByteArrayInputStream("""{"seedVersion":$version,"foods":[$foods]}""".toByteArray())
    }

    @Test
    fun firstLaunchSeeds93FoodsInFileOrderAndStoresTheVersion() = runBlocking {
        assertEquals(93, seeder().seedIfNeeded())
        assertEquals(93, dao.count())
        assertEquals(2, profileRepository.current().foodSeedVersion)
        assertEquals(listOf("Seed: 93 alimenti"), logs)

        val stored = dao.getAll()
        assertEquals("prosciutto_crudo", stored.first().id)
        // I nuovi alimenti della 1.1 sono in coda: gli indici di ordinamento esistenti non cambiano.
        assertEquals("pasto_libero", stored[84].id)
        assertEquals("tortellini_brodo", stored.last().id)
        assertEquals((0 until 93).toList(), stored.map { it.sortOrder })
        val uovo = stored.single { it.id == "uovo" }
        assertEquals(FoodCategory.UOVA, uovo.category)
        assertTrue(MealRole.COLAZIONE_G1 in uovo.roles)
        assertEquals(55.0, uovo.gramsPerUnit)
        assertNull(stored.single { it.id == "pasto_libero" }.supermarketCategory)
    }

    @Test
    fun secondLaunchDoesNotReseed() = runBlocking {
        seeder().seedIfNeeded()
        assertEquals(0, seeder().seedIfNeeded())
        assertEquals(1, dao.upsertCalls)
        assertEquals(1, logs.size)
        assertEquals(93, dao.count())
    }

    @Test
    fun emptyTableIsReseededEvenIfTheVersionIsStored() = runBlocking {
        seeder().seedIfNeeded()
        dao.clear()
        assertEquals(93, seeder().seedIfNeeded())
        assertEquals(93, dao.count())
        assertEquals(2, dao.upsertCalls)
    }

    @Test
    fun newerSeedVersionUpsertsWithoutDuplicates() = runBlocking {
        assertEquals(2, seeder(tinySeed(1, "a", "b")).seedIfNeeded())
        assertEquals(0, seeder(tinySeed(1, "a", "b")).seedIfNeeded())
        assertEquals(3, seeder(tinySeed(2, "a", "b", "c")).seedIfNeeded())
        assertEquals(listOf("a", "b", "c"), dao.getAll().map { it.id })
        assertEquals(2, profileRepository.current().foodSeedVersion)
    }

    @Test
    fun invalidEnumInSeedFileFailsLoudlyAndWritesNothing() {
        val bad = """{"seedVersion":1,"foods":[{"id":"x","nameIt":"X","category":"NON_ESISTE","roles":[],
            "kcal":1.0,"protein":0.0,"carbs":0.0,"fat":0.0,"fiber":0.0,"preferred":false,"excludable":true}]}"""
        val result = runCatching { runBlocking { seeder { ByteArrayInputStream(bad.toByteArray()) }.seedIfNeeded() } }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, runBlocking { dao.count() })
        assertEquals(0, runBlocking { profileRepository.current().foodSeedVersion })
    }
}
