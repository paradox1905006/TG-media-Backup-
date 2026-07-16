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
import com.dparadox.tgbackup.MainActivity
import com.dparadox.tgbackup.R
import com.dparadox.tgbackup.TgBackupApplication
import com.dparadox.tgbackup.data.AppDatabase
import com.dparadox.tgbackup.data.FileSyncEngine
import com.dparadox.tgbackup.data.SettingsManager
import com.dparadox.tgbackup.data.UploadedFile
import com.dparadox.tgbackup.network.RateLimitException
import com.dparadox.tgbackup.network.TelegramApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val NOTIFICATION_ID = 1001
        private const val UPLOAD_DELAY_MS = 1_200L
        private const val MAX_RETRIES = 5
    }

    private val settings = SettingsManager(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.uploadedFileDao()
    private val telegramApi = TelegramApi(appContext.contentResolver)
    private val fileSyncEngine = FileSyncEngine(appContext)

    override suspend fun doWork(): Result {
        if (!settings.isConfigured()) return Result.success()
        if (settings.wifiOnly && !isOnWifi()) return Result.success()

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGxDParadox:SyncWakeLock")

        return try {
            wakeLock.acquire(6 * 60 * 60 * 1000L)
            setForeground(createForegroundInfo("Initializing full sync...", 0, 0))
            runSync()
            backupDatabaseToCloud()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            Result.retry()
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

        val allFiles = fileSyncEngine.scanAllMedia { scanned, total ->
            try {
                setForeground(createForegroundInfo("Scanning storage: $scanned / $total", scanned, total))
            } catch (ignored: Exception) {}
        }

        val filteredFiles = if (fullSync) {
            allFiles
        } else {
            // Match files that are in a watched folder OR any of its subfolders
            allFiles.filter { file ->
                watched.any { watchedPath ->
                    file.folderName == watchedPath || file.folderName.startsWith("$watchedPath/")
                }
            }
        }

        val uniqueFiles = filteredFiles.distinctBy { it.hash }
            .sortedWith(compareBy({ it.folderName }, { it.dateModified }))
        val toUpload = uniqueFiles.filter { file ->
            if (isStopped) return
            dao.isHashUploaded(file.hash) == 0
        }

        toUpload.forEachIndexed { index, file ->
            if (isStopped) return@runSync

            try {
                setForeground(createForegroundInfo("Uploading ${index + 1} / ${toUpload.size}", index + 1, toUpload.size))
            } catch (ignored: Exception) {}

            val threadId = getOrCreateTopic(botToken, chatId, file.folderName)

            if (file.sizeBytes > FileSyncEngine.MAX_UPLOAD_BYTES) {
                dao.insert(UploadedFile(
                    hash = file.hash,
                    filePath = file.uri.toString(),
                    fileName = file.displayName,
                    fileSize = file.sizeBytes,
                    uploadDate = System.currentTimeMillis(),
                    telegramMessageId = 0,
                    status = "too_large",
                    mimeType = file.mimeType,
                    folderName = file.folderName
                ))
                return@forEachIndexed
            }

            val result = uploadWithRetry(botToken, chatId, file, asDoc, threadId)

            if (result != null) {
                dao.insert(UploadedFile(
                    hash = file.hash,
                    filePath = file.uri.toString(),
                    fileName = file.displayName,
                    fileSize = file.sizeBytes,
                    uploadDate = System.currentTimeMillis(),
                    telegramMessageId = result.first,
                    telegramFileId = result.second,
                    status = "success",
                    mimeType = file.mimeType,
                    folderName = file.folderName
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
                    folderName = file.folderName
                ))
            }
            delay(UPLOAD_DELAY_MS)
        }
    }

    private fun getOrCreateTopic(botToken: String, chatId: String, folderName: String): Int? {
        if (folderName.isBlank() || folderName == "Root") return null
        
        settings.getTopicId(folderName)?.let { return it }
        
        return try {
            val id = telegramApi.createForumTopic(botToken, chatId, folderName)
            settings.saveTopicId(folderName, id)
            id
        } catch (e: Exception) {
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
            val records = dao.getAllRecordsSync()
            if (records.isEmpty()) return@withContext
            
            val json = JSONObject()
            val array = JSONArray()
            records.forEach {
                val obj = JSONObject()
                obj.put("h", it.hash); obj.put("s", it.status); obj.put("fid", it.telegramFileId)
                obj.put("mid", it.telegramMessageId); obj.put("sz", it.fileSize); obj.put("mt", it.mimeType)
                obj.put("name", it.fileName)
                array.put(obj)
            }
            json.put("history", array)
            json.put("bot", settings.botToken)
            json.put("chat", settings.chatId)
            
            val fileName = "TGxDParadox_AutoBackup_${System.currentTimeMillis()}.json"
            val msgId = telegramApi.uploadByteArray(
                settings.botToken, 
                settings.chatId, 
                json.toString().toByteArray(), 
                fileName, 
                "🔄 Automatic Database Backup\n#AutoBackup"
            )
            try { telegramApi.pinMessage(settings.botToken, settings.chatId, msgId) } catch (e: Exception) {}
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
