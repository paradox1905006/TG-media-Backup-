package com.dparadox.tgbackup.gallery.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dparadox.tgbackup.gallery.db.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity): Long

    @Update
    suspend fun update(item: MediaItemEntity)

    @Query("DELETE FROM gallery_media_items WHERE mediaStoreId = :mediaStoreId")
    suspend fun deleteByMediaStoreId(mediaStoreId: Long)

    @Query("DELETE FROM gallery_media_items WHERE isTrashed = 1 AND trashedAt < :cutoff")
    suspend fun deleteExpiredTrash(cutoff: Long)

    @Query("SELECT * FROM gallery_media_items WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun getByMediaStoreId(mediaStoreId: Long): MediaItemEntity?

    @Query("SELECT * FROM gallery_media_items WHERE mediaStoreId IN (:ids)")
    suspend fun getByMediaStoreIds(ids: List<Long>): List<MediaItemEntity>

    @Query("SELECT * FROM gallery_media_items WHERE isHidden = 0 AND isTrashed = 0 ORDER BY dateTaken DESC")
    fun pagingSourceTimeline(): PagingSource<Int, MediaItemEntity>

    @Query("SELECT * FROM gallery_media_items WHERE isHidden = 0 AND isTrashed = 0 AND bucketId = :bucketId ORDER BY dateTaken DESC")
    fun pagingSourceByAlbum(bucketId: String): PagingSource<Int, MediaItemEntity>

    @Query("SELECT * FROM gallery_media_items WHERE isFavorite = 1 AND isHidden = 0 AND isTrashed = 0 ORDER BY dateTaken DESC")
    fun pagingSourceFavorites(): PagingSource<Int, MediaItemEntity>

    @Query("SELECT * FROM gallery_media_items WHERE isHidden = 1 AND isTrashed = 0 ORDER BY dateTaken DESC")
    fun pagingSourceHidden(): PagingSource<Int, MediaItemEntity>

    @Query("SELECT * FROM gallery_media_items WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun pagingSourceTrash(): PagingSource<Int, MediaItemEntity>

    @Query("SELECT * FROM gallery_media_items WHERE isHidden = 0 AND isTrashed = 0 ORDER BY dateTaken DESC")
    fun observeTimeline(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM gallery_media_items WHERE isHidden = 0 AND isTrashed = 0 AND bucketId = :bucketId ORDER BY dateTaken DESC")
    fun observeByAlbum(bucketId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM gallery_media_items WHERE isFavorite = 1 AND isHidden = 0 AND isTrashed = 0 ORDER BY dateTaken DESC")
    fun observeFavorites(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM gallery_media_items WHERE isHidden = 1 AND isTrashed = 0 ORDER BY dateTaken DESC")
    fun observeHidden(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM gallery_media_items WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<MediaItemEntity>>

    @Query("UPDATE gallery_media_items SET isFavorite = :isFavorite WHERE mediaStoreId IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, isFavorite: Boolean)

    @Query("UPDATE gallery_media_items SET isHidden = :isHidden WHERE mediaStoreId IN (:ids)")
    suspend fun setHidden(ids: List<Long>, isHidden: Boolean)

    @Query("UPDATE gallery_media_items SET isTrashed = 1, trashedAt = :trashedAt WHERE mediaStoreId IN (:ids)")
    suspend fun moveToTrash(ids: List<Long>, trashedAt: Long)

    @Query("UPDATE gallery_media_items SET isTrashed = 0, trashedAt = NULL WHERE mediaStoreId IN (:ids)")
    suspend fun restoreFromTrash(ids: List<Long>)

    @Query("DELETE FROM gallery_media_items WHERE mediaStoreId IN (:ids)")
    suspend fun deletePermanently(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM gallery_media_items WHERE isTrashed = 1")
    fun observeTrashCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM gallery_media_items WHERE isHidden = 1 AND isTrashed = 0")
    fun observeHiddenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM gallery_media_items WHERE isFavorite = 1 AND isHidden = 0 AND isTrashed = 0")
    fun observeFavoriteCount(): Flow<Int>
}