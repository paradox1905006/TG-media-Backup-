package com.dparadox.tgbackup.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dparadox.tgbackup.data.AppDatabase
import com.dparadox.tgbackup.data.SelectedMedia
import com.dparadox.tgbackup.data.FileSyncEngine
import com.dparadox.tgbackup.data.SettingsManager
import com.dparadox.tgbackup.data.UploadedFile
import com.dparadox.tgbackup.network.TelegramApi
import com.dparadox.tgbackup.worker.DatabaseBackupWorker
import com.dparadox.tgbackup.worker.DownloadWorker
import com.dparadox.tgbackup.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.net.Uri
import android.os.Environment

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsManager(application)
    private val db = AppDatabase.getInstance(application)
    private val dao = db.uploadedFileDao()
    private val selectedDao = db.selectedMediaDao()
    private val workManager = WorkManager.getInstance(application)
    private val telegramApi = TelegramApi(application.contentResolver)
    private val fileSyncEngine = FileSyncEngine(application)

    // debounce + distinctUntilChanged: during an active sync, uploaded_files gets
    // written to once per file, which re-triggers this query every time. Without
    // coalescing, that meant a full list recomposition (and 6 extra DB queries)
    // firing many times a second — the main cause of scroll jank on the history
    // screen while a backup is running.
    val uploadHistory: StateFlow<List<UploadedFile>> = dao.getAllRecords()
        .distinctUntilChanged()
        .debounce(200L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Selection state for manual backup mode
    val selectedMediaHashes: StateFlow<Set<String>> = selectedDao.getAllSelected()
        .map { list -> list.map { it.hash }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    data class Stats(
        val uploaded: Int = 0,
        val failed: Int = 0,
        val tooLarge: Int = 0,
        val photos: Int = 0,
        val videos: Int = 0,
        val totalSize: Long = 0
    )

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    private val _discoveredFolders = MutableStateFlow<List<String>>(emptyList())
    val discoveredFolders: StateFlow<List<String>> = _discoveredFolders

    private val _folderThumbnails = MutableStateFlow<Map<String, Uri?>>(emptyMap())
    val folderThumbnails: StateFlow<Map<String, Uri?>> = _folderThumbnails

    private val _watchedFolderUris = MutableStateFlow(settings.watchedFolderUris)
    val watchedFolderUris: StateFlow<List<String>> = _watchedFolderUris

    private val _fullDeviceSyncEnabled = MutableStateFlow(settings.fullDeviceSyncEnabled)
    val fullDeviceSyncEnabled: StateFlow<Boolean> = _fullDeviceSyncEnabled

    private val _encryptionEnabled = MutableStateFlow(settings.encryptionEnabled)
    val encryptionEnabled: StateFlow<Boolean> = _encryptionEnabled

    private val _storageStats = MutableStateFlow<StorageStats>(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats

    data class StorageStats(
        val internalEncrypted: Int = 0,
        val internalPlain: Int = 0,
        val externalEncrypted: Int = 0,
        val externalPlain: Int = 0,
        val hasExternalStorage: Boolean = false
    )

    init {
        scheduleDbBackup()
        // Dispatchers.Default: viewModelScope.launch defaults to Main.immediate, which
        // meant the forEach loop in updateStorageStats() ran on the UI thread on every
        // single history emission (once per uploaded file during a sync). For a large
        // history that's real main-thread work competing with scroll/frame rendering.
        // Only the final StateFlow assignment needs to happen at all, and StateFlow
        // updates are thread-safe, so this is safe to run off the main thread.
        viewModelScope.launch(Dispatchers.Default) {
            uploadHistory.collect { history ->
                val summary = dao.getStatsSummary()
                _stats.value = Stats(
                    uploaded  = summary.uploaded,
                    failed    = summary.failed,
                    tooLarge  = summary.tooLarge,
                    photos    = summary.photos,
                    videos    = summary.videos,
                    totalSize = summary.totalSize
                )
                updateStorageStats(history)
            }
        }
        refreshFolders()
    }

    private fun updateStorageStats(history: List<UploadedFile>) {
        val hasExternal = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED && 
                         !Environment.isExternalStorageEmulated()
        
        var iEnc = 0; var iPlain = 0; var eEnc = 0; var ePlain = 0
        
        history.forEach { file ->
            val isEncrypted = file.fileName.endsWith(".enc")
            // Very basic heuristic for internal vs external
            val isExternal = file.filePath.contains("/storage/emulated/0/") == false && 
                            file.filePath.contains("/storage/")
            
            if (isExternal) {
                if (isEncrypted) eEnc++ else ePlain++
            } else {
                if (isEncrypted) iEnc++ else iPlain++
            }
        }
        
        _storageStats.value = StorageStats(iEnc, iPlain, eEnc, ePlain, hasExternal)
    }

    fun refreshFolders() {
        discoverFolders()
    }

    private fun discoverFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            val folders = fileSyncEngine.getAllFoldersOnDevice()
            _discoveredFolders.value = folders
            
            // Pre-fetch thumbnails for the discovered folders
            val thumbs = mutableMapOf<String, Uri?>()
            folders.forEach { folder ->
                thumbs[folder] = fileSyncEngine.getThumbnailForFolder(folder)
            }
            _folderThumbnails.value = thumbs
        }
    }

    fun toggleFolder(folder: String) {
        val current = settings.watchedFolderUris.toMutableList()
        if (current.contains(folder)) {
            current.remove(folder)
        } else {
            current.add(folder)
        }
        settings.watchedFolderUris = current
        _watchedFolderUris.value = current
    }

    fun setFullDeviceSync(enabled: Boolean) {
        settings.fullDeviceSyncEnabled = enabled
        _fullDeviceSyncEnabled.value = enabled
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        settings.encryptionEnabled = enabled
        _encryptionEnabled.value = enabled
    }

    fun toggleMediaSelected(hash: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (selectedDao.isSelected(hash)) {
                selectedDao.delete(SelectedMedia(hash))
            } else {
                selectedDao.insert(SelectedMedia(hash))
            }
        }
    }

    fun retryFailedUploads() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.retryFailedUploads()
            syncNow()
        }
    }

    suspend fun getThumbnailForFolder(folderName: String): Uri? {
        return fileSyncEngine.getThumbnailForFolder(folderName)
    }

    suspend fun getMediaForFolder(folderName: String?): List<FileSyncEngine.MediaFile> {
        return withContext(Dispatchers.IO) {
            fileSyncEngine.scanAllMedia(
                limitToFolder = folderName,
                dbLookup = { size, mod -> dao.findHashBySizeAndDate(size, mod) }
            )
        }
    }

    fun addCustomFolder(uri: android.net.Uri) {
        val context = getApplication<android.app.Application>()
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val current = settings.watchedFolderUris.toMutableList()
            val uriStr = uri.toString()
            if (!current.contains(uriStr)) {
                current.add(uriStr)
                settings.watchedFolderUris = current
                _watchedFolderUris.value = current
                refreshFolders()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to add folder: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val _validationResult = MutableStateFlow<ValidationResult?>(null)
    val validationResult: StateFlow<ValidationResult?> = _validationResult

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing

    private val _syncPaused = MutableStateFlow(settings.syncPaused)
    val syncPaused: StateFlow<Boolean> = _syncPaused

    private val _restorePaused = MutableStateFlow(settings.restorePaused)
    val restorePaused: StateFlow<Boolean> = _restorePaused

    data class ValidationResult(val ok: Boolean, val message: String)

    fun validateAndSaveCredentials(botToken: String, chatId: String) {
        viewModelScope.launch {
            _isValidating.value = true
            _validationResult.value = null
            val result = withContext(Dispatchers.IO) {
                try {
                    val username = telegramApi.getMe(botToken)
                    val chat = telegramApi.getChat(botToken, chatId)
                    val isForum = chat.optBoolean("is_forum", false)
                    
                    telegramApi.sendTestMessage(botToken, chatId)
                    
                    if (!isForum) {
                        ValidationResult(ok = true, message = "Connected as @$username. NOTE: Topics/Forum mode is NOT enabled in this group. Folders will not be organized.")
                    } else {
                        ValidationResult(ok = true, message = "Connected as @$username. Topics enabled! Your backup will be organized.")
                    }
                } catch (e: Exception) {
                    ValidationResult(ok = false, message = e.message ?: "Unknown error")
                }
            }
            if (result.ok) {
                settings.botToken = botToken
                settings.chatId   = telegramApi.formatChatId(chatId)
            }
            _validationResult.value = result
            _isValidating.value     = false
        }
    }

    fun schedulePeriodicSync() {
        if (!settings.autoSyncEnabled) {
            workManager.cancelUniqueWork("tg_periodic_sync")
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(settings.syncIntervalHours.toLong(), TimeUnit.HOURS, 15L, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        workManager.enqueueUniquePeriodicWork("tg_periodic_sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleDbBackup() {
        if (!settings.dbBackupEnabled) {
            workManager.cancelUniqueWork("tg_db_backup")
            return
        }
        val request = PeriodicWorkRequestBuilder<DatabaseBackupWorker>(settings.dbBackupIntervalHours.toLong(), TimeUnit.HOURS, 15L, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork("tg_db_backup", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun syncNow() {
        settings.syncPaused = false
        _syncPaused.value = false
        val constraints = Constraints.Builder().setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
        workManager.enqueueUniqueWork("tg_sync_now", ExistingWorkPolicy.REPLACE, request)
        Toast.makeText(getApplication(), "Sync started...", Toast.LENGTH_SHORT).show()
    }

    fun downloadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = dao.getAllRecordsSync().filter { it.telegramFileId.isNotEmpty() }
            if (records.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "No files found in database to download. Sync or Restore first.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            dao.resetAllDownloadStatus()
            settings.restorePaused = false
            _restorePaused.value = false
            val request = OneTimeWorkRequestBuilder<DownloadWorker>().build()
            workManager.enqueueUniqueWork("tg_download_all", ExistingWorkPolicy.REPLACE, request)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Starting download of ${records.size} items...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleSyncPause() {
        settings.syncPaused = !settings.syncPaused
        _syncPaused.value = settings.syncPaused
    }

    fun toggleRestorePause() {
        settings.restorePaused = !settings.restorePaused
        _restorePaused.value = settings.restorePaused
    }

    fun pauseDownload() {
        workManager.cancelUniqueWork("tg_download_all")
    }

    val downloadWorkInfo = workManager.getWorkInfosForUniqueWorkFlow("tg_download_all")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncWorkInfo = workManager.getWorkInfosForUniqueWorkFlow("tg_sync_now")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun resetDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
        }
    }

    // ── Backup & Restore ───────────────────────────────────────────────────

    fun exportBackup(onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val records = dao.getAllRecordsSync()
                val json = JSONObject()
                val array = JSONArray()
                records.forEach {
                    val obj = JSONObject()
                    obj.put("h", it.hash); obj.put("s", it.status); obj.put("fid", it.telegramFileId)
                    obj.put("mid", it.telegramMessageId); obj.put("sz", it.fileSize); obj.put("mt", it.mimeType)
                    obj.put("name", it.fileName); obj.put("fold", it.folderName)
                    array.put(obj)
                }
                json.put("history", array); json.put("bot", settings.botToken); json.put("chat", settings.chatId); json.put("key", settings.getEncryptionKey())
                json.toString()
            } catch (e: Exception) { null }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun importBackup(jsonStr: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = try {
                val json = JSONObject(jsonStr)
                val array = json.getJSONArray("history")
                settings.botToken = json.optString("bot", settings.botToken)
                settings.chatId = json.optString("chat", settings.chatId)
                json.optString("key", "").let { if (it.isNotBlank()) settings.setEncryptionKey(it) }
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    dao.insert(UploadedFile(
                        hash = obj.getString("h"), filePath = "", fileName = obj.optString("name", "Imported"),
                        fileSize = obj.optLong("sz", 0), uploadDate = System.currentTimeMillis(),
                        telegramMessageId = obj.optLong("mid", 0), telegramFileId = obj.optString("fid", ""),
                        status = obj.getString("s"), mimeType = obj.optString("mt", ""),
                        folderName = obj.optString("fold", ""),
                        dateModified = 0,
                        isDownloaded = false
                    ))
                }
                true
            } catch (e: Exception) { false }
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    // ── Cloud Sync ────────────────────────────────────────────────────────

    private val _cloudFolders = MutableStateFlow<Map<String, Int>?>(null)
    val cloudFolders: StateFlow<Map<String, Int>?> = _cloudFolders
    
    private var pendingCloudRecords: List<UploadedFile> = emptyList()

    fun restoreFromCloud(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            try {
                val chat = telegramApi.getChat(settings.botToken, settings.chatId)
                val pinned = chat.optJSONObject("pinned_message")
                if (pinned != null) {
                    val doc = pinned.optJSONObject("document")
                    if (doc != null) {
                        val bytes = telegramApi.downloadFile(settings.botToken, doc.getString("file_id"))
                        val json = JSONObject(String(bytes))
                        val array = json.getJSONArray("history")
                        json.optString("key", "").let { if (it.isNotBlank()) settings.setEncryptionKey(it) }
                        
                        val records = mutableListOf<UploadedFile>()
                        val folderCounts = mutableMapOf<String, Int>()
                        
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val folder = obj.optString("fold", "Root")
                            val record = UploadedFile(
                                hash = obj.getString("h"), 
                                filePath = "", 
                                fileName = obj.optString("name", "Imported"),
                                fileSize = obj.optLong("sz", 0), 
                                uploadDate = System.currentTimeMillis(),
                                telegramMessageId = obj.optLong("mid", 0), 
                                telegramFileId = obj.optString("fid", ""),
                                status = obj.getString("s"), 
                                mimeType = obj.optString("mt", ""),
                                folderName = folder,
                                dateModified = 0,
                                isDownloaded = true // Default to true, will set false for selected
                            )
                            records.add(record)
                            folderCounts[folder] = (folderCounts[folder] ?: 0) + 1
                        }
                        
                        pendingCloudRecords = records
                        _cloudFolders.value = folderCounts
                        _isCloudSyncing.value = false
                    } else throw Exception("Pinned message is not a backup file.")
                } else throw Exception("No pinned message found in the group. Use 'Push to Cloud' first.")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    _isCloudSyncing.value = false
                    Toast.makeText(getApplication(), "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                    onResult(false) 
                }
            }
        }
    }

    // One-tap DB-only restore: pulls the last pinned backup from Telegram and
    // repopulates the local Room DB directly — no folder-select dialog, and no
    // media is downloaded. Existing records are skipped by hash so this is safe
    // to run repeatedly, and it will not overwrite locally newer entries.
    fun restoreDbFromLastPin(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            try {
                val chat = telegramApi.getChat(settings.botToken, settings.chatId)
                val pinned = chat.optJSONObject("pinned_message")
                    ?: throw Exception("No pinned message found in the group. Use 'Backup DB' first.")
                val doc = pinned.optJSONObject("document")
                    ?: throw Exception("Pinned message is not a backup file.")

                val bytes = telegramApi.downloadFile(settings.botToken, doc.getString("file_id"))
                val json = JSONObject(String(bytes))
                val array = json.getJSONArray("history")
                json.optString("key", "").let { if (it.isNotBlank()) settings.setEncryptionKey(it) }

                var restoredCount = 0
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val hash = obj.getString("h")
                    // Skip anything already present locally so we never clobber
                    // newer local state with an older cloud snapshot.
                    if (dao.isHashUploaded(hash) != 0) continue

                    dao.insert(
                        UploadedFile(
                            hash = hash,
                            filePath = "",
                            fileName = obj.optString("name", "Imported"),
                            fileSize = obj.optLong("sz", 0),
                            uploadDate = System.currentTimeMillis(),
                            telegramMessageId = obj.optLong("mid", 0),
                            telegramFileId = obj.optString("fid", ""),
                            status = obj.optString("s", "success"),
                            mimeType = obj.optString("mt", ""),
                            folderName = obj.optString("fold", "Root"),
                            dateModified = 0,
                            isDownloaded = true // DB-only restore; media stays un-downloaded on device until user restores it
                        )
                    )
                    restoredCount++
                }

                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    Toast.makeText(getApplication(), "DB restored: $restoredCount record(s) added from last pin.", Toast.LENGTH_LONG).show()
                    onResult(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    Toast.makeText(getApplication(), "DB restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                    onResult(false)
                }
            }
        }
    }

    fun confirmCloudRestore(selectedFolders: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            pendingCloudRecords.forEach { record ->
                val shouldDownload = selectedFolders.contains(record.folderName)
                dao.insert(record.copy(isDownloaded = !shouldDownload))
            }
            _cloudFolders.value = null
            if (selectedFolders.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    downloadAll()
                }
            }
        }
    }

    fun cancelCloudRestore() {
        _cloudFolders.value = null
        pendingCloudRecords = emptyList()
    }

    fun backupToCloud(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            try {
                val records = dao.getAllRecordsSync()
                if (records.isEmpty()) throw Exception("Database is empty. Nothing to backup.")
                
                val json = JSONObject()
                val array = JSONArray()
                records.forEach {
                    val obj = JSONObject()
                    obj.put("h", it.hash); obj.put("s", it.status); obj.put("fid", it.telegramFileId)
                    obj.put("mid", it.telegramMessageId); obj.put("sz", it.fileSize); obj.put("mt", it.mimeType)
                    obj.put("name", it.fileName); obj.put("fold", it.folderName)
                    array.put(obj)
                }
                json.put("history", array); json.put("bot", settings.botToken); json.put("chat", settings.chatId); json.put("key", settings.getEncryptionKey())
                val result = telegramApi.uploadByteArray(settings.botToken, settings.chatId, json.toString().toByteArray(), "TGxMediaBackup_CloudBackup.json", "☁️ Cloud Backup\n#CloudSync")
                try { telegramApi.pinMessage(settings.botToken, settings.chatId, result.first) } catch (e: Exception) {}
                withContext(Dispatchers.Main) { 
                    _isCloudSyncing.value = false
                    Toast.makeText(getApplication(), "Backup pushed & pinned to Cloud!", Toast.LENGTH_SHORT).show()
                    onResult(true) 
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    _isCloudSyncing.value = false
                    Toast.makeText(getApplication(), "Cloud backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    onResult(false) 
                }
            }
        }
    }
}
