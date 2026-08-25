package com.dparadox.tgbackup.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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

    var hasFullAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        )
    }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasFullAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (hasFullAccess) viewModel.refreshFolders()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (fullDeviceSync) PrimaryDim else Surface)
                    .border(
                        1.dp,
                        if (fullDeviceSync) PrimaryBorder else Border,
                        RoundedCornerShape(20.dp)
                    )
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

        // ── Permission required ────────────────────────────────────
        if (!hasFullAccess) {
            item { PermissionCard { storageLauncher.launch(permissionIntent()) } }
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
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Primary, Color(0xFF6C63FF))))
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Device Photos", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Select individual photos to backup", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            Icon(Icons.Default.ArrowForwardIos, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Folder card ────────────────────────────────────────────────────────────

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
    val cardBg = if (isSelected && !autoMode) PrimaryDim else Surface
    val borderColor = if (isSelected && !autoMode) PrimaryBorder else Border

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Thumbnail or folder icon
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbnail)
                            .size(110, 110)
                            .scale(Scale.FILL)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Tint overlay if selected
                    if (isSelected && !autoMode) {
                        Box(Modifier.fillMaxSize().background(Primary.copy(alpha = 0.18f)))
                    }
                } else {
                    Icon(
                        Icons.Default.Folder,
                        null,
                        tint = if (isSelected && !autoMode) Primary else TextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Name + path
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    name,
                    color      = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    maxLines   = 1,
                    overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    path,
                    color    = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Selection control or AUTO badge
            if (autoMode) {
                StatusBadge("AUTO", Success)
            } else {
                Checkbox(
                    checked  = isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor        = Primary,
                        uncheckedColor      = TextMuted,
                        checkmarkColor      = Color.White
                    )
                )
            }
        }
    }
}

// ── Permission card ────────────────────────────────────────────────────────

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(DestructiveDim)
            .border(1.dp, Destructive.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Destructive.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOff, null, tint = Destructive, modifier = Modifier.size(28.dp))
            }
            Text(
                "File Access Required",
                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
            Text(
                "To discover all your media folders, grant \"All Files Access\". Your files stay private — we only scan folder names.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onGrant,
                shape   = RoundedCornerShape(14.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = Destructive),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Grant Access", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun permissionIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:com.dparadox.tgbackup"))
    else
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:com.dparadox.tgbackup"))
