package com.dparadox.tgbackup.gallery.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.compose.itemContentType
import coil.compose.AsyncImage
import com.dparadox.tgbackup.gallery.model.MediaItem
import com.dparadox.tgbackup.gallery.model.TimelineItem
import com.dparadox.tgbackup.gallery.ui.GalleryGridViewModel
import com.dparadox.tgbackup.ui.components.DeleteConfirmationDialog
import com.dparadox.tgbackup.ui.theme.*

/**
 * Timeline screen — chronological grid of all device photos/videos,
 * grouped by date headers using Paging 3 separators.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    onOpenMedia: (Long) -> Unit = {},
    viewModel: GalleryGridViewModel = hiltViewModel()
) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val pagingItems = viewModel.pagingTimeline.collectAsLazyPagingItems()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TimelineHeader(
            isSyncing = isSyncing,
            isSelectionMode = isSelectionMode,
            selectedCount = selectedIds.size,
            selectedIds = selectedIds,
            viewModel = viewModel,
            onRefresh = { viewModel.refreshMedia() },
            onExitSelection = { viewModel.exitSelectionMode() }
        )

        if (pagingItems.itemCount == 0 && !isSyncing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos or videos yet", color = TextMuted, fontSize = 14.sp)
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
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { item ->
                    when (item) {
                        is TimelineItem.Item -> item.media.mediaStoreId
                        is TimelineItem.Header -> item.date
                    }
                },
                contentType = pagingItems.itemContentType { it is TimelineItem.Header },
                span = { index ->
                    val item = pagingItems[index]
                    if (item is TimelineItem.Header) GridItemSpan(maxLineSpan)
                    else GridItemSpan(1)
                }
            ) { index ->
                val item = pagingItems[index]
                when (item) {
                    is TimelineItem.Header -> DateHeader(item.date)
                    is TimelineItem.Item -> MediaGridCell(
                        media = item.media,
                        isSelected = item.media.mediaStoreId in selectedIds,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) viewModel.toggleSelect(item.media.mediaStoreId)
                            else onOpenMedia(item.media.mediaStoreId)
                        },
                        onLongClick = { viewModel.enterSelectionMode(item.media.mediaStoreId) }
                    )
                    null -> { /* Placeholder */ }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineHeader(
    isSyncing: Boolean,
    isSelectionMode: Boolean,
    selectedCount: Int,
    selectedIds: Set<Long>,
    viewModel: GalleryGridViewModel,
    onRefresh: () -> Unit,
    onExitSelection: () -> Unit
) {
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

    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isSelectionMode) "$selectedCount selected" else "Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (!isSelectionMode) {
                    Text("All your media", fontSize = 11.sp, color = TextMuted)
                }
            }
        },
        navigationIcon = {
            if (isSelectionMode) {
                IconButton(onClick = onExitSelection) {
                    Icon(Icons.Default.Close, "Exit", tint = TextPrimary)
                }
            }
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (isSelectionMode) {
                    IconButton(onClick = { viewModel.batchFavorite(selectedIds.toList()) }) {
                        Icon(Icons.Default.Star, "Favorite", tint = Primary)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = TextPrimary)
                    }
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Primary)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Background)
    )
}

@Composable
private fun DateHeader(day: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            day,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.3.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridCell(
    media: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(SurfaceAlt)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = media.uri,
            contentDescription = media.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video badge
        if (media.mediaType == com.dparadox.tgbackup.gallery.model.MediaType.VIDEO) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Favorite star
        if (media.isFavorite) {
            Icon(
                Icons.Default.Star,
                null,
                tint = Primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(14.dp)
            )
        }

        // Selection overlay
        if (isSelectionMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (isSelected) Primary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f))
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Primary else Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
