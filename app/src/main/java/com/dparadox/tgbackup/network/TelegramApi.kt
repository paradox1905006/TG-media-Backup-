package com.dparadox.tgbackup.network

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit

class TelegramApi(private val contentResolver: ContentResolver) {

    companion object {
        private const val BASE = "https://api.telegram.org"
        const val MAX_BYTES = 50L * 1024L * 1024L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getMe(botToken: String): String {
        val request = Request.Builder().url("$BASE/bot$botToken/getMe").get().build()
        return execute(request).getJSONObject("result").getString("username")
    }

    fun formatChatId(chatId: String): String {
        val trimmed = chatId.trim()
        if (trimmed.startsWith("-")) return trimmed
        // If it's a long number, it's likely a group/supergroup ID missing the prefix
        return if (trimmed.length >= 9) "-100$trimmed" else trimmed
    }

    fun sendTestMessage(botToken: String, chatId: String) {
        val formattedId = formatChatId(chatId)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", formattedId)
            .addFormDataPart("text", "✅ TG x Media Backup connected to this chat!")
            .build()
        execute(Request.Builder().url("$BASE/bot$botToken/sendMessage").post(body).build())
    }

    fun createForumTopic(botToken: String, chatId: String, name: String): Int {
        val formattedId = formatChatId(chatId)
        // Telegram limits topic names to 128 characters
        val safeName = if (name.length > 128) name.substring(0, 125) + "..." else name
        
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", formattedId)
            .addFormDataPart("name", safeName)
            .build()
        
        return try {
            val result = execute(Request.Builder().url("$BASE/bot$botToken/createForumTopic").post(body).build()).getJSONObject("result")
            result.getInt("message_thread_id")
        } catch (e: Exception) {
            // Rethrow with more context if it's a common "Topics not enabled" error
            if (e.message?.contains("not enough rights") == true) {
                throw Exception("Bot needs 'Manage Topics' permission.")
            } else if (e.message?.contains("TOPICS_NOT_ENABLED") == true || e.message?.contains("not a forum") == true) {
                throw Exception("Enable 'Topics' in your Telegram Group settings.")
            }
            throw e
        }
    }

    fun uploadMedia(botToken: String, chatId: String, fileUri: Uri, fileName: String, mimeType: String, asDocument: Boolean, threadId: Int? = null): Pair<Long, String> {
        val formattedId = formatChatId(chatId)
        val (method, fieldName) = when {
            asDocument -> "sendDocument" to "document"
            mimeType.startsWith("video/") -> "sendVideo" to "video"
            mimeType.startsWith("audio/") -> "sendAudio" to "audio"
            else -> "sendPhoto" to "photo"
        }
        val fileBody = object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()
            override fun writeTo(sink: BufferedSink) { openStream(fileUri).use { sink.writeAll(it.source()) } }
        }
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", formattedId)
            .addFormDataPart(fieldName, fileName, fileBody)
            
        threadId?.let { multipart.addFormDataPart("message_thread_id", it.toString()) }
            
        val result = execute(Request.Builder().url("$BASE/bot$botToken/$method").post(multipart.build()).build()).getJSONObject("result")
        return Pair(result.getLong("message_id"), getFileIdFromResult(result))
    }

    fun uploadByteArray(botToken: String, chatId: String, bytes: ByteArray, fileName: String, caption: String, threadId: Int? = null): Pair<Long, String> {
        val formattedId = formatChatId(chatId)
        // FIX: Replaced deprecated RequestBody.create() with the OkHttp extension function
        //      .toRequestBody(). The static create() was deprecated in OkHttp 4.x.
        val fileBody = bytes.toRequestBody("application/octet-stream".toMediaType())
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", formattedId)
            .addFormDataPart("caption", caption)
            .addFormDataPart("document", fileName, fileBody)
            
        threadId?.let { multipart.addFormDataPart("message_thread_id", it.toString()) }
            
        val result = execute(Request.Builder().url("$BASE/bot$botToken/sendDocument").post(multipart.build()).build()).getJSONObject("result")
        return Pair(result.getLong("message_id"), getFileIdFromResult(result))
    }

    fun pinMessage(botToken: String, chatId: String, messageId: Long) {
        val formattedId = formatChatId(chatId)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", formattedId).addFormDataPart("message_id", messageId.toString()).build()
        execute(Request.Builder().url("$BASE/bot$botToken/pinChatMessage").post(body).build())
    }

    fun getChat(botToken: String, chatId: String): JSONObject {
        val formattedId = formatChatId(chatId)
        return execute(Request.Builder().url("$BASE/bot$botToken/getChat?chat_id=$formattedId").get().build()).getJSONObject("result")
    }

    /**
     * Re-posts an existing message (by ID) into the same or another chat,
     * using [botToken] as the acting bot. This is the key trick behind
     * bot-migration: a message's `file_id` is only valid for the bot that
     * originally sent/received it, but forwarding the message with a NEW
     * bot produces a fresh copy whose `document`/`photo`/`video` file_id
     * IS valid for that new bot — without ever needing the old (possibly
     * revoked) bot's token. Works as long as the acting bot is currently a
     * member of the chat and the original message still exists there.
     */
    fun forwardMessage(botToken: String, toChatId: String, fromChatId: String, messageId: Long, threadId: Int? = null): JSONObject {
        val toId = formatChatId(toChatId)
        val fromId = formatChatId(fromChatId)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", toId)
            .addFormDataPart("from_chat_id", fromId)
            .addFormDataPart("message_id", messageId.toString())
            .apply { threadId?.let { addFormDataPart("message_thread_id", it.toString()) } }
            .build()
        return execute(Request.Builder().url("$BASE/bot$botToken/forwardMessage").post(body).build()).getJSONObject("result")
    }

    /** Returns the file_id (photo/video/audio/document) embedded in a message JSON, or null if none. */
    fun extractFileId(message: JSONObject): String? = try {
        getFileIdFromResult(message).ifBlank { null }
    } catch (e: Exception) { null }

    fun deleteMessage(botToken: String, chatId: String, messageId: Long): Boolean {
        val formattedId = formatChatId(chatId)
        return try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", formattedId)
                .addFormDataPart("message_id", messageId.toString())
                .build()
            execute(Request.Builder().url("$BASE/bot$botToken/deleteMessage").post(body).build())
            true
        } catch (e: Exception) {
            false // message may already be gone, or bot lacks delete rights — non-fatal
        }
    }

    fun downloadFile(botToken: String, fileId: String): ByteArray {
        val path = execute(Request.Builder().url("$BASE/bot$botToken/getFile?file_id=$fileId").get().build()).getJSONObject("result").getString("file_path")
        val response = client.newCall(Request.Builder().url("$BASE/file/bot$botToken/$path").get().build()).execute()
        return response.body?.bytes() ?: throw Exception("Empty body")
    }

    fun getUpdates(botToken: String, offset: Int = 0): JSONArray {
        return execute(Request.Builder().url("$BASE/bot$botToken/getUpdates?offset=$offset&limit=100").get().build()).getJSONArray("result")
    }

    private fun getFileIdFromResult(result: JSONObject): String {
        return when {
            result.has("photo") -> { val photos = result.getJSONArray("photo"); photos.getJSONObject(photos.length() - 1).getString("file_id") }
            result.has("video") -> result.getJSONObject("video").getString("file_id")
            result.has("audio") -> result.getJSONObject("audio").getString("file_id")
            result.has("document") -> result.getJSONObject("document").getString("file_id")
            else -> ""
        }
    }

    private fun execute(request: Request): JSONObject {
        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        if (response.code == 429) throw RateLimitException("Rate limited", json.optJSONObject("parameters")?.optInt("retry_after", 5) ?: 5)
        if (!json.optBoolean("ok", false)) throw TelegramException(json.optString("description", "Error"))
        return json
    }

    private fun openStream(uri: Uri): InputStream = contentResolver.openInputStream(uri) ?: throw Exception("No stream")
}

class TelegramException(message: String) : Exception(message)
class RateLimitException(message: String, val retryAfterSeconds: Int) : Exception(message)
