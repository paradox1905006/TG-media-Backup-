package com.dparadox.tgbackup.gallery.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import com.dparadox.tgbackup.gallery.db.dao.MediaItemDao
import com.dparadox.tgbackup.gallery.db.dao.AlbumDao
import com.dparadox.tgbackup.gallery.model.Album
import com.dparadox.tgbackup.gallery.model.MediaItem
import com.dparadox.tgbackup.gallery.model.TimelineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryRepository @Inject constructor(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val mediaItemDao: MediaItemDao,
    private val albumDao: AlbumDao
) {

    suspend fun syncMediaStore() = mediaStoreDataSource.syncMediaStore()

    suspend fun getMediaItem(mediaStoreId: Long): MediaItem? {
        return mediaItemDao.getByMediaStoreId(mediaStoreId)?.let { MediaItem.fromEntity(it) }
    }

    fun observeTimelineIds(): Flow<List<Long>> = mediaItemDao.observeTimeline().map { list -> list.map { it.mediaStoreId } }
    fun observeAlbumIds(bucketId: String): Flow<List<Long>> = mediaItemDao.observeByAlbum(bucketId).map { list -> list.map { it.mediaStoreId } }
    fun observeFavoriteIds(): Flow<List<Long>> = mediaItemDao.observeFavorites().map { list -> list.map { it.mediaStoreId } }
    fun observeTrashIds(): Flow<List<Long>> = mediaItemDao.observeTrash().map { list -> list.map { it.mediaStoreId } }

    fun pagingTimeline(): Flow<PagingData<TimelineItem>> = Pager(
        config = PagingConfig(
            pageSize = 60,
            prefetchDistance = 30,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { mediaItemDao.pagingSourceTimeline() }
    ).flow.map { pagingData ->
        pagingData.map { TimelineItem.Item(MediaItem.fromEntity(it)) }
            .insertSeparators { before: TimelineItem.Item?, after: TimelineItem.Item? ->
                if (after == null) return@insertSeparators null
                val afterDate = MediaItem.formatDay(after.media.dateTaken)
                if (before == null || MediaItem.formatDay(before.media.dateTaken) != afterDate) {
                    TimelineItem.Header(afterDate)
                } else {
                    null
                }
            }
    }

    fun pagingByAlbum(bucketId: String): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = 60,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { mediaItemDao.pagingSourceByAlbum(bucketId) }
    ).flow.mapPaged { MediaItem.fromEntity(it) }

    fun pagingFavorites(): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(pageSize = 60, enablePlaceholders = false),
        pagingSourceFactory = { mediaItemDao.pagingSourceFavorites() }
    ).flow.mapPaged { MediaItem.fromEntity(it) }

    fun pagingTrash(): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(pageSize = 60, enablePlaceholders = false),
        pagingSourceFactory = { mediaItemDao.pagingSourceTrash() }
    ).flow.mapPaged { MediaItem.fromEntity(it) }

    suspend fun setFavorite(ids: List<Long>, isFavorite: Boolean) {
        mediaItemDao.setFavorite(ids, isFavorite)
    }

    suspend fun moveToTrash(ids: List<Long>) {
        mediaItemDao.moveToTrash(ids, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(ids: List<Long>) {
        mediaItemDao.restoreFromTrash(ids)
    }

    suspend fun deletePermanently(ids: List<Long>) {
        mediaItemDao.deletePermanently(ids)
    }

    fun observeAlbums(): Flow<List<Album>> = albumDao.observeAll().map { entities ->
        entities.map {
            Album(
                bucketId = it.bucketId,
                bucketName = it.bucketName,
                coverUri = it.coverUri?.let { uri -> android.net.Uri.parse(uri) },
                itemCount = it.itemCount,
                dateModified = it.dateModified
            )
        }
    }

    suspend fun getAlbums(): List<Album> = albumDao.getAll().map {
        Album(
            bucketId = it.bucketId,
            bucketName = it.bucketName,
            coverUri = it.coverUri?.let { uri -> android.net.Uri.parse(uri) },
            itemCount = it.itemCount,
            dateModified = it.dateModified
        )
    }

    suspend fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MS
        mediaItemDao.deleteExpiredTrash(cutoff)
    }

    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
}

private fun <T : Any, R : Any> Flow<PagingData<T>>.mapPaged(transform: (T) -> R): Flow<PagingData<R>> =
    this.map { pagingData ->
        pagingData.map { transform(it) }
    }
