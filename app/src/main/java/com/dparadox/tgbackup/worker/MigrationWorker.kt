package com.dparadox.tgbackup.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dparadox.tgbackup.MainActivity
import com.dparadox.tgbackup.TgBackupApplication
import com.dparadox.tgbackup.data.AppDatabase
import com.dparadox.tgbackup.data.DbBackupManager
import com.dparadox.tgbackup.data.FilePart
import com.dparadox.tgbackup.data.SettingsManager
import com.dparadox.tgbackup.data.UploadedFile
import com.dparadox.tgbackup.network.RateLimitException
import com.dparadox.tgbackup.network.TelegramApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Recovers full backup history onto a NEW bot after the old bot was revoked
 * (or lost), without ever needing the old bot's token.
 *
 * The core trick: a Telegram file_id is only valid for the bot that
 * originally sent/received it — but the *message itself* survives in the
 * group forever, independent of which bot posted it. As long as [newBotToken]
 * is currently a member/admin of the same [chatId], forwardMessage() re-posts
 * any old message and hands back a brand-new file_id that IS valid for the
 * new bot. So the flow is:
 *
 *  1. Find the DB backup JSON — either auto-discovered as the group's pinned
 *     message (getChat -> pinned_message -> forwardMessage -> download), or
 *     supplied manually by the user (when the old backup isn't pinned/found).
 *  2. For every file (and every chunk of every split file) in that backup's
 *     history: forwardMessage() it with the new bot to mint a fresh, new-bot
 *     -readable file_id, then delete the old (now-redundant) message so the
 *     group doesn't end up with two copies of everything.
 *  3. Save the rewritten history into the local Room DB, switch the app's
 *     active bot token/chat ID over to the new bot, and push a brand-new DB
 *     backup — so the new bot is immediately fully operational.
 */
class MigrationWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MigrationWorker"
        private const val NOTIFICATION_ID = 1003
        private const val STEP_DELAY_MS = 900L
        private const val MAX_RETRIES = 4

        const val KEY_NEW_BOT_TOKEN      = "new_bot_token"
        const val KEY_CHAT_ID            = "chat_id"
        const val KEY_MANUAL_BACKUP_PATH = "manual_backup_path" // optional: local file path if auto-discovery isn't used

        const val KEY_RESULT_MIGRATED = "migrated_count"
        const val KEY_RESULT_SKIPPED  = "skipped_count"
        const val KEY_RESULT_ERROR    = "error_message"
    }

    private val settings = SettingsManager(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.uploadedFileDao()
    private val filePartDao = db.filePartDao()
    private val telegramApi = TelegramApi(appContext.contentResolver)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val newBotToken = inputData.getString(KEY_NEW_BOT_TOKEN)?.trim().orEmpty()
        val rawChatId   = inputData.getString(KEY_CHAT_ID)?.trim().orEmpty()
        val manualPath  = inputData.getString(KEY_MANUAL_BACKUP_PATH)

        if (newBotToken.isBlank() || rawChatId.isBlank()) {
            return@withContext Result.failure(workDataOf(KEY_RESULT_ERROR to "New bot token and chat ID are required."))
        }
        val chatId = telegramApi.formatChatId(rawChatId)

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGxDParadox:MigrationWakeLock")

        return@withContext try {
            wakeLock.acquire(6 * 60 * 60 * 1000L)
            setForeground(createForegroundInfo("Locating old backup...", 0, 0))

            // 1. Verify the new bot can actually see this chat before doing anything else.
            try {
                telegramApi.getMe(newBotToken)
            } catch (e: Exception) {
                return@withContext Result.failure(workDataOf(KEY_RESULT_ERROR to "Couldn't verify the new bot token: ${e.message}"))
            }

            // 2. Get the backup JSON — manual file takes priority if the user supplied one.
            val (jsonString, oldBackupMessageId) = if (!manualPath.isNullOrBlank()) {
                val file = File(manualPath)
                if (!file.exists()) {
                    return@withContext Result.failure(workDataOf(KEY_RESULT_ERROR to "Imported backup file not found."))
                }
                file.readText() to null
            } else {
                discoverOldBackup(newBotToken, chatId)
                    ?: return@withContext Result.failure(workDataOf(
                        KEY_RESULT_ERROR to "No pinned DB backup found in this chat. Add the new bot as admin first, " +
                            "or import the backup .json file manually below."
                    ))
            }

            val history = try {
                JSONObject(jsonString).getJSONArray("history")
            } catch (e: Exception) {
                return@withContext Result.failure(workDataOf(KEY_RESULT_ERROR to "Backup file is corrupted or not valid JSON."))
            }

            // 3. Re-link every file (and every split-file chunk) to the new bot.
            var migrated = 0
            var skipped = 0
            val total = history.length()

            for (i in 0 until total) {
                if (isStopped) break
                val entry = history.getJSONObject(i)
                setForeground(createForegroundInfo("Migrating file ${i + 1}/$total", i + 1, total))
                try {
                    setProgress(workDataOf("progress_current" to i + 1, "progress_total" to total))
                } catch (ignored: Exception) {}

                val hash   = entry.optString("h")
                val status = entry.optString("s")
                val oldMid = entry.optLong("mid", 0L)
                val isSplit = entry.optBoolean("split", false)
                val folderName = entry.optString("fold")
                val threadId = getOrCreateTopic(newBotToken, chatId, folderName)

                if (status != "success" || oldMid <= 0L || hash.isBlank()) {
                    // Nothing was actually on Telegram for this record (e.g. a
                    // locally-failed upload) — carry it over as "failed" so a
                    // normal sync retries it from the device, if still present.
                    dao.insert(UploadedFile(
                        hash = hash.ifBlank { "unknown_$i" },
                        filePath = "",
                        fileName = entry.optString("name", "unknown"),
                        fileSize = entry.optLong("sz", 0L),
                        uploadDate = System.currentTimeMillis(),
                        telegramMessageId = 0,
                        status = "failed",
                        mimeType = entry.optString("mt", "application/octet-stream"),
                        folderName = entry.optString("fold", ""),
                        dateModified = 0
                    ))
                    skipped++
                    continue
                }

                if (isSplit) {
                    val oldParts = entry.optJSONArray("parts")
                    if (oldParts == null || oldParts.length() == 0) {
                        skipped++
                        continue
                    }
                    val newParts = mutableListOf<FilePart>()
                    var allPartsOk = true
                    for (pi in 0 until oldParts.length()) {
                        if (isStopped) { allPartsOk = false; break }
                        val part = oldParts.getJSONObject(pi)
                        val relinked = migrateOneMessage(newBotToken, chatId, part.optLong("mid", 0L), threadId)
                        if (relinked == null) { allPartsOk = false; break }
                        newParts += FilePart(
                            hash = hash,
                            partIndex = part.optInt("pi", pi),
                            totalParts = part.optInt("tp", oldParts.length()),
                            telegramMessageId = relinked.first,
                            telegramFileId = relinked.second,
                            partSize = part.optLong("ps", 0L)
                        )
                        delay(STEP_DELAY_MS)
                    }
                    if (!allPartsOk) {
                        Log.w(TAG, "Split file '$hash' partially failed to migrate — marking failed for retry.")
                        skipped++
                        continue
                    }
                    filePartDao.deletePartsForHash(hash)
                    filePartDao.insertAll(newParts)
                    dao.insert(UploadedFile(
                        hash = hash,
                        filePath = "",
                        fileName = entry.optString("name"),
                        fileSize = entry.optLong("sz", 0L),
                        uploadDate = System.currentTimeMillis(),
                        telegramMessageId = newParts.first().telegramMessageId,
                        telegramFileId = "",
                        status = "success",
                        mimeType = entry.optString("mt"),
                        folderName = entry.optString("fold"),
                        dateModified = 0,
                        isSplit = true
                    ))
                    migrated++
                } else {
                    val relinked = migrateOneMessage(newBotToken, chatId, oldMid, threadId)
                    if (relinked == null) {
                        Log.w(TAG, "Message $oldMid (hash=$hash) could not be forwarded — marking failed for retry.")
                        dao.insert(UploadedFile(
                            hash = hash, filePath = "", fileName = entry.optString("name"),
                            fileSize = entry.optLong("sz", 0L), uploadDate = System.currentTimeMillis(),
                            telegramMessageId = 0, status = "failed", mimeType = entry.optString("mt"),
                            folderName = entry.optString("fold"), dateModified = 0
                        ))
                        skipped++
                        continue
                    }
                    dao.insert(UploadedFile(
                        hash = hash,
                        filePath = "",
                        fileName = entry.optString("name"),
                        fileSize = entry.optLong("sz", 0L),
                        uploadDate = System.currentTimeMillis(),
                        telegramMessageId = relinked.first,
                        telegramFileId = relinked.second,
                        status = "success",
                        mimeType = entry.optString("mt"),
                        folderName = entry.optString("fold"),
                        dateModified = 0
                    ))
                    migrated++
                }
                delay(STEP_DELAY_MS)
            }

            // 4. Clean up the old backup message itself (we already have its content).
            if (oldBackupMessageId != null) {
                telegramApi.deleteMessage(newBotToken, chatId, oldBackupMessageId)
            }

            // 5. Switch the app over to the new bot and push a fresh backup.
            settings.botToken = newBotToken
            settings.chatId = chatId
            settings.lastDatabaseHash = ""      // force a fresh push regardless of old hash
            settings.lastDbBackupMessageId = 0L // old one is already gone (or was never tracked, for manual import)

            setForeground(createForegroundInfo("Pushing fresh backup under new bot...", total, total))
            try {
                DbBackupManager.pushBackup(
                    telegramApi, settings, dao, filePartDao,
                    captionPrefix = "\u2705 Post-Migration Database Backup"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Post-migration backup push failed (migration itself still succeeded)", e)
            }

            Result.success(workDataOf(KEY_RESULT_MIGRATED to migrated, KEY_RESULT_SKIPPED to skipped))
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            Result.failure(workDataOf(KEY_RESULT_ERROR to (e.message ?: "Unknown error")))
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    /**
     * Finds the group's pinned message with the new bot, and — if it looks
     * like our DB backup document — forwards it (to mint a new-bot-readable
     * file_id) and downloads it. Returns (jsonContent, oldMessageId) or null
     * if nothing usable was pinned.
     */
    private fun discoverOldBackup(newBotToken: String, chatId: String): Pair<String, Long>? {
        return try {
            val chat = telegramApi.getChat(newBotToken, chatId)
            val pinned = chat.optJSONObject("pinned_message") ?: return null
            val document = pinned.optJSONObject("document") ?: return null
            val oldMessageId = pinned.optLong("message_id", 0L)
            if (oldMessageId <= 0L) return null

            val forwarded = telegramApi.forwardMessage(newBotToken, chatId, chatId, oldMessageId)
            val fileId = telegramApi.extractFileId(forwarded) ?: return null
            val bytes = telegramApi.downloadFile(newBotToken, fileId)

            // The forwarded copy is a temporary read-artifact — clean it up immediately,
            // we already have the bytes. The ORIGINAL (oldMessageId) is deleted later,
            // once migration fully succeeds (kept around meanwhile as a safety net).
            val forwardedMsgId = forwarded.optLong("message_id", 0L)
            if (forwardedMsgId > 0L) telegramApi.deleteMessage(newBotToken, chatId, forwardedMsgId)

            String(bytes) to oldMessageId
        } catch (e: Exception) {
            Log.w(TAG, "Auto-discovery of old backup failed: ${e.message}")
            null
        }
    }

    /**
     * Forwards [oldMessageId] with the new bot to obtain a fresh (new-bot
     * -valid) message_id + file_id, then deletes the old message so the
     * group ends up with exactly one copy instead of two. Returns null if
     * the message is gone/inaccessible (caller marks that file "failed").
     */
    private suspend fun migrateOneMessage(newBotToken: String, chatId: String, oldMessageId: Long, threadId: Int? = null): Pair<Long, String>? {
        if (oldMessageId <= 0L) return null
        for (attempt in 0 until MAX_RETRIES) {
            if (isStopped) return null
            try {
                val forwarded = telegramApi.forwardMessage(newBotToken, chatId, chatId, oldMessageId, threadId)
                val fileId = telegramApi.extractFileId(forwarded) ?: return null
                val newMessageId = forwarded.optLong("message_id", 0L)
                // Old message was posted by the (now-revoked) bot and is fully
                // superseded by the forwarded copy — remove it so nothing doubles up.
                telegramApi.deleteMessage(newBotToken, chatId, oldMessageId)
                return newMessageId to fileId
            } catch (e: RateLimitException) {
                delay(e.retryAfterSeconds * 1000L + 500L)
            } catch (e: Exception) {
                delay((Math.pow(2.0, attempt.toDouble()) * 1500L).toLong())
            }
        }
        return null
    }

    /**
     * Resolves the SAME forum topic the file originally lived in — topic IDs
     * belong to the chat/forum itself, not to whichever bot posted into them,
     * so the local folder->topic cache built by the old bot is still valid
     * for the new one. Falls back to creating the topic (matches SyncWorker's
     * naming) only if the cache is empty (e.g. fresh install), and finally to
     * null (General) if creation isn't possible.
     */
    private fun getOrCreateTopic(botToken: String, chatId: String, folderName: String): Int? {
        val safeFolderName = if (folderName.isBlank() || folderName == "Root") "General Media" else folderName

        settings.getTopicId(safeFolderName)?.let { return it }

        return try {
            val id = telegramApi.createForumTopic(botToken, chatId, safeFolderName)
            settings.saveTopicId(safeFolderName, id)
            id
        } catch (e: Exception) {
            Log.w(TAG, "Topic lookup/creation failed for '$safeFolderName' during migration: ${e.message}")
            null
        }
    }

    private fun createForegroundInfo(message: String, progress: Int, maxProgress: Int): ForegroundInfo {
        val intent = Intent(appContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(appContext, TgBackupApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Migrating to New Bot")
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
