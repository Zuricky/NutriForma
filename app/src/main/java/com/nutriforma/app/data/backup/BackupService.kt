package com.nutriforma.app.data.backup

import com.nutriforma.app.data.local.TransactionRunner
import com.nutriforma.app.data.local.dao.BodyMeasurementDao
import com.nutriforma.app.data.local.dao.PlannedMealDao
import com.nutriforma.app.data.local.dao.ShoppingCheckDao
import com.nutriforma.app.data.local.dao.WaterLogDao
import com.nutriforma.app.data.local.dao.WeeklyPlanDao
import com.nutriforma.app.data.preferences.UserProfileRepository
import java.time.Clock

/**
 * Logica di backup/ripristino senza nulla di Android (testabile con DAO finti): costruisce il [BackupFile] dal database
 * e lo ripristina in modalità "sostituisci tutto".
 */
class BackupService(
    private val planDao: WeeklyPlanDao,
    private val mealDao: PlannedMealDao,
    private val measurementDao: BodyMeasurementDao,
    private val waterDao: WaterLogDao,
    private val checkDao: ShoppingCheckDao,
    private val transactions: TransactionRunner,
    private val profileRepository: UserProfileRepository,
    private val clock: Clock,
    private val appVersionName: String,
) {
    suspend fun buildBackup(): BackupFile {
        val mealsByPlan = mealDao.getAll().groupBy { it.planId }
        return BackupFile(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            appVersionName = appVersionName,
            exportedAtMillis = clock.millis(),
            profile = profileRepository.current().toBackup(),
            weeklyPlans = planDao.getAll().map { it.toBackup(mealsByPlan[it.id].orEmpty()) },
            bodyMeasurements = measurementDao.getAll().sortedBy { it.dateEpochDay }.map { it.toBackup() },
            waterLogs = waterDao.getAll().sortedBy { it.id }.map { it.toBackup() },
            shoppingChecks = checkDao.getAll().map { it.toBackup() },
        )
    }

    /** Sostituisce piani, misure, acqua, spunte e profilo con il contenuto di [file], mantenendo gli id originali. */
    suspend fun restore(file: BackupFile) {
        validate(file)
        val planIds = file.weeklyPlans.map { it.id }.toSet()
        transactions.run {
            planDao.getAll().forEach {
                mealDao.deleteForPlan(it.id)
                checkDao.clearForPlan(it.id)
            }
            planDao.deleteAll()
            measurementDao.deleteAll()
            waterDao.deleteAll()

            planDao.insertAll(file.weeklyPlans.map { it.toEntity() })
            mealDao.insertAll(file.weeklyPlans.flatMap { it.mealEntities() })
            // Spunte di piani non presenti nel file sarebbero orfane (chiave esterna): si scartano.
            checkDao.insertAll(file.shoppingChecks.filter { it.planId in planIds }.map { it.toEntity() })
            measurementDao.insertAll(file.bodyMeasurements.map { it.toEntity() })
            waterDao.insertAll(file.waterLogs.map { it.toEntity() })
        }
        profileRepository.update { file.profile.toProfile(it) }
    }

    private fun validate(file: BackupFile) {
        val bad = BackupException("Il backup contiene dati incoerenti e non può essere ripristinato.")
        val plans = file.weeklyPlans
        if (plans.map { it.id }.toSet().size != plans.size) throw bad
        if (plans.map { it.weekStartEpochDay }.toSet().size != plans.size) throw bad
        if (plans.any { p -> p.meals.map { it.dayIndex to it.slot }.toSet().size != p.meals.size }) throw bad
        if (plans.flatMap { p -> p.meals.map { it.id } }.let { it.toSet().size != it.size }) throw bad
        if (file.bodyMeasurements.map { it.dateEpochDay }.toSet().size != file.bodyMeasurements.size) throw bad
        if (file.waterLogs.map { it.id }.toSet().size != file.waterLogs.size) throw bad
    }
}
