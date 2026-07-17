package com.dparadox.tgbackup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadedFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: UploadedFile)

    @Query("SELECT COUNT(*) FROM uploaded_files WHERE (hash = :hash OR telegramFileId = :hash) AND status = 'success'")
    suspend fun isHashUploaded(hash: String): Int

    @Query("SELECT hash FROM uploaded_files WHERE fileSize = :size AND dateModified = :modified AND status = 'success' LIMIT 1")
    suspend fun findHashBySizeAndDate(size: Long, modified: Long): String?

    @Query("SELECT * FROM uploaded_files ORDER BY uploadDate DESC")
    fun getAllRecords(): Flow<List<UploadedFile>>

    @Query("SELECT * FROM uploaded_files")
    suspend fun getAllRecordsSync(): List<UploadedFile>

    @Query("SELECT COUNT(*) FROM uploaded_files WHERE status = 'success'")
    suspend fun countSuccess(): Int

    @Query("SELECT COUNT(*) FROM uploaded_files WHERE status = 'failed'")
    suspend fun countFailed(): Int

    @Query("SELECT COUNT(*) FROM uploaded_files WHERE status = 'too_large'")
    suspend fun countTooLarge(): Int

    // ── Download & Global Stats ───────────────────────────────────────────

    @Query("SELECT * FROM uploaded_files WHERE telegramFileId != '' AND isDownloaded = 0")
    suspend fun getFilesToDownload(): List<UploadedFile>

    @Query("UPDATE uploaded_files SET isDownloaded = 1 WHERE hash = :hash")
    suspend fun markAsDownloaded(hash: String)

    @Query("SELECT SUM(fileSize) FROM uploaded_files WHERE status = 'success'")
    suspend fun getTotalSizeUploaded(): Long

    @Query("SELECT COUNT(*) FROM uploaded_files WHERE (mimeType LIKE 'image/%' OR telegramFileId LIKE 'cloud_photo%') AND status = 'success'")
    suspend fun countPhotos(): Int

    @Query("SELECT COUNT(*) FROM uploaded_files WHERE (mimeType LIKE 'video/%' OR telegramFileId LIKE 'cloud_video%') AND status = 'success'")
    suspend fun countVideos(): Int

    @Query("UPDATE uploaded_files SET isDownloaded = 0")
    suspend fun resetAllDownloadStatus()

    @Query("DELETE FROM uploaded_files")
    suspend fun deleteAll()
}
