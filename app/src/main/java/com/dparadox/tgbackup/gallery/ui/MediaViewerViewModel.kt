package com.dparadox.tgbackup.gallery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.tgbackup.gallery.data.GalleryRepository
import com.dparadox.tgbackup.gallery.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    private val repository: GalleryRepository
) : ViewModel() {

    private val _mediaIds = MutableStateFlow<List<Long>>(emptyList())
    val mediaIds: StateFlow<List<Long>> = _mediaIds.asStateFlow()

    private val _currentMedia = MutableStateFlow<MediaItem?>(null)
    val currentMedia: StateFlow<MediaItem?> = _currentMedia.asStateFlow()

    fun loadContext(type: String, bucketId: String?) {
        viewModelScope.launch {
            val idsFlow = when (type) {
                "album" -> repository.observeAlbumIds(bucketId ?: "")
                "favorites" -> repository.observeFavoriteIds()
                "trash" -> repository.observeTrashIds()
                else -> repository.observeTimelineIds()
            }
            idsFlow.collect { _mediaIds.value = it }
        }
    }

    fun loadMediaItem(mediaStoreId: Long) {
        viewModelScope.launch {
            _currentMedia.value = repository.getMediaItem(mediaStoreId)
        }
    }

    suspend fun getMediaItem(mediaStoreId: Long): MediaItem? {
        return repository.getMediaItem(mediaStoreId)
    }

    fun toggleFavorite(mediaStoreId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(listOf(mediaStoreId), isFavorite)
            if (_currentMedia.value?.mediaStoreId == mediaStoreId) {
                _currentMedia.value = _currentMedia.value?.copy(isFavorite = isFavorite)
            }
        }
    }

    fun moveToTrash(mediaStoreId: Long) {
        viewModelScope.launch {
            repository.moveToTrash(listOf(mediaStoreId))
        }
    }
    
    fun deletePermanently(mediaStoreId: Long) {
        viewModelScope.launch {
            repository.deletePermanently(listOf(mediaStoreId))
        }
    }

    fun restoreFromTrash(mediaStoreId: Long) {
        viewModelScope.launch {
            repository.restoreFromTrash(listOf(mediaStoreId))
        }
    }

    fun refreshMedia() {
        viewModelScope.launch {
            repository.syncMediaStore()
            repository.purgeExpiredTrash()
        }
    }
}
