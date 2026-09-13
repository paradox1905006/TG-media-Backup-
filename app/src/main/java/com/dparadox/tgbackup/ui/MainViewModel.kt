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
import com.dparadox.tgbackup.data.FilePart
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
import android.content.ContentUris
import android.provider.MediaStore
import android.media.MediaPlayer
import android.util.Log

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
        val music: Int = 0,
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

    // ── Appearance (light / dark) ─────────────────────────────────────────
    private val _darkModeEnabled = MutableStateFlow(settings.darkModeEnabled)
    val darkModeEnabled: StateFlow<Boolean> = _darkModeEnabled

    fun setDarkModeEnabled(enabled: Boolean) {
        settings.darkModeEnabled = enabled
        _darkModeEnabled.value = enabled
    }

    // ── Music backup ───────────────────────────────────────────────────────
    private val _includeMusicBackup = MutableStateFlow(settings.includeMusicBackup)
    val includeMusicBackup: StateFlow<Boolean> = _includeMusicBackup

    fun setIncludeMusicBackup(enabled: Boolean) {
        settings.includeMusicBackup = enabled
        _includeMusicBackup.value = enabled
    }

    // When true, all music is backed up automatically (like Folders' "Full
    // Device Backup"). When false, only tracks the user has checked below are.
    private val _autoBackupMusicEnabled = MutableStateFlow(settings.autoBackupMusicEnabled)
    val autoBackupMusicEnabled: StateFlow<Boolean> = _autoBackupMusicEnabled

    fun setAutoBackupMusicEnabled(enabled: Boolean) {
        settings.autoBackupMusicEnabled = enabled
        _autoBackupMusicEnabled.value = enabled
    }

    // Folder-level music selection — mirrors watchedFolderUris/toggleFolder,
    // scoped to folders discovered from the device's audio library.
    private val _discoveredMusicFolders = MutableStateFlow<List<String>>(emptyList())
    val discoveredMusicFolders: StateFlow<List<String>> = _discoveredMusicFolders

    private val _watchedMusicFolders = MutableStateFlow(settings.watchedMusicFolders.toSet())
    val watchedMusicFolders: StateFlow<Set<String>> = _watchedMusicFolders

    fun discoverMusicFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            _discoveredMusicFolders.value = fileSyncEngine.getAllAudioFoldersOnDevice()
        }
    }

    fun toggleMusicFolder(folder: String) {
        val current = settings.watchedMusicFolders.toMutableList()
        if (current.contains(folder)) current.remove(folder) else current.add(folder)
        settings.watchedMusicFolders = current
        _watchedMusicFolders.value = current.toSet()
    }

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
                    music     = summary.music,
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
            // FIX: syncNow() calls Toast.makeText(), which requires the main/UI
            //      thread (it needs a Looper). This whole block was running on
            //      Dispatchers.IO, so calling syncNow() straight from here threw
            //      "Can't toast on a thread that has not called Looper.prepare()"
            //      and crashed mid-sync — silently aborting every upload in that
            //      run (which is why retried files, and unrelated files queued
            //      in the same run, kept coming back "Failed").
            withContext(Dispatchers.Main) {
                syncNow()
            }
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
            .addTag("periodic")
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

    // Holds the pending-restore file count while the confirmation dialog is
    // showing; null means no preview dialog is active. Set by
    // requestRestorePreview(), cleared by confirmRestoreFromPreview()/dismissRestorePreview().
    private val _restorePreviewCount = MutableStateFlow<Int?>(null)
    val restorePreviewCount: StateFlow<Int?> = _restorePreviewCount

    /** Counts files that would be downloaded and shows a confirmation dialog, without starting anything yet. */
    fun requestRestorePreview() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = dao.getAllRecordsSync().count { it.telegramFileId.isNotEmpty() || it.isSplit }
            withContext(Dispatchers.Main) {
                if (count == 0) {
                    Toast.makeText(getApplication(), "No files found in database to download. Sync or Restore first.", Toast.LENGTH_LONG).show()
                } else {
                    _restorePreviewCount.value = count
                }
            }
        }
    }

    fun dismissRestorePreview() {
        _restorePreviewCount.value = null
    }

    /** Actually starts the restore after the user confirms the preview dialog. */
    fun confirmRestoreFromPreview() {
        _restorePreviewCount.value = null
        downloadAll()
    }

    fun downloadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = dao.getAllRecordsSync().filter { it.telegramFileId.isNotEmpty() || it.isSplit }
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

    /** 0-100, or null while indeterminate/idle. Read from SyncWorker.setProgress(). */
    val syncProgressPercent: StateFlow<Int?> = syncWorkInfo
        .map { infos ->
            val active = infos.firstOrNull { !it.state.isFinished } ?: return@map null
            val current = active.progress.getInt(SyncWorker.KEY_PROGRESS_CURRENT, 0)
            val total = active.progress.getInt(SyncWorker.KEY_PROGRESS_TOTAL, 0)
            if (total <= 0) null else ((current * 100) / total).coerceIn(0, 100)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 0-100, or null while indeterminate/idle. Read from DownloadWorker.setProgress(). */
    val downloadProgressPercent: StateFlow<Int?> = downloadWorkInfo
        .map { infos ->
            val active = infos.firstOrNull { !it.state.isFinished } ?: return@map null
            val current = active.progress.getInt(DownloadWorker.KEY_PROGRESS_CURRENT, 0)
            val total = active.progress.getInt(DownloadWorker.KEY_PROGRESS_TOTAL, 0)
            if (total <= 0) null else ((current * 100) / total).coerceIn(0, 100)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
    // hash -> chunk list, for any pending record where "split" was true in the
    // backup JSON. Populated alongside pendingCloudRecords in restoreFromCloud()
    // and written to file_parts only for the folders the user actually selects.
    private var pendingCloudParts: Map<String, List<FilePart>> = emptyMap()

    /** Parses a single history entry from the DB-backup JSON into an UploadedFile
     *  plus (if it was a split upload) its list of FilePart chunks. The "split"
     *  and "parts" fields are written by [DbBackupManager.buildBackupJson] —
     *  see that function for the exact key names used below.
     */
    private fun parseBackupEntry(obj: JSONObject): Pair<UploadedFile, List<FilePart>> {
        val hash = obj.getString("h")
        val isSplit = obj.optBoolean("split", false)
        val record = UploadedFile(
            hash = hash,
            filePath = "",
            fileName = obj.optString("name", "Imported"),
            fileSize = obj.optLong("sz", 0),
            uploadDate = System.currentTimeMillis(),
            telegramMessageId = obj.optLong("mid", 0),
            telegramFileId = obj.optString("fid", ""),
            status = obj.getString("s"),
            mimeType = obj.optString("mt", ""),
            folderName = obj.optString("fold", "Root"),
            dateModified = 0,
            isDownloaded = true, // caller overrides per-selection
            isSplit = isSplit
        )
        val parts = if (isSplit) {
            val partsArr = obj.optJSONArray("parts") ?: JSONArray()
            (0 until partsArr.length()).map { i ->
                val po = partsArr.getJSONObject(i)
                FilePart(
                    hash = hash,
                    partIndex = po.getInt("pi"),
                    totalParts = po.getInt("tp"),
                    telegramMessageId = po.getLong("mid"),
                    telegramFileId = po.getString("fid"),
                    partSize = po.optLong("ps", 0)
                )
            }
        } else emptyList()
        return record to parts
    }

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
                        val partsMap = mutableMapOf<String, List<FilePart>>()
                        val folderCounts = mutableMapOf<String, Int>()
                        
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val (record, parts) = parseBackupEntry(obj)
                            records.add(record)
                            if (parts.isNotEmpty()) partsMap[record.hash] = parts
                            folderCounts[record.folderName] = (folderCounts[record.folderName] ?: 0) + 1
                        }
                        
                        pendingCloudRecords = records
                        pendingCloudParts = partsMap
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

                    val (record, parts) = parseBackupEntry(obj)
                    dao.insert(record) // isDownloaded = true here — DB-only restore; media stays un-downloaded on device until user restores it
                    if (parts.isNotEmpty()) {
                        filePartDao.deletePartsForHash(hash)
                        filePartDao.insertAll(parts)
                    }
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
                // Split files also need their chunk map written to file_parts,
                // or DownloadWorker.getFilesToDownload() will never match this
                // record (it requires isSplit=1 in the DB, not just in this
                // in-memory copy) and the file silently never restores.
                if (shouldDownload && record.isSplit) {
                    pendingCloudParts[record.hash]?.let { parts ->
                        filePartDao.deletePartsForHash(record.hash)
                        filePartDao.insertAll(parts)
                    }
                }
            }
            _cloudFolders.value = null
            pendingCloudParts = emptyMap()
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
        pendingCloudParts = emptyMap()
    }

    fun backupToCloud(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            try {
                val pushed = com.dparadox.tgbackup.data.DbBackupManager.pushBackup(
                    telegramApi, settings, dao, filePartDao,
                    captionPrefix = "☁️ Cloud Backup"
                )
                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    Toast.makeText(
                        getApplication(),
                        if (pushed) "Backup pushed & pinned to Cloud!" else "Database unchanged since last backup — nothing to push.",
                        Toast.LENGTH_SHORT
                    ).show()
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

    // ── Migrate to New Bot ──────────────────────────────────────────────
    // Used when the old bot token was revoked/lost. Recovers the DB backup
    // (auto-discovered from the group's pinned message, or manually
    // imported) and re-links every file to the new bot via forwardMessage,
    // without ever needing the old bot's token. See MigrationWorker.

    private val filePartDao = db.filePartDao()

    private val _migrationState = MutableStateFlow<MigrationState?>(null)
    val migrationState: StateFlow<MigrationState?> = _migrationState

    data class MigrationState(val running: Boolean, val error: String? = null, val migrated: Int = 0, val skipped: Int = 0)

    /**
     * Copies a user-picked backup .json file into app-private storage and
     * returns its path — used when auto-discovery can't find the old
     * backup (e.g. it wasn't pinned, or the group history is unavailable).
     */
    fun importManualBackupFile(uri: Uri): String? {
        return try {
            val target = java.io.File(getApplication<Application>().filesDir, "manual_migration_import.json")
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "Couldn't read that file: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    fun startMigration(newBotToken: String, chatId: String, manualBackupPath: String?) {
        if (newBotToken.isBlank() || chatId.isBlank()) {
            Toast.makeText(getApplication(), "Enter the new bot token and the group's chat ID first.", Toast.LENGTH_LONG).show()
            return
        }
        _migrationState.value = MigrationState(running = true)

        val inputData = androidx.work.Data.Builder()
            .putString(com.dparadox.tgbackup.worker.MigrationWorker.KEY_NEW_BOT_TOKEN, newBotToken)
            .putString(com.dparadox.tgbackup.worker.MigrationWorker.KEY_CHAT_ID, chatId)
            .apply { if (!manualBackupPath.isNullOrBlank()) putString(com.dparadox.tgbackup.worker.MigrationWorker.KEY_MANUAL_BACKUP_PATH, manualBackupPath) }
            .build()

        val request = OneTimeWorkRequestBuilder<com.dparadox.tgbackup.worker.MigrationWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork("tg_migration", ExistingWorkPolicy.REPLACE, request)
        Toast.makeText(getApplication(), "Migration started — you'll see progress in the notification.", Toast.LENGTH_SHORT).show()

        workManager.getWorkInfoByIdLiveData(request.id).observeForever(object : androidx.lifecycle.Observer<androidx.work.WorkInfo> {
            override fun onChanged(info: androidx.work.WorkInfo) {
                if (info.state.isFinished) {
                    workManager.getWorkInfoByIdLiveData(request.id).removeObserver(this)
                    when (info.state) {
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            val migrated = info.outputData.getInt(com.dparadox.tgbackup.worker.MigrationWorker.KEY_RESULT_MIGRATED, 0)
                            val skipped  = info.outputData.getInt(com.dparadox.tgbackup.worker.MigrationWorker.KEY_RESULT_SKIPPED, 0)
                            _migrationState.value = MigrationState(running = false, migrated = migrated, skipped = skipped)
                            Toast.makeText(getApplication(), "Migration complete — $migrated migrated, $skipped skipped.", Toast.LENGTH_LONG).show()
                            schedulePeriodicSync()
                            scheduleDbBackup()
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            val error = info.outputData.getString(com.dparadox.tgbackup.worker.MigrationWorker.KEY_RESULT_ERROR) ?: "Unknown error"
                            _migrationState.value = MigrationState(running = false, error = error)
                            Toast.makeText(getApplication(), "Migration failed: $error", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            _migrationState.value = MigrationState(running = false, error = "Migration was cancelled.")
                        }
                    }
                }
            }
        })
    }

    // ── Music section ────────────────────────────────────────────────────
    // A real dedicated section (not just the Settings toggle) for browsing
    // and playing back the device's music library, with a per-track backup
    // status badge (matched against UploadedFile by content URI).

    data class MusicTrack(
        val mediaStoreId: Long,
        val uri: Uri,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val sizeBytes: Long,
        val mimeType: String,
        val isBackedUp: Boolean,
        val hash: String?,
        val folderName: String
    )

    private val _musicTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val musicTracks: StateFlow<List<MusicTrack>> = _musicTracks

    private val _isLoadingMusic = MutableStateFlow(false)
    val isLoadingMusic: StateFlow<Boolean> = _isLoadingMusic

    private val _nowPlayingId = MutableStateFlow<Long?>(null)
    val nowPlayingId: StateFlow<Long?> = _nowPlayingId

    private val _isMusicPlaying = MutableStateFlow(false)
    val isMusicPlaying: StateFlow<Boolean> = _isMusicPlaying

    private var mediaPlayer: MediaPlayer? = null

    fun loadMusicTracks() {
        discoverMusicFolders()
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMusic.value = true
            try {
                val backedUpPaths = dao.getAllRecordsSync()
                    .filter { it.status == "success" }
                    .map { it.filePath }
                    .toSet()

                val tracks = mutableListOf<MusicTrack>()
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

                getApplication<Application>().contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val folderCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    }

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                            ?: cursor.getString(nameCol) ?: "Unknown"
                        val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist"
                        val album = cursor.getString(albumCol) ?: ""
                        val duration = cursor.getLong(durationCol)
                        val size = cursor.getLong(sizeCol)
                        val mime = cursor.getString(mimeCol) ?: "audio/*"
                        val modified = cursor.getLong(modifiedCol)
                        val rawFolder = cursor.getString(folderCol) ?: ""
                        val folderName = (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            rawFolder.trim('/')
                        } else {
                            rawFolder.substringBeforeLast('/').substringAfterLast('/')
                        }).ifBlank { "Root" }
                        // Same hash the sync worker computes, so a track checked here
                        // for selective backup is recognized as "selected" during sync.
                        val hash = try {
                            fileSyncEngine.getFileHash(uri, size, modified) { s, m -> dao.findHashBySizeAndDate(s, m) }
                        } catch (e: Exception) {
                            null
                        }

                        tracks += MusicTrack(
                            mediaStoreId = id,
                            uri = uri,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            sizeBytes = size,
                            mimeType = mime,
                            isBackedUp = backedUpPaths.contains(uri.toString()),
                            hash = hash,
                            folderName = folderName
                        )
                    }
                }
                _musicTracks.value = tracks
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to load music tracks", e)
            } finally {
                _isLoadingMusic.value = false
            }
        }
    }

    /** Play/pause a track. Tapping the currently-playing track toggles pause/resume; tapping another switches to it. */
    fun toggleMusicPlayback(track: MusicTrack) {
        if (_nowPlayingId.value == track.mediaStoreId && mediaPlayer != null) {
            if (_isMusicPlaying.value) {
                mediaPlayer?.pause()
                _isMusicPlaying.value = false
            } else {
                mediaPlayer?.start()
                _isMusicPlaying.value = true
            }
            return
        }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(getApplication(), track.uri)
                setOnCompletionListener {
                    _isMusicPlaying.value = false
                    _nowPlayingId.value = null
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(getApplication(), "Couldn't play ${track.title}", Toast.LENGTH_SHORT).show()
                    _isMusicPlaying.value = false
                    _nowPlayingId.value = null
                    true
                }
                prepare()
                start()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Playback failed for ${track.title}", e)
                Toast.makeText(getApplication(), "Couldn't play ${track.title}", Toast.LENGTH_SHORT).show()
            }
        }
        _nowPlayingId.value = track.mediaStoreId
        _isMusicPlaying.value = true
    }

    fun stopMusicPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        _nowPlayingId.value = null
        _isMusicPlaying.value = false
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
