package com.dparadox.tgbackup.gallery.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dparadox.tgbackup.gallery.model.MediaItem
import com.dparadox.tgbackup.gallery.ui.MediaViewerViewModel
import com.dparadox.tgbackup.ui.components.DeleteConfirmationDialog
import com.dparadox.tgbackup.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    initialMediaId: Long,
    contextType: String,
    bucketId: String?,
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    val mediaIds by viewModel.mediaIds.collectAsStateWithLifecycle()
    val pendingDeleteIntent by viewModel.pendingDeleteIntent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Long?>(null) }
    var isDeletePermanent by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }

    val editLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshMedia()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeleteConfirmed()
        }
        viewModel.consumeDeleteIntent()
    }

    LaunchedEffect(pendingDeleteIntent) {
        pendingDeleteIntent?.let {
            deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    val initialIndex = remember(mediaIds) {
        val idx = mediaIds.indexOf(initialMediaId)
        if (idx != -1) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { mediaIds.size }
    )

    LaunchedEffect(contextType, bucketId) {
        viewModel.loadContext(contextType, bucketId)
    }

    // Update initial page when mediaIds loads for the first time
    var hasSetInitialPage by remember { mutableStateOf(false) }
    LaunchedEffect(mediaIds) {
        if (mediaIds.isNotEmpty() && !hasSetInitialPage) {
            val idx = mediaIds.indexOf(initialMediaId)
            if (idx != -1) {
                pagerState.scrollToPage(idx)
            }
            hasSetInitialPage = true
        }
    }

    if (showDeleteDialog && itemToDelete != null) {
        DeleteConfirmationDialog(
            count = 1,
            isPermanent = isDeletePermanent,
            onConfirm = {
                if (isDeletePermanent) {
                    viewModel.deletePermanently(itemToDelete!!)
                } else {
                    viewModel.moveToTrash(itemToDelete!!)
                }
                showDeleteDialog = false
                itemToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                itemToDelete = null
            }
        )
    }

    var isZoomed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (mediaIds.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 2,
                userScrollEnabled = !isZoomed
            ) { page ->
                if (page < mediaIds.size) {
                    val mediaId = mediaIds[page]
                    MediaItemView(
                        mediaStoreId = mediaId,
                        viewModel = viewModel,
                        onScaleChanged = { isZoomed = it > 1f },
                        onTap = { showBars = !showBars },
                        onDismiss = onBack
                    )
                }
            }
        }

        // Top Bar
        val currentMediaId = if (mediaIds.isNotEmpty() && pagerState.currentPage < mediaIds.size) {
            mediaIds[pagerState.currentPage]
        } else null

        val currentItem by produceState<MediaItem?>(initialValue = null, currentMediaId) {
            if (currentMediaId != null) {
                viewModel.observeMediaItem(currentMediaId).collect { value = it }
            } else {
                value = null
            }
        }

        if (showDetails && currentItem != null) {
            MediaDetailsDialog(item = currentItem!!, onDismiss = { showDetails = false })
        }

        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        currentItem?.let {
                            Text(it.displayName, color = Color.White, fontSize = 16.sp, maxLines = 1)
                            Text(
                                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(it.dateTaken)),
                                color = Color.White.copy(0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    currentItem?.let { item ->
                        if (!item.isTrashed) {
                            IconButton(onClick = { viewModel.toggleFavorite(item.mediaStoreId, !item.isFavorite) }) {
                                Icon(
                                    if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (item.isFavorite) Primary else Color.White
                                )
                            }
                            IconButton(onClick = {
                                itemToDelete = item.mediaStoreId
                                isDeletePermanent = true 
                                showDeleteDialog = true
                            }) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                            }
                        } else {
                            IconButton(onClick = {
                                viewModel.restoreFromTrash(item.mediaStoreId)
                                if (mediaIds.size <= 1) onBack()
                            }) {
                                Icon(Icons.Default.Restore, "Restore", tint = Color.White)
                            }
                            IconButton(onClick = {
                                itemToDelete = item.mediaStoreId
                                isDeletePermanent = true
                                showDeleteDialog = true
                            }) {
                                Icon(Icons.Default.DeleteForever, "Delete Permanently", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Bar
        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentItem != null && !currentItem!!.isTrashed) {
                        ViewerAction(Icons.Default.Edit, "Edit") {
                            val editIntent = Intent(Intent.ACTION_EDIT).apply {
                                setDataAndType(currentItem!!.uri, currentItem!!.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            editLauncher.launch(Intent.createChooser(editIntent, "Edit with"))
                        }
                        ViewerAction(Icons.Default.Share, "Share") {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = currentItem!!.mimeType
                                putExtra(Intent.EXTRA_STREAM, currentItem!!.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                        }
                        ViewerAction(Icons.Default.Info, "Details") {
                            showDetails = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaDetailsDialog(
    item: MediaItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Name", item.displayName)
                DetailRow("Path", item.filePath ?: "Unknown")
                DetailRow("Type", item.mimeType)
                DetailRow("Size", formatSize(item.sizeBytes))
                DetailRow("Date", SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(item.dateTaken)))
                if (item.width > 0 && item.height > 0) {
                    DetailRow("Resolution", "${item.width} x ${item.height}")
                }
                item.durationMs?.let {
                    DetailRow("Duration", formatDuration(it))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.2f MB".format(mb)
        else -> "%.2f KB".format(kb)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%d:%02d".format(mins, secs)
}

@Composable
private fun ViewerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp)
    }
}

@Composable
private fun MediaItemView(
    mediaStoreId: Long,
    viewModel: MediaViewerViewModel,
    onScaleChanged: (Float) -> Unit,
    onTap: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val item by viewModel.observeMediaItem(mediaStoreId).collectAsStateWithLifecycle(initialValue = null)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Swipe down to dismiss state
    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    val alpha = remember(swipeOffsetY) {
        (1f - abs(swipeOffsetY) / 1000f).coerceIn(0.5f, 1f)
    }

    LaunchedEffect(mediaStoreId) {
        scale = 1f
        offset = Offset.Zero
        swipeOffsetY = 0f
        onScaleChanged(1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        item?.let { mediaItem ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val maxWidthPx = constraints.maxWidth.toFloat()
                val maxHeightPx = constraints.maxHeight.toFloat()
                
                AsyncImage(
                    model = mediaItem.uri,
                    contentDescription = mediaItem.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, swipeOffsetY.roundToInt()) }
                        .alpha(alpha)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onTap() },
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 3f
                                    }
                                    onScaleChanged(scale)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                var zoomAccum = 1f
                                var pastTouchSlop = false
                                val touchSlop = viewConfiguration.touchSlop

                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (!canceled) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val pointerCount = event.changes.size
                                        val isPinch = pointerCount >= 2
                                        val shouldHandle = isPinch || scale > 1f

                                        if (!pastTouchSlop) {
                                            zoomAccum *= zoomChange
                                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                            val zoomMotion = abs(1 - zoomAccum) * centroidSize
                                            val panMotion = panChange.getDistance()
                                            if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop && shouldHandle) {
                                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                            scale = newScale
                                            onScaleChanged(newScale)
                                            if (scale > 1f) {
                                                val pan = panChange
                                                val boundX = (maxWidthPx * (scale - 1) / 2f)
                                                val boundY = (maxHeightPx * (scale - 1) / 2f)
                                                
                                                offset = Offset(
                                                    (offset.x + pan.x).coerceIn(-boundX, boundX),
                                                    (offset.y + pan.y).coerceIn(-boundY, boundY)
                                                )
                                            } else {
                                                offset = Offset.Zero
                                            }
                                            event.changes.forEach { change ->
                                                if (change.positionChanged()) change.consume()
                                            }
                                        } else if (!shouldHandle && pointerCount == 1) {
                                            // Handle swipe down to dismiss
                                            val dragAmount = panChange.y
                                            if (abs(dragAmount) > 0 && scale == 1f) {
                                                swipeOffsetY += dragAmount
                                                if (abs(swipeOffsetY) > 400) {
                                                    onDismiss()
                                                }
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                
                                // Reset swipe offset if not dismissed
                                if (abs(swipeOffsetY) <= 400) {
                                    swipeOffsetY = 0f
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
                
                if (mediaItem.mediaType == com.dparadox.tgbackup.gallery.model.MediaType.VIDEO && scale == 1f) {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(mediaItem.uri, mediaItem.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(mediaItem.uri, "video/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(fallbackIntent, "Play video with"))
                                } catch (e2: Exception) {
                                    Toast.makeText(context, "No video player found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .background(Color.Black.copy(0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
            }
        } ?: CircularProgressIndicator(color = Primary)
    }
}
