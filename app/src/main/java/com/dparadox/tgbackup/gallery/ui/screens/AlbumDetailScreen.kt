package com.dparadox.tgbackup.gallery.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.dparadox.tgbackup.gallery.ui.GalleryGridViewModel
import com.dparadox.tgbackup.ui.components.DeleteConfirmationDialog
import com.dparadox.tgbackup.ui.theme.*

/**
 * Album detail — shows a single folder's media in a grid with
 * multi-select support (favorite / trash / delete).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    bucketId: String,
    bucketName: String,
    onBack: () -> Unit,
    onOpenMedia: (Long) -> Unit = {},
    viewModel: GalleryGridViewModel = hiltViewModel()
) {
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val pagingItems = viewModel.pagingByAlbum(bucketId).collectAsLazyPagingItems()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            count = selectedIds.size,
            onConfirm = {
                viewModel.moveToTrash(selectedIds.toList())
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Header
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isSelectionMode) "${selectedIds.size} selected"
                        else bucketName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    if (!isSelectionMode) {
                        Text("${pagingItems.itemCount} items", fontSize = 12.sp, color = TextMuted)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
            },
            actions = {
                if (isSelectionMode) {
                    IconButton(onClick = { viewModel.batchFavorite(selectedIds.toList()) }) {
                        Icon(Icons.Default.Star, "Favorite", tint = Primary)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = TextPrimary)
                    }
                    TextButton(onClick = { viewModel.exitSelectionMode() }) {
                        Text("Cancel", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Background)
        )

        if (pagingItems.itemCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No media in this album", color = TextMuted, fontSize = 14.sp)
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(pagingItems.itemSnapshotList, key = { it?.mediaStoreId ?: -System.currentTimeMillis() }) { media ->
                if (media != null) {
                    MediaGridCell(
                        media = media,
                        isSelected = media.mediaStoreId in selectedIds,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) viewModel.toggleSelect(media.mediaStoreId)
                            else onOpenMedia(media.mediaStoreId)
                        },
                        onLongClick = { viewModel.enterSelectionMode(media.mediaStoreId) }
                    )
                }
            }
        }
    }
}
