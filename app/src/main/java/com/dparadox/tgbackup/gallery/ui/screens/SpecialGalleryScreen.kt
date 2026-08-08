package com.dparadox.tgbackup.gallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.paging.compose.itemKey
import com.dparadox.tgbackup.gallery.ui.GalleryGridViewModel
import com.dparadox.tgbackup.ui.components.DeleteConfirmationDialog
import com.dparadox.tgbackup.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialGalleryScreen(
    type: String, // "favorites" or "trash"
    onBack: () -> Unit,
    onOpenMedia: (Long, String) -> Unit,
    viewModel: GalleryGridViewModel = hiltViewModel()
) {
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            count = selectedIds.size,
            isPermanent = type == "trash",
            onConfirm = {
                if (type == "trash") {
                    viewModel.deletePermanently(selectedIds.toList())
                } else {
                    viewModel.moveToTrash(selectedIds.toList())
                }
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
    
    val pagingItems = when (type) {
        "favorites" -> viewModel.pagingFavorites.collectAsLazyPagingItems()
        else -> viewModel.pagingTrash.collectAsLazyPagingItems()
    }

    val title = when (type) {
        "favorites" -> "Favorites"
        "trash" -> "Recycle Bin"
        else -> "Gallery"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isSelectionMode) "${selectedIds.size} selected" else title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (type == "trash") {
                                IconButton(onClick = { viewModel.restoreFromTrash(selectedIds.toList()) }) {
                                    Icon(Icons.Default.Restore, "Restore", tint = Primary)
                                }
                                IconButton(onClick = { showDeleteDialog = true }) {
                                    Icon(Icons.Default.DeleteForever, "Delete Permanently", tint = TextPrimary)
                                }
                            } else {
                                IconButton(onClick = { viewModel.batchFavorite(selectedIds.toList()) }) {
                                    Icon(Icons.Default.StarBorder, "Unfavorite", tint = Primary)
                                }
                                IconButton(onClick = { showDeleteDialog = true }) {
                                    Icon(Icons.Default.Delete, "Delete", tint = TextPrimary)
                                }
                            }
                            TextButton(onClick = { viewModel.exitSelectionMode() }) {
                                Text("Cancel", color = Primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Background)
            )
        },
        containerColor = Background
    ) { padding ->
        if (pagingItems.itemCount == 0) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    if (type == "favorites") "No favorite items yet" else "Recycle bin is empty",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { it.mediaStoreId }
                ) { index ->
                    val media = pagingItems[index]
                    if (media != null) {
                        MediaGridCell(
                            media = media,
                            isSelected = media.mediaStoreId in selectedIds,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) viewModel.toggleSelect(media.mediaStoreId)
                                else onOpenMedia(media.mediaStoreId, type)
                            },
                            onLongClick = { viewModel.enterSelectionMode(media.mediaStoreId) }
                        )
                    }
                }
            }
        }
    }
}
