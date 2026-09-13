package com.dparadox.tgbackup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dparadox.tgbackup.data.AppDatabase
import com.dparadox.tgbackup.data.DbBackupManager
import com.dparadox.tgbackup.data.SettingsManager
import com.dparadox.tgbackup.network.TelegramApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val settings = SettingsManager(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.uploadedFileDao()
    private val filePartDao = db.filePartDao()
    private val telegramApi = TelegramApi(appContext.contentResolver)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!settings.isConfigured() || !settings.dbBackupEnabled) return@withContext Result.success()

        return@withContext try {
            // Delegates to the shared, security-fixed builder — no more
            // plaintext bot token / chat ID / encryption key in the JSON
            // that gets uploaded to the group itself.
            DbBackupManager.pushBackup(
                telegramApi, settings, dao, filePartDao,
                captionPrefix = "🔄 Scheduled Database Backup"
            )
            Result.success()
        } catch (e: Exception) {
            Log.e("DBBackupWorker", "Backup failed", e)
            Result.retry()
        }
    }
}
