package com.dparadox.tgbackup.worker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.dparadox.tgbackup.MainActivity
import com.dparadox.tgbackup.TgBackupApplication
import com.dparadox.tgbackup.data.AppDatabase
import com.dparadox.tgbackup.data.SettingsManager
import com.dparadox.tgbackup.data.UploadedFile
import com.dparadox.tgbackup.network.TelegramApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val settings = SettingsManager(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.uploadedFileDao()
    private val telegramApi = TelegramApi(appContext.contentResolver)

    override suspend fun doWork(): Result {
        if (!settings.isConfigured()) return Result.success()
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGxDParadox:DownloadWakeLock")

        return try {
            wakeLock.acquire(6 * 60 * 60 * 1000L)
            
            // 1. Discovery phase
            setForeground(createForegroundInfo("Searching for cloud media...", 0, 0))
            fetchRecentMessages()

            // 2. Start download loop
            runDownload()
            
            // 3. Final Success Notification
            sendCompletionNotification()
            
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Fatal error", e)
            Result.retry()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private suspend fun fetchRecentMessages() = withContext(Dispatchers.IO) {
        try {
            var offset = 0
            for (batch in 0 until 5) { 
                val updates = telegramApi.getUpdates(settings.botToken, offset)
                if (updates.length() == 0) break
                
                for (i in 0 until updates.length()) {
                    val upd = updates.getJSONObject(i)
                    offset = upd.getInt("update_id") + 1
                    val msg = upd.optJSONObject("message") ?: continue
                    processMessage(msg)
                }
            }
        } catch (e: Exception) {
            Log.w("DownloadWorker", "Discovery failed: ${e.message}")
        }
    }

    private suspend fun processMessage(msg: JSONObject) {
        val msgId = msg.optLong("message_id")
        val fileId = when {
            msg.has("photo") -> {
                val pa = msg.getJSONArray("photo")
                pa.getJSONObject(pa.length() - 1).getString("file_id")
            }
            msg.has("video") -> msg.getJSONObject("video").getString("file_id")
            msg.has("document") -> msg.getJSONObject("document").getString("file_id")
            else -> null
        } ?: return

        if (dao.isHashUploaded(fileId) == 0) {
            dao.insert(UploadedFile(
                hash = "cloud_$fileId",
                filePath = "",
                fileName = "Restored_${msgId}",
                fileSize = 0,
                uploadDate = msg.optLong("date") * 1000L,
                telegramMessageId = msgId,
                telegramFileId = fileId,
                status = "success",
                mimeType = if (msg.has("video")) "video/mp4" else "image/jpeg",
                isDownloaded = false
            ))
        }
    }

    private suspend fun runDownload() = withContext(Dispatchers.IO) {
        // Only download files that have a telegramFileId and ARE NOT yet downloaded.
        // This allows pausing (cancelling) and resuming (re-running) the worker.
        val files = dao.getAllRecordsSync().filter { 
            it.telegramFileId.isNotEmpty() && !it.isDownloaded 
        }
        
        if (files.isEmpty()) return@withContext

        files.forEachIndexed { index, record ->
            if (isStopped) return@withContext

            setForeground(createForegroundInfo(
                "Restoring ${index + 1} of ${files.size}: ${record.fileName}",
                index + 1,
                files.size
            ))

            try {
                val bytes = telegramApi.downloadFile(settings.botToken, record.telegramFileId)
                saveToGallery(bytes, record.fileName, record.mimeType, record.folderName)
                dao.markAsDownloaded(record.hash)
            } catch (e: Exception) {
                Log.e("DownloadWorker", "Failed to download ${record.fileName}: ${e.message}")
            }

            delay(1000L)
        }
    }

    private fun saveToGallery(bytes: ByteArray, fileName: String, mimeType: String, folderName: String) {
        val timestamp = System.currentTimeMillis()
        val finalFileName = if (fileName.contains("Restored")) "${fileName}_$timestamp" else fileName
        
        val collection = if (mimeType.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val normalizedFolder = folderName.trim('/')
        val relativePath = if (normalizedFolder.isEmpty() || normalizedFolder == "Root") {
            "Pictures/TGxDParadox_Restored/"
        } else {
            "Pictures/TGxDParadox_Restored/$normalizedFolder/"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = appContext.contentResolver
        val uri = resolver.insert(collection, values)

        uri?.let {
            resolver.openOutputStream(it)?.use { stream ->
                stream.write(bytes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, values, null, null)
            }
        }
    }

    private fun sendCompletionNotification() {
        val notification = NotificationCompat.Builder(appContext, TgBackupApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Restoration Complete")
            .setContentText("All media has been successfully saved to your gallery.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2002, notification)
    }

    private fun createForegroundInfo(message: String, progress: Int, maxProgress: Int): ForegroundInfo {
        val intent = Intent(appContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(appContext, TgBackupApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Restoration Engine")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (maxProgress > 0) {
                    setProgress(maxProgress, progress, false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1002, notification)
        }
    }
}
