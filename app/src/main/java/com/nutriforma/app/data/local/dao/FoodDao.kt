package com.nutriforma.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nutriforma.app.data.local.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Upsert
    suspend fun upsertAll(items: List<FoodItemEntity>)

    @Query("SELECT * FROM food_items ORDER BY sortOrder")
    fun observeAll(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items ORDER BY sortOrder")
    suspend fun getAll(): List<FoodItemEntity>

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun count(): Int
}
