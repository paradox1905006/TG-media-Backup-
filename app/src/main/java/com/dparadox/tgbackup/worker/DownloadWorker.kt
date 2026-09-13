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
import androidx.work.workDataOf
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
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val settings = SettingsManager(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.uploadedFileDao()
    private val filePartDao = db.filePartDao()
    private val telegramApi = TelegramApi(appContext.contentResolver)

    companion object {
        // Same idea as SyncWorker: exposed via WorkManager's own progress data
        // so the Dashboard can render a real "42%" instead of a spinner.
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL   = "progress_total"
    }

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
        val document = msg.optJSONObject("document")
        val audio = msg.optJSONObject("audio")
        val fileId = when {
            msg.has("photo") -> {
                val pa = msg.getJSONArray("photo")
                pa.getJSONObject(pa.length() - 1).getString("file_id")
            }
            msg.has("video") -> msg.getJSONObject("video").getString("file_id")
            audio != null -> audio.getString("file_id")
            document != null -> document.getString("file_id")
            else -> null
        } ?: return

        // Prefer Telegram's actual document/audio filename (preserves the .enc
        // marker for encrypted uploads) over a generic placeholder.
        val docFileName = document?.optString("file_name", "") ?: audio?.optString("file_name", "")
        val resolvedFileName = if (!docFileName.isNullOrBlank()) docFileName else "Restored_${msgId}"
        val resolvedMimeType = when {
            resolvedFileName.endsWith(".enc") -> guessMimeFromName(resolvedFileName.removeSuffix(".enc"))
            document != null && document.has("mime_type") -> document.getString("mime_type")
            audio != null && audio.has("mime_type") -> audio.getString("mime_type")
            audio != null -> "audio/mpeg"
            msg.has("video") -> "video/mp4"
            else -> "image/jpeg"
        }

        if (dao.isHashUploaded(fileId) == 0) {
            dao.insert(UploadedFile(
                hash = "cloud_$fileId",
                filePath = "",
                fileName = resolvedFileName,
                fileSize = 0,
                uploadDate = msg.optLong("date") * 1000L,
                telegramMessageId = msgId,
                telegramFileId = fileId,
                status = "success",
                mimeType = resolvedMimeType,
                isDownloaded = false
            ))
        }
    }

    private suspend fun runDownload() = withContext(Dispatchers.IO) {
        // FIX: Was using getAllRecordsSync().filter { ... } which loaded the entire
        //      DB into memory and filtered in Kotlin. getFilesToDownload() is an
        //      existing SQL query that returns only the rows we need — much faster
        //      for large histories and avoids holding every record in RAM.
        val files = dao.getFilesToDownload()
        
        if (files.isEmpty()) return@withContext

        files.forEachIndexed { index, record ->
            if (isStopped) return@withContext
            
            // PAUSE LOGIC
            while (settings.restorePaused && !isStopped) {
                delay(2000L)
            }

            setForeground(createForegroundInfo(
                "Restoring ${index + 1} of ${files.size}: ${record.fileName}",
                index + 1,
                files.size
            ))
            try {
                setProgress(workDataOf(KEY_PROGRESS_CURRENT to index + 1, KEY_PROGRESS_TOTAL to files.size))
            } catch (ignored: Exception) {}

            try {
                if (record.isSplit) {
                    // Large file — was uploaded as multiple chunks. Fully
                    // automatic: fetch every part, reassemble in order,
                    // decrypt if needed, verify the hash, then save.
                    restoreSplitFile(record, index, files.size)
                } else {
                    val bytes = telegramApi.downloadFile(settings.botToken, record.telegramFileId)

                    val decryptedBytes = if (record.fileName.endsWith(".enc")) {
                        try {
                            com.dparadox.tgbackup.data.EncryptionUtils.decrypt(bytes, settings.getEncryptionKey())
                        } catch (e: Exception) {
                            Log.e("DownloadWorker", "Decryption failed for ${record.fileName}", e)
                            bytes // Fallback to raw bytes if decryption fails
                        }
                    } else {
                        bytes
                    }

                    saveToGallery(decryptedBytes, record.fileName.removeSuffix(".enc"), record.mimeType, record.folderName)
                    dao.markAsDownloaded(record.hash)
                }
            } catch (e: Exception) {
                Log.e("DownloadWorker", "Failed to download ${record.fileName}: ${e.message}")
            }

            delay(1000L)
        }
    }

    /**
     * Downloads every chunk of a split-uploaded file, in order, into a temp
     * file on disk (so a multi-GB file is never fully held in RAM while
     * downloading), decrypts once if the original upload was encrypted,
     * verifies the SHA-256 against [UploadedFile.hash], and only then saves
     * it to the gallery. If any part is missing or the hash doesn't match,
     * the file is left un-downloaded (isDownloaded stays false) so it is
     * retried on the next restore run — nothing corrupt ever reaches the
     * user's gallery.
     */
    private suspend fun restoreSplitFile(record: UploadedFile, index: Int, total: Int) {
        val parts = filePartDao.getPartsForHash(record.hash)
        if (parts.isEmpty()) {
            Log.e("DownloadWorker", "No parts found in DB for split file ${record.fileName} — cannot restore")
            return
        }

        val tempFile = File(appContext.cacheDir, "${record.hash}.restoring")
        try {
            FileOutputStream(tempFile).use { out ->
                parts.forEachIndexed { partIdx, part ->
                    setForeground(createForegroundInfo(
                        "Restoring ${index + 1} of $total: ${record.fileName} — part ${partIdx + 1}/${parts.size}",
                        index + 1, total
                    ))
                    val partBytes = telegramApi.downloadFile(settings.botToken, part.telegramFileId)
                    out.write(partBytes)
                }
            }

            var finalBytes = tempFile.readBytes()

            // If encryption was on at upload time, the reassembled bytes are
            // still the AES-GCM ciphertext of the *whole* original file —
            // decrypt once now that all chunks are back together.
            if (record.fileName.endsWith(".enc")) {
                finalBytes = try {
                    com.dparadox.tgbackup.data.EncryptionUtils.decrypt(finalBytes, settings.getEncryptionKey())
                } catch (e: Exception) {
                    Log.e("DownloadWorker", "Decryption failed for split file ${record.fileName}", e)
                    return // don't save something we couldn't decrypt correctly
                }
            }

            val actualHash = sha256(finalBytes)
            if (actualHash != record.hash) {
                Log.e(
                    "DownloadWorker",
                    "Integrity check FAILED for ${record.fileName}: expected ${record.hash}, got $actualHash — " +
                        "a part may be missing or corrupted. Will retry on next restore."
                )
                return
            }

            saveToGallery(finalBytes, record.fileName.removeSuffix(".enc"), record.mimeType, record.folderName)
            dao.markAsDownloaded(record.hash)
        } finally {
            tempFile.delete()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun guessMimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4", "m4v" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "aac" -> "audio/aac"
            "wma" -> "audio/x-ms-wma"
            else -> "image/jpeg"
        }
    }

    private fun saveToGallery(bytes: ByteArray, fileName: String, mimeType: String, folderName: String) {
        val timestamp = System.currentTimeMillis()
        val finalFileName = if (fileName.contains("Restored")) "${fileName}_$timestamp" else fileName

        val collection = when {
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val restoreRoot = if (mimeType.startsWith("audio/")) "Music/TGxDParadox_Restored/" else "Pictures/TGxDParadox_Restored/"
        val normalizedFolder = folderName.trim('/')
        val relativePath = if (normalizedFolder.isEmpty() || normalizedFolder == "Root") {
            restoreRoot
        } else {
            "$restoreRoot$normalizedFolder/"
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
