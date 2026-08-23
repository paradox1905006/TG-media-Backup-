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

    // Single aggregate query replacing 6 separate round-trips — cuts DB load
    // dramatically when called on every history update (e.g. during active sync).
    @Query(
        """
        SELECT
          SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS uploaded,
          SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed,
          SUM(CASE WHEN status = 'too_large' THEN 1 ELSE 0 END) AS tooLarge,
          SUM(CASE WHEN status = 'success' AND (mimeType LIKE 'image/%' OR telegramFileId LIKE 'cloud_photo%') THEN 1 ELSE 0 END) AS photos,
          SUM(CASE WHEN status = 'success' AND (mimeType LIKE 'video/%' OR telegramFileId LIKE 'cloud_video%') THEN 1 ELSE 0 END) AS videos,
          SUM(CASE WHEN status = 'success' THEN fileSize ELSE 0 END) AS totalSize
        FROM uploaded_files
        """
    )
    suspend fun getStatsSummary(): UploadStatsSummary

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

    @Query("UPDATE uploaded_files SET status = 'pending' WHERE status = 'failed'")
    suspend fun retryFailedUploads()

    @Query("DELETE FROM uploaded_files")
    suspend fun deleteAll()
}
