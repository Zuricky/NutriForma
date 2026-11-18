package com.nutriforma.app.data.repository

import com.nutriforma.app.data.local.dao.FoodDao
import com.nutriforma.app.data.mapper.toDomain
import com.nutriforma.app.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FoodRepository(private val foodDao: FoodDao) {

    fun observeFoods(): Flow<List<FoodItem>> = foodDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getFoods(): List<FoodItem> = foodDao.getAll().map { it.toDomain() }
}
