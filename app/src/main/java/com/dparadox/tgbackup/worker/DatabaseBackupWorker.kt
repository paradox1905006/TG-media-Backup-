package com.dparadox.tgbackup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dparadox.tgbackup.data.AppDatabase
import com.dparadox.tgbackup.data.SettingsManager
import com.dparadox.tgbackup.network.TelegramApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DatabaseBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val settings = SettingsManager(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.uploadedFileDao()
    private val telegramApi = TelegramApi(appContext.contentResolver)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!settings.isConfigured() || !settings.dbBackupEnabled) return@withContext Result.success()

        return@withContext try {
            val records = dao.getAllRecordsSync()
            if (records.isEmpty()) return@withContext Result.success()

            val json = JSONObject()
            val array = JSONArray()
            records.forEach {
                val obj = JSONObject()
                obj.put("h", it.hash); obj.put("s", it.status); obj.put("fid", it.telegramFileId)
                obj.put("mid", it.telegramMessageId); obj.put("sz", it.fileSize); obj.put("mt", it.mimeType)
                obj.put("name", it.fileName); obj.put("fold", it.folderName)
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
                "🔄 Scheduled Database Backup\n#AutoBackup"
            )
            try { telegramApi.pinMessage(settings.botToken, settings.chatId, msgId) } catch (e: Exception) {}
            
            Result.success()
        } catch (e: Exception) {
            Log.e("DBBackupWorker", "Backup failed", e)
            Result.retry()
        }
    }
}
