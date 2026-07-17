package com.dparadox.tgbackup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.work.WorkInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.tgbackup.R
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.components.bounceClick
import com.dparadox.tgbackup.ui.theme.*

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val settings = viewModel.settings
    val syncPaused by viewModel.syncPaused.collectAsStateWithLifecycle()
    val restorePaused by viewModel.restorePaused.collectAsStateWithLifecycle()
    
    val syncWorkInfos by viewModel.syncWorkInfo.collectAsStateWithLifecycle()
    val isSyncing = syncWorkInfos.any { !it.state.isFinished }
    
    val downloadWorkInfos by viewModel.downloadWorkInfo.collectAsStateWithLifecycle()
    val isRestoring = downloadWorkInfos.any { !it.state.isFinished }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Header(settings.isConfigured())

        // ── Telegram Cloud Stats ──────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TELEGRAM CLOUD TOTALS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    UsageStat("PHOTOS", stats.photos.toString(), Icons.Default.Photo)
                    UsageStat("VIDEOS", stats.videos.toString(), Icons.Default.Videocam)
                    UsageStat("STORAGE", formatSize(stats.totalSize), Icons.Default.Cloud)
                }
            }
        }

        // ── Actions ────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ActionButton(
                label = if (isSyncing) (if (syncPaused) "RESUME\nSYNC" else "STOP\nSYNC") else "INIT\nSYNC",
                icon = if (isSyncing && syncPaused) Icons.Default.PlayArrow else if (isSyncing) Icons.Default.Pause else Icons.Default.Sync,
                color = if (isSyncing && syncPaused) Warning else Primary,
                modifier = Modifier.weight(1f),
                enabled = settings.isConfigured(),
                onClick = { 
                    if (isSyncing) {
                        viewModel.toggleSyncPause()
                    } else {
                        viewModel.schedulePeriodicSync()
                        viewModel.syncNow() 
                    }
                }
            )
            ActionButton(
                label = "BACKUP\nDB",
                icon = Icons.Default.CloudUpload,
                color = SurfaceAlt,
                contentColor = Primary,
                shadowColor = Primary,
                modifier = Modifier.weight(1f),
                enabled = settings.isConfigured(),
                onClick = { viewModel.backupToCloud { _ -> } }
            )
            ActionButton(
                label = if (isRestoring) (if (restorePaused) "RESUME\nRESTORE" else "PAUSE\nRESTORE") else "RESTORE\nALL",
                icon = if (isRestoring && restorePaused) Icons.Default.PlayArrow else if (isRestoring) Icons.Default.Pause else Icons.Default.CloudDownload,
                color = if (isRestoring && restorePaused) Warning else Success,
                modifier = Modifier.weight(1f),
                enabled = settings.isConfigured(),
                onClick = { 
                    if (isRestoring) {
                        viewModel.toggleRestorePause()
                    } else {
                        viewModel.downloadAll() 
                    }
                }
            )
        }

        // ── Engine Performance ─────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("LIFETIME ENGINE STATS", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("BACKED UP", stats.uploaded.toString(), Success, Modifier.weight(1f))
                StatCard("ERRORS", stats.failed.toString(), Destructive, Modifier.weight(1f))
            }
        }

        // ── Status Info ───────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                InfoItem("Backup Engine", "FULL DEVICE SCAN", Icons.Default.Security)
                Divider()
                InfoItem("Cloud Sync", "ENCRYPTED JSON", Icons.Default.Backup)
                Divider()
                InfoItem("Background", if (settings.autoSyncEnabled) "CONTINUOUS" else "MANUAL ONLY", Icons.Default.Bolt)
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun UsageStat(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    contentColor: Color = OnPrimary,
    shadowColor: Color = color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(70.dp)
            .shadow(if (enabled) 8.dp else 0.dp, RoundedCornerShape(20.dp), ambientColor = shadowColor, spotColor = shadowColor)
            .bounceClick(enabled, onClick),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (enabled) contentColor else TextMuted,
            disabledContainerColor = SurfaceAlt,
            disabledContentColor = TextMuted
        ),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 8.sp,
                letterSpacing = 0.sp,
                lineHeight = 10.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun Header(isConfigured: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(SurfaceAlt, Background))).border(1.dp, Border, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(48.dp))
        }
        Column {
            Text("TG × MEDIA BACKUP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isConfigured) Success else Destructive))
                Spacer(Modifier.width(6.dp))
                Text(if (isConfigured) "PREMIUM ENGINE ACTIVE" else "CONNECTION REQUIRED", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = Surface, border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = modifier.height(100.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Light, color = TextPrimary)
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, icon: ImageVector) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = Border, thickness = 1.dp, modifier = Modifier.padding(start = 36.dp))
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(bytes / 1024.0)
    }
}
