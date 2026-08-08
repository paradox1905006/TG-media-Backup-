package com.dparadox.tgbackup.gallery.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dparadox.tgbackup.gallery.model.MediaItem
import com.dparadox.tgbackup.gallery.ui.MediaViewerViewModel
import com.dparadox.tgbackup.ui.components.DeleteConfirmationDialog
import com.dparadox.tgbackup.ui.theme.Primary

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
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Long?>(null) }
    var isDeletePermanent by remember { mutableStateOf(false) }

    val editLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshMedia()
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
                if (mediaIds.size <= 1) onBack()
            },
            onDismiss = {
                showDeleteDialog = false
                itemToDelete = null
            }
        )
    }

    var isZoomed by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            val currentMediaId = if (mediaIds.isNotEmpty() && pagerState.currentPage < mediaIds.size) {
                mediaIds[pagerState.currentPage]
            } else null
            
            var currentItem by remember { mutableStateOf<MediaItem?>(null) }
            LaunchedEffect(currentMediaId) {
                if (currentMediaId != null) {
                    currentItem = viewModel.getMediaItem(currentMediaId)
                }
            }

            TopAppBar(
                title = {
                    currentItem?.let {
                        Text(it.displayName, color = Color.White, fontSize = 14.sp, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
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
                                isDeletePermanent = false
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            )
        },
        bottomBar = {
            val currentMediaId = if (mediaIds.isNotEmpty() && pagerState.currentPage < mediaIds.size) {
                mediaIds[pagerState.currentPage]
            } else null
            
            var currentItem by remember { mutableStateOf<MediaItem?>(null) }
            LaunchedEffect(currentMediaId) {
                if (currentMediaId != null) {
                    currentItem = viewModel.getMediaItem(currentMediaId)
                }
            }

            if (currentItem != null && !currentItem!!.isTrashed) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                    }
                }
            }
        }
    ) { innerPadding ->
        if (mediaIds.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 2,
                userScrollEnabled = !isZoomed
            ) { page ->
                val mediaId = mediaIds[page]
                MediaItemView(mediaId, viewModel) { scale ->
                    isZoomed = scale > 1f
                }
            }
        }
    }
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
    onScaleChanged: (Float) -> Unit
) {
    var item by remember { mutableStateOf<MediaItem?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(mediaStoreId) {
        item = viewModel.getMediaItem(mediaStoreId)
        scale = 1f
        offset = Offset.Zero
        onScaleChanged(1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Custom transform detector: only claims the gesture (and blocks the
                // parent HorizontalPager's swipe) when it's an actual pinch (2+ fingers)
                // or a pan while already zoomed in. A single-finger drag on a
                // non-zoomed image is left untouched so the pager can swipe to the
                // next/previous photo or video.
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
                                    offset += panChange
                                } else {
                                    offset = Offset.Zero
                                }
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                            // else: single-finger drag on a non-zoomed image —
                            // do not consume, so HorizontalPager can swipe pages.
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        item?.let {
            AsyncImage(
                model = it.uri,
                contentDescription = it.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        } ?: CircularProgressIndicator(color = Primary)
    }
}
