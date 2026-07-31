package com.dparadox.tgbackup.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SelectedMediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(selectedMedia: SelectedMedia)

    @Delete
    suspend fun delete(selectedMedia: SelectedMedia)

    @Query("SELECT * FROM selected_media")
    fun getAllSelected(): Flow<List<SelectedMedia>>

    @Query("SELECT * FROM selected_media")
    suspend fun getAllSelectedSync(): List<SelectedMedia>

    @Query("SELECT EXISTS(SELECT 1 FROM selected_media WHERE hash = :hash)")
    suspend fun isSelected(hash: String): Boolean
}
