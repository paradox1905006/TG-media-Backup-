package com.dparadox.tgbackup.gallery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.dparadox.tgbackup.gallery.data.GalleryRepository
import com.dparadox.tgbackup.gallery.model.MediaItem
import com.dparadox.tgbackup.gallery.model.TimelineItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared ViewModel for gallery grids that need paging + multi-select.
 * Timeline and album-detail screens both use this.
 */
@HiltViewModel
class GalleryGridViewModel @Inject constructor(
    private val galleryRepository: GalleryRepository
) : ViewModel() {

    val pagingTimeline: Flow<PagingData<TimelineItem>> = galleryRepository.pagingTimeline()
        .cachedIn(viewModelScope)

    val pagingFavorites: Flow<PagingData<MediaItem>> = galleryRepository.pagingFavorites()
        .cachedIn(viewModelScope)

    val pagingTrash: Flow<PagingData<MediaItem>> = galleryRepository.pagingTrash()
        .cachedIn(viewModelScope)

    // Lazy-init album paging per bucket (created on first access)
    private val albumPages = mutableMapOf<String, Flow<PagingData<MediaItem>>>()
    fun pagingByAlbum(bucketId: String): Flow<PagingData<MediaItem>> =
        albumPages.getOrPut(bucketId) {
            galleryRepository.pagingByAlbum(bucketId).cachedIn(viewModelScope)
        }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    init {
        refreshMedia()
    }

    fun refreshMedia() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                galleryRepository.syncMediaStore()
                galleryRepository.purgeExpiredTrash()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun toggleFavorite(mediaStoreId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            galleryRepository.setFavorite(listOf(mediaStoreId), isFavorite)
        }
    }

    fun enterSelectionMode(mediaStoreId: Long) {
        _isSelectionMode.value = true
        _selectedIds.value = setOf(mediaStoreId)
    }

    fun toggleSelect(mediaStoreId: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (mediaStoreId in current) current - mediaStoreId else current + mediaStoreId
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun moveToTrash(mediaStoreIds: List<Long>) {
        viewModelScope.launch {
            galleryRepository.moveToTrash(mediaStoreIds)
            exitSelectionMode()
        }
    }

    fun restoreFromTrash(mediaStoreIds: List<Long>) {
        viewModelScope.launch {
            galleryRepository.restoreFromTrash(mediaStoreIds)
            exitSelectionMode()
        }
    }

    fun deletePermanently(mediaStoreIds: List<Long>) {
        viewModelScope.launch {
            galleryRepository.deletePermanently(mediaStoreIds)
            exitSelectionMode()
        }
    }

    fun batchFavorite(ids: List<Long>) {
        viewModelScope.launch {
            galleryRepository.setFavorite(ids, true)
            exitSelectionMode()
        }
    }
}
