package com.nutriforma.app.data.backup

import com.nutriforma.app.data.local.TransactionRunner
import com.nutriforma.app.data.local.dao.BodyMeasurementDao
import com.nutriforma.app.data.local.dao.PlannedMealDao
import com.nutriforma.app.data.local.dao.ShoppingCheckDao
import com.nutriforma.app.data.local.dao.WaterLogDao
import com.nutriforma.app.data.local.dao.WeeklyPlanDao
import com.nutriforma.app.data.preferences.UserProfileRepository
import com.nutriforma.app.data.repository.MealCompletionRepository
import com.nutriforma.app.data.repository.ShoppingExtrasRepository
import java.time.Clock

/** Esito di un ripristino riuscito: [warnings] elenca i passi accessori non riusciti (messaggi in italiano). */
data class RestoreResult(val warnings: List<String> = emptyList())

/**
 * Logica di backup/ripristino senza nulla di Android (testabile con DAO finti): costruisce il [BackupFile] dal database
 * e lo ripristina in modalità "sostituisci tutto".
 *
 * Dopo un ripristino l'app deve essere subito utilizzabile: [ensureCurrentWeek] ricrea la settimana in corso se il
 * backup non la contiene e [syncReminders] riallinea i promemoria alle impostazioni ripristinate.
 */
class BackupService(
    private val planDao: WeeklyPlanDao,
    private val mealDao: PlannedMealDao,
    private val measurementDao: BodyMeasurementDao,
    private val waterDao: WaterLogDao,
    private val checkDao: ShoppingCheckDao,
    private val transactions: TransactionRunner,
    private val profileRepository: UserProfileRepository,
    private val completionRepository: MealCompletionRepository,
    private val extrasRepository: ShoppingExtrasRepository,
    private val clock: Clock,
    private val appVersionName: String,
    private val ensureCurrentWeek: suspend () -> Unit = {},
    private val syncReminders: suspend () -> Unit = {},
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
            completedMealIds = completionRepository.current().sorted(),
            shoppingExtras = extrasRepository.current().map { it.toBackup() },
        )
    }

    /**
     * Sostituisce piani, misure, acqua, spunte, pasti completati, extra della spesa e profilo con il contenuto di
     * [file], mantenendo gli id originali. I campi assenti nei backup più vecchi si leggono come vuoti.
     *
     * La parte su database è un'unica transazione: se fallisce non cambia nulla. Il profilo e le preferenze si
     * scrivono dopo; se uno di quei passi fallisce l'errore lo dice chiaramente e ripetere il ripristino è sicuro.
     */
    suspend fun restore(file: BackupFile): RestoreResult {
        validate(file)
        val planIds = file.weeklyPlans.map { it.id }.toSet()
        val mealIds = file.weeklyPlans.flatMap { p -> p.meals.map { it.id } }.toSet()
        try {
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
        } catch (e: Exception) {
            throw BackupException("Ripristino non riuscito: i dati attuali non sono stati modificati.", e)
        }

        try {
            profileRepository.updateSilently { file.profile.toProfile(it) }
            // I pasti completati hanno senso solo per i pasti ripristinati: gli id altrui non vanno tenuti.
            completionRepository.replaceAll(file.completedMealIds.filter { it in mealIds }.toSet())
            extrasRepository.replaceAll(file.shoppingExtras.map { it.toDomain() })
        } catch (e: Exception) {
            throw BackupException(
                "Ripristino incompleto: piani e misure sono stati ripristinati ma non le impostazioni. Riprova a ripristinare lo stesso file.",
                e,
            )
        }

        val warnings = mutableListOf<String>()
        try {
            ensureCurrentWeek()
        } catch (e: Exception) {
            warnings += "La settimana in corso verrà preparata aprendo la scheda Dieta."
        }
        try {
            syncReminders()
        } catch (e: Exception) {
            warnings += "Non sono riuscito ad aggiornare i promemoria: riattivali dal Profilo."
        }
        return RestoreResult(warnings)
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
        if (file.shoppingExtras.map { it.id }.toSet().size != file.shoppingExtras.size) throw bad
    }
}
