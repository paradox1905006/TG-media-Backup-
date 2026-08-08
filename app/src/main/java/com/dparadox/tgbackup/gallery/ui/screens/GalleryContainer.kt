package com.dparadox.tgbackup.gallery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dparadox.tgbackup.ui.theme.*

/**
 * Gallery tab container — hosts the Timeline and Albums sub-views with a
 * segmented top toggle, mirroring the app's blue-on-black aesthetic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryContainer(
    onOpenAlbum: (String, String) -> Unit,
    onOpenMedia: (Long) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenTrash: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Top Header
        CenterAlignedTopAppBar(
            title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Gallery",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenFavorites) {
                    Icon(Icons.Default.Star, "Favorites", tint = Primary)
                }
                IconButton(onClick = onOpenTrash) {
                    Icon(Icons.Default.Delete, "Trash", tint = TextMuted)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Background
            )
        )

        // Segmented toggle
        Row(
            Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.9f)
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceAlt)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GalleryTabChip(
                label = "Timeline",
                icon = Icons.Default.GridView,
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.weight(1f)
            )
            GalleryTabChip(
                label = "Albums",
                icon = Icons.Default.PhotoLibrary,
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            },
            label = "gallery_tab"
        ) { tab ->
            when (tab) {
                0 -> TimelineScreen(onOpenMedia)
                1 -> AlbumsScreen(onOpenAlbum)
            }
        }
    }
}

@Composable
private fun GalleryTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = if (selected) Primary else Color.Transparent,
        modifier = modifier.height(38.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) Color.White else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                label,
                color = if (selected) Color.White else TextMuted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
