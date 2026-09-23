package com.dparadox.tgbackup.gallery.model

import android.net.Uri
import com.dparadox.tgbackup.gallery.db.entity.MediaItemEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MediaType {
    IMAGE,
    VIDEO
}

data class MediaItem(
    val id: Long = 0,
    val mediaStoreId: Long,
    val uri: Uri,
    val filePath: String?,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateTaken: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val bucketId: String,
    val bucketName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null
) {
    val mediaType: MediaType
        get() = if (mimeType.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE

    fun toEntity(): MediaItemEntity = MediaItemEntity(
        mediaStoreId = mediaStoreId,
        uri = uri.toString(),
        filePath = filePath,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateTaken = dateTaken,
        dateAdded = dateAdded,
        dateModified = dateModified,
        bucketId = bucketId,
        bucketName = bucketName,
        width = width,
        height = height,
        durationMs = durationMs,
        isFavorite = isFavorite,
        isHidden = isHidden,
        isTrashed = isTrashed,
        trashedAt = trashedAt
    )

    companion object {
        fun fromEntity(entity: MediaItemEntity): MediaItem = MediaItem(
            id = entity.id,
            mediaStoreId = entity.mediaStoreId,
            uri = Uri.parse(entity.uri),
            filePath = entity.filePath,
            displayName = entity.displayName,
            mimeType = entity.mimeType,
            sizeBytes = entity.sizeBytes,
            dateTaken = entity.dateTaken,
            dateAdded = entity.dateAdded,
            dateModified = entity.dateModified,
            bucketId = entity.bucketId,
            bucketName = entity.bucketName,
            width = entity.width,
            height = entity.height,
            durationMs = entity.durationMs,
            isFavorite = entity.isFavorite,
            isHidden = entity.isHidden,
            isTrashed = entity.isTrashed,
            trashedAt = entity.trashedAt
        )

        fun formatDay(timestamp: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val now = Calendar.getInstance()
            return when {
                cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) ->
                    "Today"

                cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 ->
                    "Yesterday"

                else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}

data class Album(
    val id: Long = 0,
    val bucketId: String,
    val bucketName: String,
    val coverUri: Uri? = null,
    val itemCount: Int = 0,
    val dateModified: Long = 0
)

sealed class TimelineItem {
    data class Item(val media: MediaItem) : TimelineItem()
    data class Header(val date: String) : TimelineItem()
}
