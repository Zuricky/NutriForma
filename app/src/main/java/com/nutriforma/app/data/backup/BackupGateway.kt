package com.nutriforma.app.data.backup

import kotlinx.coroutines.flow.StateFlow

/**
 * Operazioni di backup viste dalla schermata Profilo. I percorsi (file o cartella scelti con il selettore di sistema)
 * viaggiano come stringhe, così chi le usa non dipende da classi Android.
 */
interface BackupGateway {
    /** Ultimo errore del backup automatico (es. cartella non più accessibile); `null` se tutto ok. */
    val autoBackupError: StateFlow<String?>

    fun suggestedFileName(): String

    suspend fun exportTo(target: String)

    /** Legge e valida il file scelto, senza toccare i dati attuali. */
    suspend fun readBackup(source: String): BackupFile

    suspend fun restore(file: BackupFile): RestoreResult

    suspend fun setAutoBackupFolder(tree: String)

    suspend fun setAutoBackupEnabled(enabled: Boolean)
}
