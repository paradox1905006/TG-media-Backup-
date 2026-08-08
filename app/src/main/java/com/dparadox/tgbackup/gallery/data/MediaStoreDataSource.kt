package com.dparadox.tgbackup.gallery.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.dparadox.tgbackup.gallery.db.dao.AlbumDao
import com.dparadox.tgbackup.gallery.db.dao.MediaItemDao
import com.dparadox.tgbackup.gallery.db.entity.AlbumEntity
import com.dparadox.tgbackup.gallery.db.entity.MediaItemEntity
import com.dparadox.tgbackup.gallery.model.Album
import com.dparadox.tgbackup.gallery.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries MediaStore for device images/videos and syncs metadata
 * into Room for the gallery feature.
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaItemDao: MediaItemDao,
    private val albumDao: AlbumDao
) {

    private val projectionImages = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.DATA
    )

    private val projectionVideos = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_TAKEN,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DATA
    )

    suspend fun syncMediaStore() = withContext(Dispatchers.IO) {
        val images = queryImages()
        val videos = queryVideos()
        val all = images + videos
        val existing = mediaItemDao.getByMediaStoreIds(all.map { it.mediaStoreId })

        // Preserve local gallery state (favorite/hidden/trash)
        val existingState = existing.associateBy { it.mediaStoreId }
        val merged = all.map { item ->
            val prior = existingState[item.mediaStoreId]
            if (prior != null) {
                item.copy(
                    id = prior.id,
                    isFavorite = prior.isFavorite,
                    isHidden = prior.isHidden,
                    isTrashed = prior.isTrashed,
                    trashedAt = prior.trashedAt
                )
            } else {
                item
            }
        }

        mediaItemDao.upsertAll(merged)
        syncAlbums(merged)
    }

    private fun queryImages(): List<MediaItemEntity> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val items = mutableListOf<MediaItemEntity>()
        context.contentResolver.query(
            collection,
            projectionImages,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                items += MediaItemEntity(
                    mediaStoreId = id,
                    uri = uri.toString(),
                    filePath = cursor.getString(dataCol)?.takeIf { it.isNotBlank() },
                    displayName = cursor.getString(nameCol) ?: "IMG_$id",
                    mimeType = cursor.getString(mimeCol) ?: "image/*",
                    sizeBytes = cursor.getLong(sizeCol),
                    dateTaken = cursor.getLong(takenCol).takeIf { it > 0 } ?: cursor.getLong(addedCol),
                    dateAdded = cursor.getLong(addedCol),
                    dateModified = cursor.getLong(modifiedCol),
                    bucketId = cursor.getString(bucketIdCol) ?: "default",
                    bucketName = cursor.getString(bucketNameCol) ?: "Gallery",
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol)
                )
            }
        }
        return items
    }

    private fun queryVideos(): List<MediaItemEntity> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val items = mutableListOf<MediaItemEntity>()
        context.contentResolver.query(
            collection,
            projectionVideos,
            null,
            null,
            "${MediaStore.Video.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                items += MediaItemEntity(
                    mediaStoreId = id,
                    uri = uri.toString(),
                    filePath = cursor.getString(dataCol)?.takeIf { it.isNotBlank() },
                    displayName = cursor.getString(nameCol) ?: "VID_$id",
                    mimeType = cursor.getString(mimeCol) ?: "video/*",
                    sizeBytes = cursor.getLong(sizeCol),
                    dateTaken = cursor.getLong(takenCol).takeIf { it > 0 } ?: cursor.getLong(addedCol),
                    dateAdded = cursor.getLong(addedCol),
                    dateModified = cursor.getLong(modifiedCol),
                    bucketId = cursor.getString(bucketIdCol) ?: "default",
                    bucketName = cursor.getString(bucketNameCol) ?: "Videos",
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    durationMs = cursor.getLong(durationCol).takeIf { it > 0 }
                )
            }
        }
        return items
    }

    private suspend fun syncAlbums(mediaItems: List<MediaItemEntity>) {
        val albums = mediaItems
            .groupBy { it.bucketId }
            .map { (bucketId, items) ->
                val newest = items.maxByOrNull { it.dateTaken } ?: items.first()
                AlbumEntity(
                    bucketId = bucketId,
                    bucketName = newest.bucketName,
                    coverUri = newest.uri,
                    itemCount = items.size,
                    dateModified = newest.dateModified
                )
            }
        albumDao.upsertAll(albums)
    }

    fun observeAlbums(): Flow<List<Album>> = albumDao.observeAll().let { flow ->
        flow {
            flow.collect { entities ->
                emit(entities.map { entity ->
                    Album(
                        id = entity.id,
                        bucketId = entity.bucketId,
                        bucketName = entity.bucketName,
                        coverUri = entity.coverUri?.let { Uri.parse(it) },
                        itemCount = entity.itemCount,
                        dateModified = entity.dateModified
                    )
                })
            }
        }.flowOn(Dispatchers.IO)
    }

    fun observeTimeline(): Flow<List<MediaItem>> = mediaItemDao.observeTimeline().let { flow ->
        flow {
            flow.collect { entities ->
                emit(entities.map { MediaItem.fromEntity(it) })
            }
        }.flowOn(Dispatchers.IO)
    }

    fun observeFavorites(): Flow<List<MediaItem>> = mediaItemDao.observeFavorites().let { flow ->
        flow {
            flow.collect { entities ->
                emit(entities.map { MediaItem.fromEntity(it) })
            }
        }.flowOn(Dispatchers.IO)
    }

    fun observeHidden(): Flow<List<MediaItem>> = mediaItemDao.observeHidden().let { flow ->
        flow {
            flow.collect { entities ->
                emit(entities.map { MediaItem.fromEntity(it) })
            }
        }.flowOn(Dispatchers.IO)
    }

    fun observeTrash(): Flow<List<MediaItem>> = mediaItemDao.observeTrash().let { flow ->
        flow {
            flow.collect { entities ->
                emit(entities.map { MediaItem.fromEntity(it) })
            }
        }.flowOn(Dispatchers.IO)
    }

    fun observeTrashCount(): Flow<Int> = mediaItemDao.observeTrashCount()
    fun observeHiddenCount(): Flow<Int> = mediaItemDao.observeHiddenCount()
    fun observeFavoriteCount(): Flow<Int> = mediaItemDao.observeFavoriteCount()
}