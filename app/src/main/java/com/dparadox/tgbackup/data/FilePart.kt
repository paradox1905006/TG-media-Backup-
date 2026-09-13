package com.dparadox.tgbackup.data

import androidx.room.Entity

/**
 * One row per uploaded chunk of a large (split) file.
 *
 * When a file is bigger than [FileSyncEngine.MAX_UPLOAD_BYTES] (the Bot API's
 * 50 MB hard limit), it is broken into sequential chunks of
 * [FileSyncEngine.SPLIT_CHUNK_BYTES] bytes each, and every chunk is uploaded
 * as its own Telegram document. The parent [UploadedFile] row (same [hash])
 * is marked `isSplit = true` and has no single [UploadedFile.telegramFileId] of
 * its own — instead its bytes live across the rows in this table.
 *
 * Restoring is fully automatic: DownloadWorker looks up all parts for a
 * hash, sorted by [partIndex], downloads them in order, concatenates them
 * into one local file, and verifies the SHA-256 of the result against
 * [UploadedFile.hash] before saving it to the gallery.
 */
@Entity(tableName = "file_parts", primaryKeys = ["hash", "partIndex"])
data class FilePart(
    val hash: String,             // Parent UploadedFile.hash this chunk belongs to
    val partIndex: Int,           // 0-based position of this chunk within the file
    val totalParts: Int,          // Total number of chunks for the parent file
    val telegramMessageId: Long,  // Message ID this chunk was posted as
    val telegramFileId: String,   // File ID used to download this chunk back
    val partSize: Long            // Byte size of this specific chunk (last chunk is usually smaller)
)
