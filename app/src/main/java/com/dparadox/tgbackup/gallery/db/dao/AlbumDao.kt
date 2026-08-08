package com.dparadox.tgbackup.gallery.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dparadox.tgbackup.gallery.db.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(album: AlbumEntity): Long

    @Query("SELECT * FROM gallery_albums ORDER BY dateModified DESC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM gallery_albums ORDER BY dateModified DESC")
    suspend fun getAll(): List<AlbumEntity>

    @Query("SELECT * FROM gallery_albums WHERE bucketId = :bucketId LIMIT 1")
    suspend fun getByBucketId(bucketId: String): AlbumEntity?

    @Query("DELETE FROM gallery_albums")
    suspend fun clearAll()
}