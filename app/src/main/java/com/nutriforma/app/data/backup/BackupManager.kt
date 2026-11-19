package com.nutriforma.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.nutriforma.app.data.preferences.UserProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Backup locale in JSON tramite Storage Access Framework (nessun permesso di archiviazione, nessuna rete):
 * - export/import manuali con `CreateDocument` / `OpenDocument`;
 * - seconda copia automatica `dieta_backup_auto.json` nella cartella scelta con `OpenDocumentTree`.
 */
class BackupManager(
    private val context: Context,
    private val service: BackupService,
    private val profileRepository: UserProfileRepository,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) : BackupGateway {
    // Canale conflated: un segnale inviato prima che il collettore parta non va perso e i segnali ravvicinati si fondono.
    private val trigger = Channel<Unit>(Channel.CONFLATED)
    private val _autoBackupError = MutableStateFlow<String?>(null)

    override val autoBackupError: StateFlow<String?> = _autoBackupError

    @OptIn(FlowPreview::class)
    private fun startAutoBackupLoop() {
        scope.launch { trigger.receiveAsFlow().debounce(AUTO_DEBOUNCE_MS).collect { runAutoBackup() } }
    }

    init {
        startAutoBackupLoop()
    }

    override fun suggestedFileName(): String =
        "NutriForma_backup_${LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.json"

    // ---- Manuale ---------------------------------------------------------------------------------------------

    override suspend fun exportTo(target: String) = withContext(ioDispatcher) {
        val uri = Uri.parse(target)
        val text = BackupCodec.encode(service.buildBackup())
        try {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw BackupException("Non riesco a scrivere il file.")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Non riesco a scrivere il file di backup.", e)
        }
        markBackupDone()
    }

    override suspend fun readBackup(source: String): BackupFile = withContext(ioDispatcher) {
        val uri = Uri.parse(source)
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: throw BackupException("Non riesco a leggere il file.")
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Non riesco a leggere il file selezionato.", e)
        }
        BackupCodec.decode(text)
    }

    /**
     * Un ripristino iniziato non si interrompe a metà (anche se la schermata viene chiusa): il servizio gestisce da sé
     * gli errori e rimette l'app in uno stato utilizzabile (settimana corrente e promemoria).
     */
    override suspend fun restore(file: BackupFile): RestoreResult = withContext(ioDispatcher + NonCancellable) {
        val result = try {
            service.restore(file)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Ripristino non riuscito. Riprova.", e)
        }
        // I dati ripristinati diventano la nuova copia automatica.
        scheduleAutoBackup()
        result
    }

    // ---- Automatico ------------------------------------------------------------------------------------------

    /** Salva il permesso persistente sulla cartella, attiva il backup automatico e scrive subito la prima copia. */
    override suspend fun setAutoBackupFolder(tree: String) = withContext(ioDispatcher) {
        val treeUri = Uri.parse(tree)
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            throw BackupException("Non posso usare questa cartella: scegline un'altra.", e)
        }
        profileRepository.updateSilently { it.copy(backupFolderUri = treeUri.toString(), autoBackupEnabled = true) }
        _autoBackupError.value = null
        if (!runAutoBackup()) {
            throw BackupException("Cartella scelta, ma non riesco a scrivere il backup: scegline un'altra.")
        }
    }

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        profileRepository.updateSilently { it.copy(autoBackupEnabled = enabled && it.backupFolderUri != null) }
        if (enabled) runAutoBackup() else _autoBackupError.value = null
    }

    /** Da chiamare quando cambiano i dati dell'utente (piani, profilo, acqua, spunte…): scrive dopo una breve pausa. */
    fun scheduleAutoBackup() {
        trigger.trySend(Unit)
    }

    /** Scrive/aggiorna `dieta_backup_auto.json`. Restituisce `true` se la copia è stata scritta. */
    suspend fun runAutoBackup(): Boolean = withContext(ioDispatcher) {
        val profile = profileRepository.current()
        val folder = profile.backupFolderUri
        if (!profile.autoBackupEnabled || folder == null) return@withContext false
        try {
            val text = BackupCodec.encode(service.buildBackup())
            writeAutoFile(Uri.parse(folder), text)
            _autoBackupError.value = null
            markBackupDone()
            true
        } catch (e: Exception) {
            _autoBackupError.value = "Non riesco a scrivere nella cartella di backup automatico: scegli di nuovo la cartella."
            false
        }
    }

    private fun writeAutoFile(tree: Uri, text: String) {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val existing = findChild(tree, treeDocId, AUTO_FILE_NAME)
        val target = existing ?: DocumentsContract.createDocument(
            resolver, DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId), "application/json", AUTO_FILE_NAME,
        ) ?: throw BackupException("Non riesco a creare il file nella cartella scelta.")
        val stream = resolver.openOutputStream(target, "wt") ?: throw BackupException("Non riesco a scrivere il file.")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun findChild(tree: Uri, parentDocId: String, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        return null
    }

    private suspend fun markBackupDone() {
        profileRepository.updateSilently { it.copy(lastBackupAtMillis = clock.millis()) }
    }

    companion object {
        const val AUTO_FILE_NAME = "dieta_backup_auto.json"
        private const val AUTO_DEBOUNCE_MS = 2_000L
    }
}
