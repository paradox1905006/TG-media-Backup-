package com.dparadox.tgbackup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity — stores hashes of files that the user has manually excluded from backup.
 */
@Entity(tableName = "excluded_media")
data class ExcludedMedia(
    @PrimaryKey
    val hash: String
)
