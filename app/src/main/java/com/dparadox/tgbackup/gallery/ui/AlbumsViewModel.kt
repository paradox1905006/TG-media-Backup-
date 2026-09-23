package com.dparadox.tgbackup.gallery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.tgbackup.gallery.data.GalleryRepository
import com.dparadox.tgbackup.gallery.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    galleryRepository: GalleryRepository
) : ViewModel() {

    val albums: StateFlow<List<Album>> = galleryRepository.observeAlbums()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}