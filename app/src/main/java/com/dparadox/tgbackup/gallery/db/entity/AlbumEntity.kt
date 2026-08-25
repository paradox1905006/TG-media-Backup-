package com.dparadox.tgbackup.gallery.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gallery_albums",
    indices = [
        Index(value = ["bucketId"], unique = true)
    ]
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bucketId: String,
    val bucketName: String,
    val coverUri: String? = null,
    val itemCount: Int = 0,
    val dateModified: Long = 0
)