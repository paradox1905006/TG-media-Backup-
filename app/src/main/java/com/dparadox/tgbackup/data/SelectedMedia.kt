package com.dparadox.tgbackup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity — stores hashes of files that the user has manually selected for backup.
 * Used when full-device sync is disabled.
 */
@Entity(tableName = "selected_media")
data class SelectedMedia(
    @PrimaryKey
    val hash: String
)
