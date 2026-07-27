package com.dparadox.tgbackup.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.tgbackup.R
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.components.*
import com.dparadox.tgbackup.ui.theme.*

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val stats          by viewModel.stats.collectAsStateWithLifecycle()
    val settings        = viewModel.settings
    val syncPaused     by viewModel.syncPaused.collectAsStateWithLifecycle()
    val restorePaused  by viewModel.restorePaused.collectAsStateWithLifecycle()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
    val cloudFolders   by viewModel.cloudFolders.collectAsStateWithLifecycle()

    val syncWorkInfos     by viewModel.syncWorkInfo.collectAsStateWithLifecycle()
    val isSyncing          = syncWorkInfos.any { !it.state.isFinished }
    val downloadWorkInfos by viewModel.downloadWorkInfo.collectAsStateWithLifecycle()
    val isRestoring        = downloadWorkInfos.any { !it.state.isFinished }

    // ── Cloud folder picker dialog ─────────────────────────────────────
    cloudFolders?.let { folders ->
        CloudFolderPickerDialog(
            folderCounts = folders,
            onConfirm    = { viewModel.confirmCloudRestore(it) },
            onDismiss    = { viewModel.cancelCloudRestore() }
        )
    }

    // ── Cloud scan loading dialog ─────────────────────────────────────
    if (isCloudSyncing) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, Border, RoundedCornerShape(28.dp))
            ) {
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    CircularProgressIndicator(
                        color       = Primary,
                        strokeWidth = 2.5.dp,
                        modifier    = Modifier.size(40.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Scanning Cloud", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text("Discovering your backup folders…", fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    // ── Main scroll content ───────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // Header
        Header(settings.isConfigured())

        // Cloud stats
        CloudStatsCard(stats)

        // Sync controls
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("SYNC ENGINE")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumActionButton(
                    label   = if (isSyncing) (if (syncPaused) "Resume Sync" else "Stop Sync") else "Start Sync",
                    icon    = if (isSyncing && syncPaused) Icons.Default.PlayArrow else if (isSyncing) Icons.Default.Stop else Icons.Default.Sync,
                    color   = if (isSyncing && syncPaused) Warning else Primary,
                    enabled = settings.isConfigured(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isSyncing) viewModel.toggleSyncPause() else {
                            viewModel.schedulePeriodicSync(); viewModel.syncNow()
                        }
                    }
                )
                PremiumActionButton(
                    label   = "Backup DB",
                    icon    = Icons.Default.CloudUpload,
                    color   = SurfaceElevated,
                    contentColor = Primary,
                    enabled = settings.isConfigured() && !isCloudSyncing,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.backupToCloud {} }
                )
            }
        }

        // Cloud restore
        CloudRestoreCard(
            isConfigured    = settings.isConfigured(),
            isRestoring     = isRestoring,
            restorePaused   = restorePaused,
            isCloudSyncing  = isCloudSyncing,
            onBrowseCloud   = { viewModel.restoreFromCloud {} },
            onRestoreAll    = { viewModel.downloadAll() },
            onPause         = { viewModel.toggleRestorePause(); viewModel.pauseDownload() },
            onResume        = { viewModel.downloadAll() },
            onCancel        = { viewModel.pauseDownload() }
        )

        // Lifetime stats
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("LIFETIME STATS")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label  = "BACKED UP",
                    value  = stats.uploaded.toString(),
                    accentColor = Success,
                    icon   = Icons.Default.CloudDone,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label  = "ERRORS",
                    value  = stats.failed.toString(),
                    accentColor = Destructive,
                    icon   = Icons.Default.ErrorOutline,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label  = "SKIPPED",
                    value  = stats.tooLarge.toString(),
                    accentColor = Warning,
                    icon   = Icons.Default.Block,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Engine info
        EngineInfoCard(settings)

        Spacer(Modifier.height(8.dp))
    }
}

// ── Header ─────────────────────────────────────────────────────────────────

@Composable
private fun Header(isConfigured: Boolean) {
    Row(
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Logo box with glow ring
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(PrimaryDim)
                .border(1.dp, PrimaryBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(44.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "TG × Media Backup",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.3).sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isConfigured) PulsingDot(Success) else Box(Modifier.size(8.dp).clip(CircleShape).background(Destructive))
                Text(
                    if (isConfigured) "Engine Active" else "Setup Required",
                    color      = if (isConfigured) Success else Destructive,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

// ── Cloud stats card ───────────────────────────────────────────────────────

@Composable
private fun CloudStatsCard(stats: MainViewModel.Stats) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(listOf(PrimaryDim, Color.Transparent, Color.Transparent))
            )
            .border(1.dp, PrimaryBorder, RoundedCornerShape(22.dp))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Cloud, null, tint = Primary, modifier = Modifier.size(16.dp))
                Text("TELEGRAM CLOUD", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, color = Primary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                CloudStat(Icons.Default.Photo,    "Photos",  stats.photos.toString())
                Box(Modifier.width(1.dp).height(40.dp).background(Border))
                CloudStat(Icons.Default.Videocam, "Videos",  stats.videos.toString())
                Box(Modifier.width(1.dp).height(40.dp).background(Border))
                CloudStat(Icons.Default.Storage,  "Storage", formatSize(stats.totalSize))
            }
        }
    }
}

@Composable
private fun CloudStat(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.3).sp)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextMuted, letterSpacing = 0.5.sp)
    }
}

// ── Cloud restore card ─────────────────────────────────────────────────────

@Composable
private fun CloudRestoreCard(
    isConfigured: Boolean,
    isRestoring: Boolean,
    restorePaused: Boolean,
    isCloudSyncing: Boolean,
    onBrowseCloud: () -> Unit,
    onRestoreAll: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("CLOUD RESTORE")

        PremiumCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Status row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            isRestoring && !restorePaused -> PulsingDot(Success)
                            isRestoring && restorePaused  -> PulsingDot(Warning)
                            else -> Box(Modifier.size(8.dp).clip(CircleShape).background(TextMuted))
                        }
                        Text(
                            when {
                                isCloudSyncing                -> "Scanning folders…"
                                isRestoring && !restorePaused -> "Downloading"
                                isRestoring && restorePaused  -> "Paused"
                                else                          -> "Ready"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                            color      = TextPrimary
                        )
                    }

                    if (isRestoring) {
                        StatusBadge(
                            label = if (restorePaused) "PAUSED" else "ACTIVE",
                            color = if (restorePaused) Warning else Success
                        )
                    }
                }

                Text(
                    if (isRestoring)
                        "Your media is being restored from Telegram Cloud to your device gallery."
                    else
                        "Browse your Telegram Cloud backup and selectively restore specific folders, or download everything at once.",
                    fontSize   = 13.sp,
                    color      = TextSecondary,
                    lineHeight = 19.sp
                )

                // Buttons
                AnimatedContent(
                    targetState = isRestoring,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "restore_buttons"
                ) { restoring ->
                    if (!restoring) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Browse cloud folders
                            Button(
                                onClick  = onBrowseCloud,
                                enabled  = isConfigured && !isCloudSyncing,
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor        = Primary,
                                    contentColor          = Color.White,
                                    disabledContainerColor = SurfaceElevated,
                                    disabledContentColor  = TextMuted
                                ),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("Browse", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // Restore all
                            OutlinedButton(
                                onClick  = onRestoreAll,
                                enabled  = isConfigured && !isCloudSyncing,
                                shape    = RoundedCornerShape(14.dp),
                                border   = androidx.compose.foundation.BorderStroke(
                                    1.dp, if (isConfigured) Success.copy(alpha = 0.5f) else Border
                                ),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Success, disabledContentColor = TextMuted),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("Restore All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Pause / Resume
                            Button(
                                onClick  = if (restorePaused) onResume else onPause,
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = if (restorePaused) Success else Warning,
                                    contentColor   = Color.Black
                                ),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(
                                    if (restorePaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    null, modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (restorePaused) "Resume" else "Pause",
                                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp
                                )
                            }

                            // Cancel
                            OutlinedButton(
                                onClick  = onCancel,
                                shape    = RoundedCornerShape(14.dp),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, Destructive.copy(alpha = 0.5f)),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Destructive),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Engine info card ────────────────────────────────────────────────────────

@Composable
private fun EngineInfoCard(settings: com.dparadox.tgbackup.data.SettingsManager) {
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(4.dp)) {
            EngineInfoRow(Icons.Default.Security,   "Backup Mode",  "Full Device Scan")
            GradientDivider(Modifier.padding(horizontal = 16.dp))
            EngineInfoRow(Icons.Default.Backup,     "Cloud Sync",   "AES-256 Encrypted")
            GradientDivider(Modifier.padding(horizontal = 16.dp))
            EngineInfoRow(Icons.Default.Bolt,       "Background",   if (settings.autoSyncEnabled) "Continuous" else "Manual Only")
        }
    }
}

@Composable
private fun EngineInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        Text(label, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

// ── Premium action button ──────────────────────────────────────────────────

@Composable
private fun PremiumActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (enabled) 1f else 0.97f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "btn_scale"
    )
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bounceClick(enabled, onClick),
        shape  = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor        = color,
            contentColor          = contentColor,
            disabledContainerColor = SurfaceAlt,
            disabledContentColor  = TextMuted
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
    }
}

// ── Cloud folder picker dialog ─────────────────────────────────────────────

@Composable
private fun CloudFolderPickerDialog(
    folderCounts: Map<String, Int>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val folders         = remember(folderCounts) { folderCounts.keys.sorted() }
    val selectedFolders = remember { mutableStateOf(folders.toSet()) }
    val allSelected     = selectedFolders.value.size == folders.size
    val totalFiles      = selectedFolders.value.sumOf { folderCounts[it] ?: 0 }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceElevated)
                .border(1.dp, Border, RoundedCornerShape(28.dp))
        ) {
            Column(Modifier.fillMaxSize()) {

                // Header
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(PrimaryDim, Color.Transparent))
                        )
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(PrimaryDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudDownload, null, tint = Primary, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text("Cloud Restore", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary, letterSpacing = (-0.2).sp)
                            Text("Select folders to download", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    StatusBadge("${folders.size} folders found", Primary)
                }

                GradientDivider()

                // Select all
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFolders.value = if (allSelected) emptySet() else folders.toSet() }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (allSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            null, tint = if (allSelected) Primary else TextMuted, modifier = Modifier.size(22.dp)
                        )
                        Text("Select All", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("${selectedFolders.value.size} / ${folders.size}", color = TextMuted, fontSize = 12.sp)
                }

                GradientDivider()

                // Folder list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(folders, key = { it }) { folder ->
                        val isSelected  = folder in selectedFolders.value
                        val fileCount   = folderCounts[folder] ?: 0
                        val displayName = folder.ifBlank { "Uncategorised" }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFolders.value = if (isSelected)
                                        selectedFolders.value - folder
                                    else
                                        selectedFolders.value + folder
                                }
                                .background(if (isSelected) PrimaryDim else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                null, tint = if (isSelected) Primary else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                                    .background(if (isSelected) PrimaryDim else SurfaceAlt),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Folder, null,
                                    tint = if (isSelected) Primary else TextMuted,
                                    modifier = Modifier.size(19.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    displayName,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text("$fileCount file${if (fileCount != 1) "s" else ""}", color = TextMuted, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                GradientDivider()

                // Action bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape   = RoundedCornerShape(14.dp),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) { Text("Cancel", fontWeight = FontWeight.SemiBold) }

                    Button(
                        onClick  = { onConfirm(selectedFolders.value) },
                        enabled  = selectedFolders.value.isNotEmpty(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor        = Primary,
                            contentColor          = Color.White,
                            disabledContainerColor = SurfaceAlt,
                            disabledContentColor  = TextMuted
                        ),
                        modifier = Modifier.weight(2f).height(50.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download  ·  $totalFiles files", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "${bytes / 1024} KB"
    }
}
