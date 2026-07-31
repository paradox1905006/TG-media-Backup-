package com.dparadox.tgbackup.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.dparadox.tgbackup.data.FileSyncEngine
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.components.*
import com.dparadox.tgbackup.ui.theme.*
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: MainViewModel,
    encodedPath: String,
    onBack: () -> Unit
) {
    val folderPath         = remember(encodedPath) { URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString()) }
    val isAllMedia         = folderPath == "ALL_MEDIA"
    val selectedHashes     by viewModel.selectedMediaHashes.collectAsStateWithLifecycle()
    val allFolders         by viewModel.discoveredFolders.collectAsStateWithLifecycle()

    var mediaList by remember { mutableStateOf<List<FileSyncEngine.MediaFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(folderPath) {
        mediaList = if (isAllMedia) {
            viewModel.getMediaForFolder(null as String?)
        } else {
            viewModel.getMediaForFolder(folderPath)
        }
        isLoading = false
    }

    val filteredMedia = remember(mediaList, selectedFilter) {
        if (selectedFilter == "All") mediaList
        else mediaList.filter { it.folderName == selectedFilter }
    }

    val displayTitle = if (isAllMedia) "Device Photos" else folderPath.substringAfterLast("/").ifBlank { folderPath }

    // Optimization: Pagination / Chunking to prevent OOM on very large galleries
    val pageSize = 60
    var visibleCount by remember(filteredMedia) { mutableIntStateOf(pageSize) }
    val displayedItems = remember(filteredMedia, visibleCount) {
        filteredMedia.take(visibleCount)
    }

    val gridState = rememberLazyGridState()
    
    // Load more when reaching bottom
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= visibleCount - 10 && visibleCount < filteredMedia.size) {
                    visibleCount += pageSize
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            displayTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 17.sp,
                            color      = TextPrimary
                        )
                        if (!isLoading) {
                            Text(
                                "${filteredMedia.size} item${if (filteredMedia.size != 1) "s" else ""}",
                                fontSize = 12.sp,
                                color    = TextMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = Background,
                    scrolledContainerColor = Surface
                )
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ── Info banner ──────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isAllMedia) {
                        // Folder Filter Chips
                        val filters = remember(allFolders) { listOf("All") + allFolders }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            items(filters) { filter ->
                                val selected = selectedFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary,
                                        selectedLabelColor = Color.White,
                                        containerColor = SurfaceAlt,
                                        labelColor = TextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        selectedBorderColor = Primary,
                                        borderColor = Border
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Headline explaining how this works
                    Text(
                        "Select Media for Backup",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tap any photo to select it. Only selected items will be uploaded when Full Device Backup is turned off.",
                        style  = MaterialTheme.typography.bodySmall,
                        color  = TextSecondary,
                        lineHeight = 16.sp
                    )
                    
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedHashes.size} file${if (selectedHashes.size != 1) "s" else ""} selected",
                            color = Primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                        )
                    }
                }
            }

            GradientDivider()

            // ── Grid ────────────────────────────────────────────
            when {
                isLoading -> {
                    // Shimmer loading grid
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
                    }
                }
                filteredMedia.isEmpty() -> {
                    Box(
                        Modifier.fillMaxSize().padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            emoji    = "🖼️",
                            title    = "No media found",
                            subtitle = "This selection doesn't contain any photos or videos."
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(108.dp),
                        state = gridState,
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedItems, key = { it.uri.toString() + it.hash }) { media ->
                            val isSelected = selectedHashes.contains(media.hash)
                            MediaGridItem(media, isSelected) { viewModel.toggleMediaSelected(media.hash) }
                        }
                        
                        if (visibleCount < filteredMedia.size) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Media grid cell ────────────────────────────────────────────────────────

@Composable
fun MediaGridItem(
    media: FileSyncEngine.MediaFile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "grid_scale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceAlt)
            .clickable { onClick() }
    ) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(media.uri)
                .size(200, 200) // Slightly smaller thumbnail for better performance
                .scale(Scale.FILL)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier     = Modifier.fillMaxSize()
        )

        // Selected overlay
        AnimatedVisibility(
            visible = isSelected,
            enter   = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f),
            exit    = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Primary.copy(alpha = 0.25f))
                    .border(3.dp, Primary, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint     = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Video badge
        if (media.mimeType.startsWith("video/")) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            ) {
                Text("▶", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
