package com.nutriforma.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.nutriforma.app.data.local.entity.ShoppingCheckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingCheckDao {
    @Upsert
    suspend fun upsert(check: ShoppingCheckEntity)

    @Query("SELECT * FROM shopping_checks WHERE planId = :planId")
    fun observeForPlan(planId: Long): Flow<List<ShoppingCheckEntity>>

    @Query("DELETE FROM shopping_checks WHERE planId = :planId")
    suspend fun clearForPlan(planId: Long)

    @Query("SELECT * FROM shopping_checks")
    suspend fun getAll(): List<ShoppingCheckEntity>

    @Insert
    suspend fun insertAll(items: List<ShoppingCheckEntity>)
}
