package com.nutriforma.app.data

import com.nutriforma.app.data.local.TransactionRunner
import com.nutriforma.app.data.local.dao.PlannedMealDao
import com.nutriforma.app.data.local.dao.ShoppingCheckDao
import com.nutriforma.app.data.local.dao.WeeklyPlanDao
import com.nutriforma.app.data.local.entity.PlannedMealEntity
import com.nutriforma.app.data.local.entity.ShoppingCheckEntity
import com.nutriforma.app.data.local.entity.WeeklyPlanEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Hand-written in-memory DAOs (no Mockito/Robolectric). */
class FakeWeeklyPlanDao : WeeklyPlanDao {
    val plans = MutableStateFlow<List<WeeklyPlanEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(plan: WeeklyPlanEntity): Long {
        require(plans.value.none { it.weekStartEpochDay == plan.weekStartEpochDay }) { "UNIQUE weekStartEpochDay" }
        val id = if (plan.id != 0L) plan.id else nextId++
        if (id >= nextId) nextId = id + 1
        plans.value = plans.value + plan.copy(id = id)
        return id
    }

    override suspend fun update(plan: WeeklyPlanEntity) {
        plans.value = plans.value.map { if (it.id == plan.id) plan else it }
    }

    override suspend fun getByWeekStart(epochDay: Long) = plans.value.firstOrNull { it.weekStartEpochDay == epochDay }
    override fun observeByWeekStart(epochDay: Long): Flow<WeeklyPlanEntity?> =
        plans.map { list -> list.firstOrNull { it.weekStartEpochDay == epochDay } }

    override suspend fun getById(id: Long) = plans.value.firstOrNull { it.id == id }
    override suspend fun getAll() = plans.value.sortedBy { it.weekStartEpochDay }
    override suspend fun insertAll(plans: List<WeeklyPlanEntity>) { plans.forEach { insert(it) } }
    override suspend fun deleteById(id: Long) { plans.value = plans.value.filterNot { it.id == id } }
    override suspend fun deleteAll() { plans.value = emptyList() }
}

class FakePlannedMealDao : PlannedMealDao {
    val meals = MutableStateFlow<List<PlannedMealEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insertAll(meals: List<PlannedMealEntity>): List<Long> {
        val inserted = meals.map { m ->
            val id = if (m.id != 0L) m.id else nextId++
            if (id >= nextId) nextId = id + 1
            m.copy(id = id)
        }
        this.meals.value = this.meals.value + inserted
        return inserted.map { it.id }
    }

    override suspend fun update(meal: PlannedMealEntity) {
        meals.value = meals.value.map { if (it.id == meal.id) meal else it }
    }

    override suspend fun updateAll(meals: List<PlannedMealEntity>) { meals.forEach { update(it) } }

    // Like SQL `ORDER BY dayIndex, slot`: alphabetical on the slot name (the repository must re-sort).
    override fun observeForPlan(planId: Long): Flow<List<PlannedMealEntity>> =
        meals.map { list -> list.filter { it.planId == planId }.sortedWith(compareBy({ it.dayIndex }, { it.slot.name })) }

    override suspend fun getForPlan(planId: Long) =
        meals.value.filter { it.planId == planId }.sortedWith(compareBy({ it.dayIndex }, { it.slot.name }))

    override suspend fun getById(id: Long) = meals.value.firstOrNull { it.id == id }
    override suspend fun deleteForPlan(planId: Long) { meals.value = meals.value.filterNot { it.planId == planId } }
    override suspend fun getAll() = meals.value
}

class FakeShoppingCheckDao : ShoppingCheckDao {
    val checks = MutableStateFlow<List<ShoppingCheckEntity>>(emptyList())

    override suspend fun upsert(check: ShoppingCheckEntity) {
        checks.value = checks.value.filterNot { it.planId == check.planId && it.foodId == check.foodId } + check
    }

    override fun observeForPlan(planId: Long): Flow<List<ShoppingCheckEntity>> =
        checks.map { list -> list.filter { it.planId == planId } }

    override suspend fun clearForPlan(planId: Long) { checks.value = checks.value.filterNot { it.planId == planId } }
    override suspend fun getAll() = checks.value
    override suspend fun insertAll(items: List<ShoppingCheckEntity>) { items.forEach { upsert(it) } }
}

/** Runs the block directly and counts how many transactions were opened. */
class CountingTransactionRunner : TransactionRunner {
    var count = 0
        private set

    override suspend fun <T> run(block: suspend () -> T): T {
        count++
        return block()
    }
}
