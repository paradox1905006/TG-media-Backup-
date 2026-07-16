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
    }

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

    /** True if both bot token and chat ID have been entered. */
    fun isConfigured(): Boolean = botToken.isNotBlank() && chatId.isNotBlank()
}
