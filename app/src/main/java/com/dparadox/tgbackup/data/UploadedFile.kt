package com.dparadox.tgbackup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity — one row per file we have attempted to upload or download.
 */
@Entity(tableName = "uploaded_files")
data class UploadedFile(
    @PrimaryKey
    val hash: String,                   // SHA-256 of the file content

    val filePath: String,               // Local path
    val fileName: String,               // e.g. "IMG_2024.jpg"
    val fileSize: Long,                 // Bytes
    val uploadDate: Long,               // Unix epoch ms
    val telegramMessageId: Long,        // Message ID in group
    val telegramFileId: String = "",    // File ID for downloading
    val status: String,                 // "success" | "failed" | "too_large"
    val mimeType: String = "",          // "image/jpeg", "video/mp4", etc.
    val folderName: String = "",        // Name of the folder (for organized restore)
    val dateModified: Long = 0,         // Last modified time
    val isDownloaded: Boolean = false   // For tracking download progress
)

/**
 * Aggregate counts/sums for the history stats row, computed in a single
 * SQL query instead of 6 separate DAO calls.
 */
data class UploadStatsSummary(
    val uploaded: Int = 0,
    val failed: Int = 0,
    val tooLarge: Int = 0,
    val photos: Int = 0,
    val videos: Int = 0,
    val totalSize: Long = 0
)
