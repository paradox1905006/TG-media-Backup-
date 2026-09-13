package com.dparadox.tgbackup.gallery.duplicates

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.tgbackup.data.FileSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One duplicate cluster — several on-device files that hash identically. */
data class DuplicateGroup(
    val hash: String,
    val items: List<FileSyncEngine.MediaFile>
)

data class DuplicatesUiState(
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val groups: List<DuplicateGroup> = emptyList(),
    val selected: Set<Uri> = emptySet(),
    val scannedCount: Int = 0,
    val totalToScan: Int = 0
)

/**
 * Finds exact-content duplicates already sitting on the device (independent of
 * backup/upload status) by reusing FileSyncEngine's SHA-256 hashing, then lets
 * the user pick which copies to delete via the standard scoped-storage delete
 * flow (same approach as GalleryRepository.deletePermanently).
 */
class DuplicatesViewModel(application: Application) : AndroidViewModel(application) {

    private val fileSyncEngine = FileSyncEngine(application)

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState

    private val _pendingDeleteIntent = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteIntent: StateFlow<IntentSender?> = _pendingDeleteIntent

    private var urisAwaitingDeleteConfirmation: List<Uri> = emptyList()

    fun scan() {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, hasScanned = false)
            val allFiles = withContext(Dispatchers.IO) {
                // No dbLookup shortcut here on purpose — duplicate detection must use the
                // real content hash, not a (size, modified-date) guess, since a false
                // match here could lead to deleting a file that isn't actually a duplicate.
                fileSyncEngine.scanAllMedia(
                    onProgress = { scanned, total ->
                        _uiState.value = _uiState.value.copy(scannedCount = scanned, totalToScan = total)
                    }
                )
            }

            val groups = allFiles
                .groupBy { it.hash }
                .filter { (_, files) -> files.size > 1 }
                .map { (hash, files) -> DuplicateGroup(hash, files.sortedByDescending { it.dateModified }) }
                .sortedByDescending { group -> group.items.sumOf { it.sizeBytes } }

            // Pre-select every copy except the most recently modified one in each
            // group, so the default action keeps the newest file and frees space.
            val preselected = groups.flatMap { group -> group.items.drop(1).map { it.uri } }.toSet()

            _uiState.value = _uiState.value.copy(
                isScanning = false,
                hasScanned = true,
                groups = groups,
                selected = preselected
            )
        }
    }

    fun toggleSelected(uri: Uri) {
        val current = _uiState.value.selected
        _uiState.value = _uiState.value.copy(
            selected = if (current.contains(uri)) current - uri else current + uri
        )
    }

    val reclaimableBytes: Long
        get() = _uiState.value.groups
            .flatMap { it.items }
            .filter { it.uri in _uiState.value.selected }
            .sumOf { it.sizeBytes }

    fun deleteSelected() {
        val uris = _uiState.value.selected.toList()
        if (uris.isEmpty()) return
        urisAwaitingDeleteConfirmation = uris
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val intentSender: IntentSender? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        uris.forEach { context.contentResolver.delete(it, null, null) }
                        null
                    } catch (e: SecurityException) {
                        (e as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                    }
                } else {
                    uris.forEach { context.contentResolver.delete(it, null, null) }
                    null
                }
            } catch (e: Exception) {
                null
            }

            if (intentSender != null) {
                _pendingDeleteIntent.value = intentSender
            } else {
                onDeleteConfirmed()
            }
        }
    }

    /** Call after the system delete-confirmation flow (IntentSender) returns RESULT_OK. */
    fun onDeleteConfirmed() {
        val deleted = urisAwaitingDeleteConfirmation.toSet()
        urisAwaitingDeleteConfirmation = emptyList()
        val updatedGroups = _uiState.value.groups
            .map { group -> group.copy(items = group.items.filterNot { it.uri in deleted }) }
            .filter { it.items.size > 1 }
        _uiState.value = _uiState.value.copy(
            groups = updatedGroups,
            selected = _uiState.value.selected - deleted
        )
    }

    fun consumeDeleteIntent() {
        _pendingDeleteIntent.value = null
    }
}
