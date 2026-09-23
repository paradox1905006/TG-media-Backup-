package com.dparadox.tgbackup.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dparadox.tgbackup.MainActivity
import com.dparadox.tgbackup.R
import com.dparadox.tgbackup.TgBackupApplication
import com.dparadox.tgbackup.data.AppDatabase
import com.dparadox.tgbackup.data.DbBackupManager
import com.dparadox.tgbackup.data.FilePart
import com.dparadox.tgbackup.data.FileSyncEngine
import com.dparadox.tgbackup.data.SettingsManager
import com.dparadox.tgbackup.data.UploadedFile
import com.dparadox.tgbackup.network.RateLimitException
import com.dparadox.tgbackup.network.TelegramApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream

class SyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val NOTIFICATION_ID = 1001
        private const val UPLOAD_DELAY_MS = 1_200L
        private const val MAX_RETRIES = 5

        // WorkManager progress keys (read from Settings/Dashboard to show a
        // live upload % instead of just an indeterminate spinner).
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL   = "progress_total"
        const val KEY_PROGRESS_PHASE   = "progress_phase" // "scanning" | "uploading"
    }

    private val settings = SettingsManager(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.uploadedFileDao()
    private val filePartDao = db.filePartDao()
    private val telegramApi = TelegramApi(appContext.contentResolver)
    private val fileSyncEngine = FileSyncEngine(appContext)

    override suspend fun doWork(): Result {
        if (!settings.isConfigured()) return Result.success()
        if (settings.wifiOnly && !isOnWifi()) return Result.success()
        // Quiet-hours window only applies to the periodic (scheduled) worker,
        // never to a manually-triggered "Start Sync" (unique work name "tg_sync_now"
        // is a OneTimeWorkRequest the user tapped, so let it through immediately).
        if (settings.quietHoursEnabled && tags.contains("periodic") && !settings.isWithinQuietHours()) {
            Log.d(TAG, "Outside quiet-hours backup window, skipping this scheduled run.")
            return Result.success()
        }

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGxDParadox:SyncWakeLock")

        return try {
            wakeLock.acquire(6 * 60 * 60 * 1000L)
            try {
                setForeground(createForegroundInfo("Initializing full sync...", 0, 0))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set foreground at start: ${e.message}")
            }
            runSync()
            backupDatabaseToCloud()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private suspend fun runSync() {
        val botToken = settings.botToken
        val chatId   = settings.chatId
        val asDoc    = settings.uploadAsDocument
        val fullSync = settings.fullDeviceSyncEnabled
        val watched  = settings.watchedFolderUris
        val watchedMusicFolders = settings.watchedMusicFolders.toSet()
        val encryptionEnabled = settings.encryptionEnabled
        val encryptionKey = settings.getEncryptionKey()
        
        val selectedHashes = db.selectedMediaDao().getAllSelectedSync().map { it.hash }.toSet()

        val allFiles = mutableListOf<FileSyncEngine.MediaFile>()
        val hashesFromWatchedFolders = mutableSetOf<String>()

        // 1. Scan System MediaStore
        val mediaStoreFiles = fileSyncEngine.scanAllMedia(
            onProgress = { scanned, total ->
                try {
                    setForeground(createForegroundInfo("Scanning storage: $scanned / $total", scanned, total))
                    setProgress(workDataOf(
                        KEY_PROGRESS_PHASE to "scanning",
                        KEY_PROGRESS_CURRENT to scanned,
                        KEY_PROGRESS_TOTAL to total
                    ))
                } catch (ignored: Exception) {}
            },
            dbLookup = { size, mod -> dao.findHashBySizeAndDate(size, mod) },
            includeAudio = settings.includeMusicBackup
        )
        allFiles.addAll(mediaStoreFiles)
        
        // Mark files from watched MediaStore folders
        val watchedSet = watched.toSet()
        mediaStoreFiles.forEach { 
            if (it.folderName in watchedSet) {
                hashesFromWatchedFolders.add(it.hash)
            }
        }

        // 2. Scan Custom SAF Folders (if any)
        val customUris = watched.filter { it.startsWith("content://") }
        customUris.forEach { uriStr ->
            try {
                val safFiles = fileSyncEngine.scanSafFolder(
                    android.net.Uri.parse(uriStr),
                    dbLookup = { size, mod -> dao.findHashBySizeAndDate(size, mod) },
                    includeAudio = settings.includeMusicBackup
                )
                allFiles.addAll(safFiles)
                // All files in a watched SAF folder are included
                safFiles.forEach { hashesFromWatchedFolders.add(it.hash) }
            } catch (e: Exception) {
                Log.e(TAG, "SAF scan failed: $uriStr", e)
            }
        }

        // Music has its own auto/selective switch, independent of the photo &
        // video "Full Device Backup" toggle — mirrors the Folders screen's
        // auto-vs-manual model, just scoped to audio files. Selection can be
        // by whole folder (watchedMusicFolders) or by individual track hash.
        val autoBackupMusic = settings.autoBackupMusicEnabled
        val filteredFiles = allFiles.filter { file ->
            if (file.mimeType.startsWith("audio/")) {
                autoBackupMusic || watchedMusicFolders.contains(file.folderName) || selectedHashes.contains(file.hash)
            } else if (fullSync) {
                true
            } else {
                // Manual selection mode: include both watched folders AND specific hashes
                hashesFromWatchedFolders.contains(file.hash) || selectedHashes.contains(file.hash)
            }
        }

        val uniqueFiles = filteredFiles.distinctBy { it.hash }
            .sortedWith(compareBy({ it.folderName }, { it.dateModified }))
        
        // Include both new files and previously failed ones
        val toUpload = uniqueFiles.filter { file ->
            if (isStopped) return
            val status = dao.isHashUploaded(file.hash)
            status == 0 // We'll re-upload if not successfully uploaded. 
            // Note: UploadedFileDao.isHashUploaded returns count of 'success' records.
        }

        toUpload.forEachIndexed { index, file ->
            if (isStopped) return@runSync
            
            // PAUSE LOGIC
            while (settings.syncPaused && !isStopped) {
                delay(2000L)
            }

            try {
                setForeground(createForegroundInfo("Uploading ${index + 1} / ${toUpload.size}", index + 1, toUpload.size))
                setProgress(workDataOf(
                    KEY_PROGRESS_PHASE to "uploading",
                    KEY_PROGRESS_CURRENT to index + 1,
                    KEY_PROGRESS_TOTAL to toUpload.size
                ))
            } catch (ignored: Exception) {}

            val threadId = getOrCreateTopic(botToken, chatId, file.folderName)

            // Large files can be automatically broken into <=18MB chunks and
            // uploaded as a sequence of documents (restore reassembles them
            // transparently — see DownloadWorker) — but only when the user has
            // split-upload turned on in Settings. If it's off, big files are
            // parked as "too_large" and skipped entirely, same as before
            // split-upload existed.
            if (file.sizeBytes > FileSyncEngine.MAX_UPLOAD_BYTES) {
                if (!settings.splitUploadEnabled) {
                    dao.insert(UploadedFile(
                        hash = file.hash,
                        filePath = file.uri.toString(),
                        fileName = file.displayName,
                        fileSize = file.sizeBytes,
                        uploadDate = System.currentTimeMillis(),
                        telegramMessageId = 0,
                        status = "too_large",
                        mimeType = file.mimeType,
                        folderName = file.folderName,
                        dateModified = file.dateModified
                    ))
                    delay(UPLOAD_DELAY_MS)
                    return@forEachIndexed
                }
                val parts = uploadSplitWithRetry(botToken, chatId, file, encryptionEnabled, encryptionKey, threadId)
                if (parts != null && parts.isNotEmpty()) {
                    filePartDao.deletePartsForHash(file.hash) // clear any stale partial attempt from a previous failed run
                    filePartDao.insertAll(parts)
                    dao.insert(UploadedFile(
                        hash = file.hash,
                        filePath = file.uri.toString(),
                        fileName = if (encryptionEnabled) file.displayName + ".enc" else file.displayName,
                        fileSize = file.sizeBytes,
                        uploadDate = System.currentTimeMillis(),
                        telegramMessageId = parts.first().telegramMessageId,
                        telegramFileId = "", // bytes live across file_parts, not a single file_id
                        status = "success",
                        mimeType = file.mimeType,
                        folderName = file.folderName,
                        dateModified = file.dateModified,
                        isSplit = true
                    ))
                } else {
                    dao.insert(UploadedFile(
                        hash = file.hash,
                        filePath = file.uri.toString(),
                        fileName = file.displayName,
                        fileSize = file.sizeBytes,
                        uploadDate = System.currentTimeMillis(),
                        telegramMessageId = 0,
                        status = "failed",
                        mimeType = file.mimeType,
                        folderName = file.folderName,
                        dateModified = file.dateModified
                    ))
                }
                delay(UPLOAD_DELAY_MS)
                return@forEachIndexed
            }

            val result = if (encryptionEnabled) {
                uploadEncryptedWithRetry(botToken, chatId, file, encryptionKey, threadId)
            } else {
                uploadWithRetry(botToken, chatId, file, asDoc, threadId)
            }

            if (result != null) {
                dao.insert(UploadedFile(
                    hash = file.hash,
                    filePath = file.uri.toString(),
                    fileName = if (encryptionEnabled) file.displayName + ".enc" else file.displayName,
                    fileSize = file.sizeBytes,
                    uploadDate = System.currentTimeMillis(),
                    telegramMessageId = result.first,
                    telegramFileId = result.second,
                    status = "success",
                    mimeType = file.mimeType,
                    folderName = file.folderName,
                    dateModified = file.dateModified
                ))
            } else {
                dao.insert(UploadedFile(
                    hash = file.hash,
                    filePath = file.uri.toString(),
                    fileName = file.displayName,
                    fileSize = file.sizeBytes,
                    uploadDate = System.currentTimeMillis(),
                    telegramMessageId = 0,
                    status = "failed",
                    mimeType = file.mimeType,
                    folderName = file.folderName,
                    dateModified = file.dateModified
                ))
            }
            delay(UPLOAD_DELAY_MS)
        }
    }

    private suspend fun uploadEncryptedWithRetry(
        botToken: String,
        chatId: String,
        file: FileSyncEngine.MediaFile,
        key: String,
        threadId: Int?
    ): Pair<Long, String>? = withContext(Dispatchers.IO) {
        try {
            val inputStream = appContext.contentResolver.openInputStream(file.uri) ?: return@withContext null
            val bytes = inputStream.readBytes()
            val encryptedBytes = com.dparadox.tgbackup.data.EncryptionUtils.encrypt(bytes, key)
            
            for (attempt in 0 until MAX_RETRIES) {
                if (isStopped) return@withContext null
                try {
                    return@withContext telegramApi.uploadByteArray(
                        botToken, 
                        chatId, 
                        encryptedBytes, 
                        file.displayName + ".enc", 
                        "🔒 Encrypted Media: ${file.displayName}",
                        threadId
                    )
                } catch (e: RateLimitException) {
                    delay(e.retryAfterSeconds * 1000L + 500L)
                } catch (e: Exception) {
                    val waitMs = (Math.pow(2.0, attempt.toDouble()) * 2000L).toLong()
                    delay(waitMs)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Encryption upload failed", e)
        }
        null
    }

    // ── Split-upload (files larger than the 50MB Bot API limit) ──────────

    /**
     * Uploads [file] as a sequence of <=SPLIT_CHUNK_BYTES chunks and returns
     * the ordered list of [FilePart] rows to persist, or null if the upload
     * could not complete (caller marks the whole file "failed" and it will
     * be retried on the next sync — no partial state is left behind since
     * failure here means no FilePart rows get written for this attempt).
     *
     * Encrypted files are encrypted once as a whole (same as the existing
     * single-file encrypted path), then the resulting ciphertext is split.
     * Unencrypted files are streamed directly from disk in chunks, so a
     * multi-GB video is never fully loaded into memory.
     */
    private suspend fun uploadSplitWithRetry(
        botToken: String,
        chatId: String,
        file: FileSyncEngine.MediaFile,
        encryptionEnabled: Boolean,
        encryptionKey: String,
        threadId: Int?
    ): List<FilePart>? = withContext(Dispatchers.IO) {
        try {
            if (encryptionEnabled) {
                val raw = appContext.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                    ?: return@withContext null
                val encrypted = com.dparadox.tgbackup.data.EncryptionUtils.encrypt(raw, encryptionKey)
                uploadChunksFromByteArray(botToken, chatId, encrypted, file, threadId, nameSuffix = ".enc")
            } else {
                val stream = appContext.contentResolver.openInputStream(file.uri) ?: return@withContext null
                stream.use { uploadChunksFromStream(botToken, chatId, it, file, threadId) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Split upload failed for ${file.displayName}", e)
            null
        }
    }

    private suspend fun uploadChunksFromByteArray(
        botToken: String,
        chatId: String,
        bytes: ByteArray,
        file: FileSyncEngine.MediaFile,
        threadId: Int?,
        nameSuffix: String
    ): List<FilePart>? {
        val chunkSize = FileSyncEngine.SPLIT_CHUNK_BYTES.toInt()
        val totalParts = ((bytes.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)
        val parts = mutableListOf<FilePart>()
        for (partIndex in 0 until totalParts) {
            if (isStopped) return null
            val start = partIndex * chunkSize
            val end = minOf(start + chunkSize, bytes.size)
            val chunk = bytes.copyOfRange(start, end)
            val uploaded = uploadOneChunkWithRetry(botToken, chatId, chunk, file, partIndex, totalParts, threadId, nameSuffix)
                ?: return null
            parts += FilePart(file.hash, partIndex, totalParts, uploaded.first, uploaded.second, chunk.size.toLong())
            delay(UPLOAD_DELAY_MS)
        }
        return parts
    }

    private suspend fun uploadChunksFromStream(
        botToken: String,
        chatId: String,
        stream: InputStream,
        file: FileSyncEngine.MediaFile,
        threadId: Int?
    ): List<FilePart>? {
        val chunkSize = FileSyncEngine.SPLIT_CHUNK_BYTES.toInt()
        val totalParts = ((file.sizeBytes + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
        val parts = mutableListOf<FilePart>()
        var partIndex = 0
        while (true) {
            if (isStopped) return null
            val buffer = ByteArray(chunkSize)
            var offset = 0
            while (offset < buffer.size) {
                val read = stream.read(buffer, offset, buffer.size - offset)
                if (read == -1) break
                offset += read
            }
            if (offset == 0) break
            val chunk = if (offset == buffer.size) buffer else buffer.copyOf(offset)
            val uploaded = uploadOneChunkWithRetry(botToken, chatId, chunk, file, partIndex, totalParts, threadId, "")
                ?: return null
            parts += FilePart(file.hash, partIndex, totalParts, uploaded.first, uploaded.second, chunk.size.toLong())
            partIndex++
            delay(UPLOAD_DELAY_MS)
        }
        return parts
    }

    private suspend fun uploadOneChunkWithRetry(
        botToken: String,
        chatId: String,
        chunk: ByteArray,
        file: FileSyncEngine.MediaFile,
        partIndex: Int,
        totalParts: Int,
        threadId: Int?,
        nameSuffix: String
    ): Pair<Long, String>? {
        try {
            setForeground(createForegroundInfo(
                "Uploading ${file.displayName} — part ${partIndex + 1}/$totalParts",
                partIndex + 1, totalParts
            ))
            setProgress(workDataOf(
                KEY_PROGRESS_PHASE to "uploading_part",
                KEY_PROGRESS_CURRENT to partIndex + 1,
                KEY_PROGRESS_TOTAL to totalParts
            ))
        } catch (ignored: Exception) {}

        for (attempt in 0 until MAX_RETRIES) {
            if (isStopped) return null
            try {
                return telegramApi.uploadByteArray(
                    botToken, chatId, chunk,
                    "${file.displayName}$nameSuffix.part${partIndex + 1}of$totalParts",
                    "📦 ${file.displayName} — part ${partIndex + 1}/$totalParts",
                    threadId
                )
            } catch (e: RateLimitException) {
                delay(e.retryAfterSeconds * 1000L + 500L)
            } catch (e: Exception) {
                delay((Math.pow(2.0, attempt.toDouble()) * 2000L).toLong())
            }
        }
        return null
    }

    private fun getOrCreateTopic(botToken: String, chatId: String, folderName: String): Int? {
        // Use a default name for files in the root directory to keep them organized too
        val safeFolderName = if (folderName.isBlank() || folderName == "Root") "General Media" else folderName
        
        settings.getTopicId(safeFolderName)?.let { return it }
        
        return try {
            Log.d(TAG, "Attempting to create/find topic for: $safeFolderName")
            val id = telegramApi.createForumTopic(botToken, chatId, safeFolderName)
            settings.saveTopicId(safeFolderName, id)
            id
        } catch (e: Exception) {
            Log.e(TAG, "Topic creation failed for '$safeFolderName': ${e.message}")
            // If it failed because it's not a forum, we'll get an error in validation or here.
            // We return null to upload to the General/Main chat as a fallback.
            null
        }
    }

    private suspend fun uploadWithRetry(
        botToken: String,
        chatId: String,
        file: FileSyncEngine.MediaFile,
        asDoc: Boolean,
        threadId: Int?
    ): Pair<Long, String>? = withContext(Dispatchers.IO) {
        for (attempt in 0 until MAX_RETRIES) {
            if (isStopped) return@withContext null
            try {
                return@withContext telegramApi.uploadMedia(botToken, chatId, file.uri, file.displayName, file.mimeType, asDoc, threadId)
            } catch (e: RateLimitException) {
                delay(e.retryAfterSeconds * 1000L + 500L)
            } catch (e: Exception) {
                val waitMs = (Math.pow(2.0, attempt.toDouble()) * 2000L).toLong()
                delay(waitMs)
            }
        }
        null
    }

    private suspend fun backupDatabaseToCloud() = withContext(Dispatchers.IO) {
        if (!settings.dbBackupEnabled) return@withContext
        try {
            DbBackupManager.pushBackup(
                telegramApi, settings, dao, filePartDao,
                captionPrefix = "🔄 Automatic Database Backup"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Auto backup failed", e)
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun createForegroundInfo(message: String, progress: Int, maxProgress: Int): ForegroundInfo {
        val intent = Intent(appContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(appContext, TgBackupApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Sync Active")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .apply { if (maxProgress > 0) setProgress(maxProgress, progress, false) else setProgress(0, 0, true) }
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
