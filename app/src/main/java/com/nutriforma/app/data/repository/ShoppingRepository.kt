package com.nutriforma.app.data.repository

import com.nutriforma.app.data.local.dao.ShoppingCheckDao
import com.nutriforma.app.data.local.entity.ShoppingCheckEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock

/** Spunte "acquistato / già in casa" della spesa, per piano e per alimento (tabella `shopping_checks`). */
class ShoppingRepository(
    private val checkDao: ShoppingCheckDao,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeCheckedFoodIds(planId: Long): Flow<Set<String>> =
        checkDao.observeForPlan(planId)
            .map { rows -> rows.filter { it.isChecked }.map { it.foodId }.toSet() }
            .distinctUntilChanged()

    suspend fun setChecked(planId: Long, foodId: String, checked: Boolean) = withContext(ioDispatcher) {
        checkDao.upsert(ShoppingCheckEntity(planId, foodId, checked, clock.millis()))
    }

    suspend fun clearChecks(planId: Long) = withContext(ioDispatcher) { checkDao.clearForPlan(planId) }
}
