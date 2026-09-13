package com.dparadox.tgbackup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FilePartDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(part: FilePart)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(parts: List<FilePart>)

    @Query("SELECT * FROM file_parts WHERE hash = :hash ORDER BY partIndex ASC")
    suspend fun getPartsForHash(hash: String): List<FilePart>

    @Query("SELECT COUNT(*) FROM file_parts WHERE hash = :hash")
    suspend fun countPartsForHash(hash: String): Int

    @Query("DELETE FROM file_parts WHERE hash = :hash")
    suspend fun deletePartsForHash(hash: String)
}
