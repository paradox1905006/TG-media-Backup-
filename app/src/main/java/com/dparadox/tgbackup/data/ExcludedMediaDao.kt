package com.dparadox.tgbackup.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedMediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(excludedMedia: ExcludedMedia)

    @Delete
    suspend fun delete(excludedMedia: ExcludedMedia)

    @Query("SELECT * FROM excluded_media")
    fun getAllExcluded(): Flow<List<ExcludedMedia>>

    @Query("SELECT * FROM excluded_media")
    suspend fun getAllExcludedSync(): List<ExcludedMedia>

    @Query("SELECT EXISTS(SELECT 1 FROM excluded_media WHERE hash = :hash)")
    suspend fun isExcluded(hash: String): Boolean
}
