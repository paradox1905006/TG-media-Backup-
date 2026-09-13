package com.dparadox.tgbackup.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SettingsManager — stores all user preferences.
 *
 * Sensitive values (bot token, chat ID) go into EncryptedSharedPreferences,
 * which is backed by the Android Keystore. The encryption key is generated
 * once and stored in the Keystore — it never leaves the device.
 *
 * Non-sensitive settings (upload mode, Wi-Fi only, folders) also go in
 * the same encrypted prefs for simplicity.
 */
class SettingsManager(context: Context) {

    // Create or retrieve the master key from Android Keystore
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // EncryptedSharedPreferences uses AES256-SIV for keys and AES256-GCM for values
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "tg_backup_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_BOT_TOKEN          = "bot_token"
        private const val KEY_CHAT_ID            = "chat_id"
        private const val KEY_UPLOAD_AS_DOCUMENT = "upload_as_document"
        private const val KEY_WIFI_ONLY          = "wifi_only"
        private const val KEY_SYNC_INTERVAL_HOURS= "sync_interval_hours"
        private const val KEY_AUTO_SYNC_ENABLED  = "auto_sync_enabled"
        private const val KEY_FULL_DEVICE_SYNC   = "full_device_sync"
        private const val KEY_TERMS_ACCEPTED     = "terms_accepted"
        private const val KEY_DB_BACKUP_ENABLED  = "db_backup_enabled"
        private const val KEY_DB_BACKUP_INTERVAL = "db_backup_interval"
        private const val KEY_TOPIC_MAP          = "topic_map"
        private const val KEY_WATCHED_FOLDERS    = "watched_folders"   // JSON array of URI strings
        private const val KEY_WATCHED_MUSIC_FOLDERS = "watched_music_folders"
        private const val KEY_SYNC_PAUSED         = "sync_paused"
        private const val KEY_RESTORE_PAUSED      = "restore_paused"
        private const val KEY_ENCRYPTION_ENABLED  = "encryption_enabled"
        private const val KEY_ENCRYPTION_KEY      = "encryption_key_v1"
        private const val KEY_LAST_DB_HASH        = "last_db_hash"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START   = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END     = "quiet_hours_end"
        private const val KEY_DARK_MODE_ENABLED   = "dark_mode_enabled"
        private const val KEY_INCLUDE_MUSIC       = "include_music_backup"
        private const val KEY_AUTO_BACKUP_MUSIC   = "auto_backup_music_enabled"
        private const val KEY_DB_BACKUP_AUTO_DELETE_OLD = "db_backup_auto_delete_old"
        private const val KEY_LAST_DB_BACKUP_MSG_ID      = "last_db_backup_message_id"
        private const val KEY_SPLIT_UPLOAD_ENABLED       = "split_upload_enabled"
    }

    // ── Appearance ────────────────────────────────────────────────────────

    /** True = Premium Pitch-Black theme (default/original). False = Light mode. */
    var darkModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, value).apply() }

    // ── Music backup ──────────────────────────────────────────────────────

    /** When true, audio files (mp3/m4a/wav/flac/ogg/...) are scanned & backed up like photos/videos. */
    var includeMusicBackup: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_MUSIC, false)
        set(value) { prefs.edit().putBoolean(KEY_INCLUDE_MUSIC, value).apply() }

    // ── Large-file split upload ───────────────────────────────────────────

    /**
     * When true (default): files bigger than [FileSyncEngine.MAX_UPLOAD_BYTES] (50MB)
     * are automatically chunked into ≤[FileSyncEngine.SPLIT_CHUNK_BYTES] pieces and
     * uploaded as a sequence (see SyncWorker.uploadSplitWithRetry).
     *
     * When false: large files are NOT chunked. They're skipped and recorded with
     * status = "too_large" in history, same as pre-split-upload behavior — the user
     * doesn't want big files eating into upload time/data at all.
     */
    var splitUploadEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPLIT_UPLOAD_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_SPLIT_UPLOAD_ENABLED, value).apply() }

    /**
     * When true, every scanned audio track is backed up automatically (mirrors
     * "Full Device Backup" for folders, but scoped to music). When false, only
     * individually-selected tracks (see [KEY_AUTO_BACKUP_MUSIC] sibling
     * SelectedMedia rows, keyed by file hash) are uploaded.
     */
    var autoBackupMusicEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP_MUSIC, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_BACKUP_MUSIC, value).apply() }

    // ── Quiet-hours backup window ────────────────────────────────────────
    // When enabled, scheduled (periodic) sync only runs between start–end
    // hour (24h, device local time). A manual "Start Sync" tap always runs
    // immediately regardless of this window.

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, value).apply() }

    /** Hour (0-23) the allowed backup window opens. Default 1 AM. */
    var quietHoursStart: Int
        get() = prefs.getInt(KEY_QUIET_HOURS_START, 1)
        set(value) { prefs.edit().putInt(KEY_QUIET_HOURS_START, value.coerceIn(0, 23)).apply() }

    /** Hour (0-23) the allowed backup window closes. Default 6 AM. */
    var quietHoursEnd: Int
        get() = prefs.getInt(KEY_QUIET_HOURS_END, 6)
        set(value) { prefs.edit().putInt(KEY_QUIET_HOURS_END, value.coerceIn(0, 23)).apply() }

    /** True if the current device hour falls inside the configured window (handles overnight wraparound, e.g. 23→5). */
    fun isWithinQuietHours(currentHour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): Boolean {
        val start = quietHoursStart
        val end = quietHoursEnd
        return if (start == end) {
            true // 0-width window means "always allowed"
        } else if (start < end) {
            currentHour in start until end
        } else {
            currentHour >= start || currentHour < end // wraps past midnight
        }
    }

    // ── Encryption Settings ──────────────────────────────────────────────

    var encryptionEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENCRYPTION_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_ENCRYPTION_ENABLED, value).apply() }

    fun getEncryptionKey(): String {
        var key = prefs.getString(KEY_ENCRYPTION_KEY, null)
        if (key == null) {
            key = java.util.UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_ENCRYPTION_KEY, key).apply()
        }
        return key
    }

    // Used when restoring a backup so decrypt uses the SAME key that was used to
    // encrypt on the original device, instead of silently keeping a freshly
    // auto-generated (and therefore mismatched) local key.
    fun setEncryptionKey(key: String) {
        if (key.isBlank()) return
        prefs.edit().putString(KEY_ENCRYPTION_KEY, key).apply()
    }

    var lastDatabaseHash: String
        get() = prefs.getString(KEY_LAST_DB_HASH, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_DB_HASH, value).apply() }

    // ── Auto-sync toggle ──────────────────────────────────────────────────

    var autoSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, value).apply() }

    var fullDeviceSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_FULL_DEVICE_SYNC, true)
        set(value) { prefs.edit().putBoolean(KEY_FULL_DEVICE_SYNC, value).apply() }

    var termsAccepted: Boolean
        get() = prefs.getBoolean(KEY_TERMS_ACCEPTED, false)
        set(value) { prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, value).apply() }

    var dbBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_DB_BACKUP_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_DB_BACKUP_ENABLED, value).apply() }
    
    var dbBackupIntervalHours: Int
        get() = prefs.getInt(KEY_DB_BACKUP_INTERVAL, 12)
        set(value) { prefs.edit().putInt(KEY_DB_BACKUP_INTERVAL, value).apply() }

    /** When true, pushing a fresh DB backup deletes the previous backup message from the group. */
    var dbBackupAutoDeleteOld: Boolean
        get() = prefs.getBoolean(KEY_DB_BACKUP_AUTO_DELETE_OLD, false)
        set(value) { prefs.edit().putBoolean(KEY_DB_BACKUP_AUTO_DELETE_OLD, value).apply() }

    /** message_id of the most recently pushed DB backup — used to delete it once a newer one lands. */
    var lastDbBackupMessageId: Long
        get() = prefs.getLong(KEY_LAST_DB_BACKUP_MSG_ID, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_DB_BACKUP_MSG_ID, value).apply() }

    var syncPaused: Boolean
        get() = prefs.getBoolean(KEY_SYNC_PAUSED, false)
        set(value) { prefs.edit().putBoolean(KEY_SYNC_PAUSED, value).apply() }

    var restorePaused: Boolean
        get() = prefs.getBoolean(KEY_RESTORE_PAUSED, false)
        set(value) { prefs.edit().putBoolean(KEY_RESTORE_PAUSED, value).apply() }

    fun getTopicId(folderName: String): Int? {
        val map = getTopicMap()
        return if (map.containsKey(folderName)) map[folderName] else null
    }

    fun saveTopicId(folderName: String, topicId: Int) {
        val map = getTopicMap().toMutableMap()
        map[folderName] = topicId
        saveTopicMap(map)
    }

    private fun getTopicMap(): Map<String, Int> {
        val raw = prefs.getString(KEY_TOPIC_MAP, "") ?: ""
        if (raw.isEmpty()) return emptyList<Pair<String, Int>>().toMap()
        return try {
            val json = org.json.JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            json.keys().forEach { map[it] = json.getInt(it) }
            map
        } catch (e: Exception) { emptyMap() }
    }

    private fun saveTopicMap(map: Map<String, Int>) {
        val json = org.json.JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_TOPIC_MAP, json.toString()).apply()
    }

    // ── Bot token ──────────────────────────────────────────────────────────

    var botToken: String
        get() = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        set(value) { prefs.edit().putString(KEY_BOT_TOKEN, value).apply() }

    // ── Chat ID ───────────────────────────────────────────────────────────

    var chatId: String
        get() = prefs.getString(KEY_CHAT_ID, "") ?: ""
        set(value) { prefs.edit().putString(KEY_CHAT_ID, value).apply() }

    // ── Upload mode ───────────────────────────────────────────────────────

    /** If true: use sendDocument (original quality). If false: sendPhoto/sendVideo (compressed). */
    var uploadAsDocument: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_AS_DOCUMENT, false)
        set(value) { prefs.edit().putBoolean(KEY_UPLOAD_AS_DOCUMENT, value).apply() }

    // ── Wi-Fi only ────────────────────────────────────────────────────────

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)   // default: Wi-Fi only
        set(value) { prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply() }

    // ── Sync interval ─────────────────────────────────────────────────────

    /** How many hours between auto-syncs. Default: 6. */
    var syncIntervalHours: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL_HOURS, 6)
        set(value) { prefs.edit().putInt(KEY_SYNC_INTERVAL_HOURS, value).apply() }

    // ── Watched folders ───────────────────────────────────────────────────

    /**
     * Persist a list of content-URI strings (from SAF folder picker).
     * Stored as a pipe-separated string, e.g. "content://...│content://..."
     */
    var watchedFolderUris: List<String>
        get() {
            val raw = prefs.getString(KEY_WATCHED_FOLDERS, "") ?: ""
            return if (raw.isEmpty()) emptyList() else raw.split("|")
        }
        set(value) {
            prefs.edit().putString(KEY_WATCHED_FOLDERS, value.joinToString("|")).apply()
        }

    /** Music-only counterpart to [watchedFolderUris] — folders whose audio backs up when [autoBackupMusicEnabled] is off. */
    var watchedMusicFolders: List<String>
        get() {
            val raw = prefs.getString(KEY_WATCHED_MUSIC_FOLDERS, "") ?: ""
            return if (raw.isEmpty()) emptyList() else raw.split("|")
        }
        set(value) {
            prefs.edit().putString(KEY_WATCHED_MUSIC_FOLDERS, value.joinToString("|")).apply()
        }

    /** True if both bot token and chat ID have been entered. */
    fun isConfigured(): Boolean = botToken.isNotBlank() && chatId.isNotBlank()
}
