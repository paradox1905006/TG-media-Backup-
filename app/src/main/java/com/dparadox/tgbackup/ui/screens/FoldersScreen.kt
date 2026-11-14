package com.dparadox.tgbackup.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.components.*
import com.dparadox.tgbackup.ui.theme.*

@Composable
fun FoldersScreen(viewModel: MainViewModel, onFolderClick: (String) -> Unit) {
    val context          = LocalContext.current
    val folders          by viewModel.discoveredFolders.collectAsStateWithLifecycle()
    val thumbnails       by viewModel.folderThumbnails.collectAsStateWithLifecycle()
    val fullDeviceSync   by viewModel.fullDeviceSyncEnabled.collectAsStateWithLifecycle()
    val watchedFolders   by viewModel.watchedFolderUris.collectAsStateWithLifecycle()

    var hasFullAccess by remember { mutableStateOf(hasMediaAccess(context)) }

    // Standard runtime media permissions (READ_MEDIA_* on Android 13+, READ_EXTERNAL_STORAGE
    // before that). Folder discovery goes through MediaStore, so no All Files Access is needed.
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasFullAccess = hasMediaAccess(context)
        if (hasFullAccess) viewModel.refreshFolders()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Page header ───────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Media Folders",
                        style      = MaterialTheme.typography.headlineMedium,
                        color      = TextPrimary
                    )
                    Text(
                        "${folders.size} folder${if (folders.size != 1) "s" else ""} discovered",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                FilledIconButton(
                    onClick = { if (hasFullAccess) viewModel.refreshFolders() },
                    colors  = IconButtonDefaults.filledIconButtonColors(
                        containerColor = SurfaceAlt,
                        contentColor   = Primary
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Refresh", modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── Full device sync toggle ────────────────────────────────
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (fullDeviceSync) PrimaryDim else Surface)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                            .background(if (fullDeviceSync) PrimaryDim else SurfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            null,
                            tint = if (fullDeviceSync) Primary else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Full Device Backup",
                            color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp
                        )
                        Text(
                            if (fullDeviceSync) "All folders backed up automatically"
                            else "Select specific folders below",
                            color = if (fullDeviceSync) Primary.copy(alpha = 0.8f) else TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = fullDeviceSync,
                        onCheckedChange = { viewModel.setFullDeviceSync(it) },
                    )
                }
            }
        }

        // ── Permission required ────────────────────────────────────
        if (!hasFullAccess) {
            item { PermissionCard { storageLauncher.launch(mediaPermissions()) } }
            return@LazyColumn
        }

        // ── Section label ─────────────────────────────────────────
        item { SectionLabel(if (fullDeviceSync) "ALL DEVICE FOLDERS" else "SELECT TO BACKUP") }

        // ── Empty state ───────────────────────────────────────────
        if (folders.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    EmptyState(
                        emoji    = "📁",
                        title    = "No folders found",
                        subtitle = "Tap refresh to scan your device for media folders."
                    )
                }
            }
        }

        // ── Folder cards ──────────────────────────────────────────
        itemsIndexed(folders, key = { _, it -> it }) { _, path ->
            val isSelected = watchedFolders.contains(path)
            val thumb      = thumbnails[path]
            val name       = path.substringAfterLast("/").ifBlank { path }

            FolderCard(
                name       = name,
                path       = path,
                thumbnail  = thumb,
                isSelected = isSelected,
                autoMode   = fullDeviceSync,
                onClick    = { onFolderClick(path) },
                onToggle   = { viewModel.toggleFolder(path) }
            )
        }
    }
}

@Composable
private fun DevicePhotosCard(totalPhotos: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Device Photos", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
                Text("Select individual photos to backup", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Folder row ─────────────────────────────────────────────────────────────

@Composable
private fun FolderCard(
    name: String,
    path: String,
    thumbnail: android.net.Uri?,
    isSelected: Boolean,
    autoMode: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(thumbnail).size(110, 110).scale(Scale.FILL).crossfade(false).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Folder, null, tint = cs.primary, modifier = Modifier.size(26.dp))
                    }
                }
            },
            headlineContent = {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(path, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            },
            trailingContent = {
                if (autoMode) StatusBadge("Auto", Success)
                else Switch(checked = isSelected, onCheckedChange = { onToggle() })
            }
        )
        HorizontalDivider(Modifier.padding(start = 84.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
    }
}

// ── Permission card ────────────────────────────────────────────────────────

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DestructiveDim)
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(24.dp)).background(Destructive.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOff, null, tint = Destructive, modifier = Modifier.size(28.dp))
            }
            Text(
                "Media Access Required",
                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
            Text(
                "To list your photo and video folders, allow access to media. Your files stay private — we only read folder names until you choose what to back up.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onGrant,
                shape   = RoundedCornerShape(14.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = Destructive),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Allow Media Access", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun hasMediaAccess(context: android.content.Context): Boolean =
    mediaPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
