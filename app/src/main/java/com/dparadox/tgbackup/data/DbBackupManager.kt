package com.dparadox.tgbackup.data

import android.util.Log
import com.dparadox.tgbackup.network.TelegramApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds and pushes the cloud DB backup JSON. Used by both [com.dparadox.tgbackup.worker.SyncWorker]
 * (backup-after-every-sync) and [com.dparadox.tgbackup.worker.DatabaseBackupWorker] (scheduled backup),
 * so the format and the security fix below only need to live in one place.
 *
 * SECURITY FIX: earlier versions of this backup uploaded the bot token, chat ID,
 * and raw encryption key in PLAINTEXT inside the JSON file — which then sat in
 * the Telegram group itself. Anyone with read access to that group could hijack
 * the bot and decrypt every encrypted file. None of those secrets are written
 * here anymore; restoring a backup on a new device still needs the user to
 * re-enter their bot token/chat ID/encryption key by hand (or via the
 * "Migrate to New Bot" flow, which never needs the OLD bot's token at all).
 */
object DbBackupManager {

    private const val TAG = "DbBackupManager"
    const val BACKUP_JSON_VERSION = 2

    /** Serializes the current upload history (including split-file chunk maps) to JSON. Contains no secrets. */
    fun buildBackupJson(records: List<UploadedFile>, partsByHash: Map<String, List<FilePart>>): String {
        val array = JSONArray()
        records.forEach { r ->
            val obj = JSONObject()
            obj.put("h", r.hash); obj.put("s", r.status); obj.put("fid", r.telegramFileId)
            obj.put("mid", r.telegramMessageId); obj.put("sz", r.fileSize); obj.put("mt", r.mimeType)
            obj.put("name", r.fileName); obj.put("fold", r.folderName); obj.put("split", r.isSplit)
            if (r.isSplit) {
                val partsArr = JSONArray()
                partsByHash[r.hash]?.forEach { p ->
                    val po = JSONObject()
                    po.put("pi", p.partIndex); po.put("tp", p.totalParts)
                    po.put("mid", p.telegramMessageId); po.put("fid", p.telegramFileId); po.put("ps", p.partSize)
                    partsArr.put(po)
                }
                obj.put("parts", partsArr)
            }
            array.put(obj)
        }
        val json = JSONObject()
        json.put("version", BACKUP_JSON_VERSION)
        json.put("history", array)
        return json.toString()
    }

    /**
     * Builds the current backup, uploads it (skipped if unchanged since the
     * last push), pins it, and — if [SettingsManager.dbBackupAutoDeleteOld]
     * is on — deletes the previous backup message (and, best-effort, the
     * "pinned a message" service notice that came with it) so the group only
     * ever holds the latest snapshot instead of accumulating one per interval.
     *
     * Returns true if a new backup was actually uploaded.
     */
    suspend fun pushBackup(
        telegramApi: TelegramApi,
        settings: SettingsManager,
        dao: UploadedFileDao,
        filePartDao: FilePartDao,
        captionPrefix: String = "🔄 Database Backup"
    ): Boolean {
        val records = dao.getAllRecordsSync()
        if (records.isEmpty()) return false

        val partsByHash = mutableMapOf<String, List<FilePart>>()
        records.filter { it.isSplit }.forEach { r -> partsByHash[r.hash] = filePartDao.getPartsForHash(r.hash) }

        val jsonString = buildBackupJson(records, partsByHash)
        val newHash = EncryptionUtils.sha256(jsonString.toByteArray())
        if (newHash == settings.lastDatabaseHash) {
            Log.d(TAG, "Database unchanged, skipping upload.")
            return false
        }

        val fileName = "TGxDParadox_DbBackup_${System.currentTimeMillis()}.json"
        val result = telegramApi.uploadByteArray(
            settings.botToken,
            settings.chatId,
            jsonString.toByteArray(),
            fileName,
            "$captionPrefix\n#DbBackup"
        )
        try { telegramApi.pinMessage(settings.botToken, settings.chatId, result.first) } catch (e: Exception) {}

        if (settings.dbBackupAutoDeleteOld) {
            val previousId = settings.lastDbBackupMessageId
            if (previousId > 0 && previousId != result.first) {
                try { telegramApi.deleteMessage(settings.botToken, settings.chatId, previousId) } catch (e: Exception) {}
                // Telegram auto-generates a separate "X pinned a message" service
                // message right after pinChatMessage() succeeds — its own message,
                // with its own message_id, that pinChatMessage's response never
                // returns to us. Left alone it becomes an orphaned line once the
                // backup document it refers to is deleted above. The Bot API has
                // no lookup for "give me the pin-notice id for message N", but
                // Telegram assigns chat message_ids sequentially, and the pin
                // notice for a message is (almost always) the very next id in the
                // chat — so we opportunistically delete previousId + 1 too. This
                // is a best-effort heuristic, not a guarantee: if another message
                // landed in the chat between the old pin and now, this deletes
                // the wrong (usually already-gone or unrelated) id, which is why
                // it's wrapped in its own try/catch and never allowed to fail
                // the backup push itself.
                try { telegramApi.deleteMessage(settings.botToken, settings.chatId, previousId + 1) } catch (e: Exception) {}
            }
        }

        settings.lastDatabaseHash = newHash
        settings.lastDbBackupMessageId = result.first
        return true
    }
}
