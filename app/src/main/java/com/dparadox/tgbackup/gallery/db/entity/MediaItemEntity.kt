package com.dparadox.tgbackup.gallery.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gallery_media_items",
    indices = [
        Index(value = ["mediaStoreId"], unique = true),
        Index(value = ["bucketId"]),
        Index(value = ["isHidden"]),
        Index(value = ["isTrashed"]),
        Index(value = ["trashedAt"]),
        Index(value = ["dateTaken"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaStoreId: Long,
    val uri: String,
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
    val trashedAt: Long? = null,
    val deletedAt: Long? = null
)