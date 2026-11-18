package com.nutriforma.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nutriforma.app.data.local.dao.BodyMeasurementDao
import com.nutriforma.app.data.local.dao.FoodDao
import com.nutriforma.app.data.local.dao.PlannedMealDao
import com.nutriforma.app.data.local.dao.ShoppingCheckDao
import com.nutriforma.app.data.local.dao.WaterLogDao
import com.nutriforma.app.data.local.dao.WeeklyPlanDao
import com.nutriforma.app.data.local.entity.BodyMeasurementEntity
import com.nutriforma.app.data.local.entity.FoodItemEntity
import com.nutriforma.app.data.local.entity.PlannedMealEntity
import com.nutriforma.app.data.local.entity.ShoppingCheckEntity
import com.nutriforma.app.data.local.entity.WaterLogEntity
import com.nutriforma.app.data.local.entity.WeeklyPlanEntity

/**
 * Future schema changes: bump [version] + explicit `Migration` + androidTest migration test.
 * Never use `fallbackToDestructiveMigration` or `allowMainThreadQueries`.
 */
@Database(
    entities = [
        FoodItemEntity::class,
        WeeklyPlanEntity::class,
        PlannedMealEntity::class,
        BodyMeasurementEntity::class,
        WaterLogEntity::class,
        ShoppingCheckEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NutriFormaDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun weeklyPlanDao(): WeeklyPlanDao
    abstract fun plannedMealDao(): PlannedMealDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun shoppingCheckDao(): ShoppingCheckDao

    companion object {
        const val NAME = "nutriforma.db"
    }
}
