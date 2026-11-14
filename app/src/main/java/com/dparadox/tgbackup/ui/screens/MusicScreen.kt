package com.dparadox.tgbackup.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.components.*
import com.dparadox.tgbackup.ui.theme.*

@Composable
fun MusicScreen(viewModel: MainViewModel) {
    val tracks       by viewModel.musicTracks.collectAsStateWithLifecycle()
    val isLoading    by viewModel.isLoadingMusic.collectAsStateWithLifecycle()
    val nowPlayingId by viewModel.nowPlayingId.collectAsStateWithLifecycle()
    val isPlaying    by viewModel.isMusicPlaying.collectAsStateWithLifecycle()
    val includeMusicBackup by viewModel.includeMusicBackup.collectAsStateWithLifecycle()
    val autoBackupMusic by viewModel.autoBackupMusicEnabled.collectAsStateWithLifecycle()
    val selectedHashes by viewModel.selectedMediaHashes.collectAsStateWithLifecycle()
    val musicFolders by viewModel.discoveredMusicFolders.collectAsStateWithLifecycle()
    val watchedMusicFolders by viewModel.watchedMusicFolders.collectAsStateWithLifecycle()
    var folderSectionExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadMusicTracks() }
    DisposableEffect(Unit) { onDispose { viewModel.stopMusicPlayback() } }

    val backedUpCount = remember(tracks) { tracks.count { it.isBackedUp } }
    val totalSize = remember(tracks) { tracks.sumOf { it.sizeBytes } }
    val tracksPerFolder = remember(tracks) { tracks.groupingBy { it.folderName }.eachCount() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Music", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text("Your device's audio library", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    IconButton(onClick = { viewModel.loadMusicTracks() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = TextSecondary)
                    }
                }

                // Stats card
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceAlt)
                        .padding(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MusicStat(tracks.size.toString(), "Tracks", Primary)
                        MusicStat(backedUpCount.toString(), "Backed Up", Success)
                        MusicStat(formatBytesM(totalSize), "Total Size", TextSecondary)
                    }
                }

                // Master enable — whether music participates in backup at all
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Surface)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Include Music in Backup", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Back up these tracks the same way as photos & videos.", color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = includeMusicBackup,
                            onCheckedChange = { viewModel.setIncludeMusicBackup(it) }
                        )
                    }
                }

                // Auto vs. selective — only meaningful once music backup is enabled.
                // Mirrors the "Full Device Backup" toggle on the Folders screen.
                if (includeMusicBackup) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (autoBackupMusic) PrimaryDim else Surface)
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
                                    .background(if (autoBackupMusic) PrimaryDim else SurfaceAlt),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LibraryMusic,
                                    null,
                                    tint = if (autoBackupMusic) Primary else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Auto Backup All Music", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    if (autoBackupMusic) "Every track is backed up automatically"
                                    else "Select specific tracks below",
                                    color = if (autoBackupMusic) Primary.copy(alpha = 0.8f) else TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = autoBackupMusic,
                                onCheckedChange = { viewModel.setAutoBackupMusicEnabled(it) }
                            )
                        }
                    }
                }

                // Folder-level selection — same idea as the Folders screen, just
                // scoped to audio: check a whole folder instead of one track at a time.
                if (includeMusicBackup && !autoBackupMusic && musicFolders.isNotEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Surface)
                    ) {
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { folderSectionExpanded = !folderSectionExpanded }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Folder, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Backup by Folder", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            if (watchedMusicFolders.isEmpty()) "Or check a whole folder instead of single tracks"
                                            else "${watchedMusicFolders.size} folder${if (watchedMusicFolders.size != 1) "s" else ""} selected",
                                            color = TextMuted, fontSize = 11.sp
                                        )
                                    }
                                }
                                Icon(
                                    if (folderSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, tint = TextMuted
                                )
                            }
                            AnimatedVisibility(visible = folderSectionExpanded) {
                                Column(Modifier.padding(bottom = 8.dp)) {
                                    musicFolders.forEach { folder ->
                                        val isSelected = watchedMusicFolders.contains(folder)
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.toggleMusicFolder(folder) }
                                                .background(if (isSelected) PrimaryDim else Color.Transparent)
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Folder, null,
                                                tint = if (isSelected) Primary else TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(folder, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${tracksPerFolder[folder] ?: 0} tracks", color = TextMuted, fontSize = 11.sp)
                                            }
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { viewModel.toggleMusicFolder(folder) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (includeMusicBackup) {
            item {
                SectionLabel(if (autoBackupMusic) "ALL TRACKS AUTO-BACKED UP" else "OR SELECT INDIVIDUAL TRACKS")
            }
        }

        if (isLoading && tracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
        } else if (tracks.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.MusicOff, null, tint = TextMuted, modifier = Modifier.size(40.dp))
                    Text("No music found on this device", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            items(tracks, key = { it.mediaStoreId }) { track ->
                MusicRow(
                    track = track,
                    isPlaying = nowPlayingId == track.mediaStoreId && isPlaying,
                    isCurrent = nowPlayingId == track.mediaStoreId,
                    onTap = { viewModel.toggleMusicPlayback(track) },
                    selectionMode = includeMusicBackup && !autoBackupMusic,
                    isSelected = watchedMusicFolders.contains(track.folderName) || (track.hash != null && selectedHashes.contains(track.hash)),
                    onToggleSelected = { track.hash?.let { viewModel.toggleMediaSelected(it) } }
                )
            }
        }
    }
}

@Composable
private fun MusicStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun MusicRow(
    track: MainViewModel.MusicTrack,
    isPlaying: Boolean,
    isCurrent: Boolean,
    onTap: () -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelected: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (isCurrent) cs.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                .let { if (selectionMode) it.clickable { onToggleSelected() } else it.clickable { onTap() } }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FilledTonalIconButton(
                onClick = onTap,
                modifier = Modifier.size(44.dp),
                colors = if (isCurrent) IconButtonDefaults.filledIconButtonColors()
                         else IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(
                    if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.artist} · ${formatDurationM(track.durationMs)}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
            } else {
                Icon(
                    if (track.isBackedUp) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    null,
                    tint = if (track.isBackedUp) Success else cs.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 66.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
    }
}

private fun formatDurationM(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatBytesM(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L         -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L                 -> "%.0f KB".format(bytes / 1024.0)
    else                           -> "$bytes B"
}
