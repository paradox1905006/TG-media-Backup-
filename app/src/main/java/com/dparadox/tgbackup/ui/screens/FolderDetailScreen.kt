package com.dparadox.tgbackup.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
    val excludedHashes     by viewModel.excludedMediaHashes.collectAsStateWithLifecycle()
    val excludeMarkedEnabled by viewModel.excludeMarked.collectAsStateWithLifecycle()

    var mediaList by remember { mutableStateOf<List<FileSyncEngine.MediaFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(folderPath) {
        mediaList = viewModel.getMediaForFolder(folderPath)
        isLoading = false
    }

    val folderName = folderPath.substringAfterLast("/").ifBlank { folderPath }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            folderName,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 17.sp,
                            color      = TextPrimary
                        )
                        if (!isLoading) {
                            Text(
                                "${mediaList.size} item${if (mediaList.size != 1) "s" else ""}",
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
                    .border(width = 0.dp, color = Color.Transparent, shape = RoundedCornerShape(0))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Marquee hint
                    Text(
                        "Long-press any photo to mark it — marked photos are skipped during backup.",
                        style  = MaterialTheme.typography.bodySmall,
                        color  = Primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )

                    // Exclude toggle
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Skip Marked Files", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "${excludedHashes.size} file${if (excludedHashes.size != 1) "s" else ""} marked",
                                color = TextMuted, fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = excludeMarkedEnabled,
                            onCheckedChange = { viewModel.setExcludeMarked(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor   = Color.White,
                                checkedTrackColor   = Primary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceElevated
                            )
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
                mediaList.isEmpty() -> {
                    Box(
                        Modifier.fillMaxSize().padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            emoji    = "🖼️",
                            title    = "No media found",
                            subtitle = "This folder doesn't contain any photos or videos."
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(108.dp),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(mediaList, key = { _, it -> it.hash }) { index, media ->
                            val isMarked = excludedHashes.contains(media.hash)

                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(index.coerceAtMost(24) * 20L)
                                visible = true
                            }

                            AnimatedVisibility(
                                visible = visible,
                                enter   = fadeIn(tween(180)) + scaleIn(
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    initialScale = 0.88f
                                )
                            ) {
                                MediaGridItem(media, isMarked) { viewModel.toggleMediaMarked(media.hash) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Media grid cell ────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    media: FileSyncEngine.MediaFile,
    isMarked: Boolean,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (isMarked) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "grid_scale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceAlt)
            .combinedClickable(
                onClick     = {},
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model        = media.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier     = Modifier.fillMaxSize()
        )

        // Marked overlay
        AnimatedVisibility(
            visible = isMarked,
            enter   = fadeIn(tween(150)),
            exit    = fadeOut(tween(150))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.35f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Marked",
                        tint     = Color.White,
                        modifier = Modifier.size(20.dp)
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
