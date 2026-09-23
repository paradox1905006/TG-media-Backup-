package com.dparadox.tgbackup.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.tgbackup.data.UploadedFile
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.components.*
import com.dparadox.tgbackup.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val history by viewModel.uploadHistory.collectAsStateWithLifecycle()
    val stats   by viewModel.stats.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Header ───────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Upload History",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            "${history.size} total records",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    if (stats.failed > 0) {
                        FilledTonalButton(
                            onClick = { viewModel.retryFailedUploads() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DestructiveDim,
                                contentColor   = Destructive
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Retry ${stats.failed}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HistoryStatCard(
                        value = stats.uploaded.toString(),
                        label = "Backed Up",
                        color = Success,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryStatCard(
                        value = stats.failed.toString(),
                        label = "Failed",
                        color = Destructive,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryStatCard(
                        value = stats.tooLarge.toString(),
                        label = "Skipped",
                        color = Warning,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (history.isNotEmpty()) {
                    SectionLabel("LAST 14 DAYS")
                    ActivityTimeline(history)
                }

                SectionLabel("RECENT UPLOADS")
            }
        }

        // ── Empty state ───────────────────────────────────────────
        if (history.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        emoji    = "📂",
                        title    = "No uploads yet",
                        subtitle = "Once your first sync runs, all uploaded files will appear here."
                    )
                }
            }
        }

        // ── List items ────────────────────────────────────────────
        itemsIndexed(
            history,
            key = { _, it -> it.hash },
            contentType = { _, _ -> "history_item" } // lets LazyColumn reuse composition slots while scrolling
        ) { _, record ->
            HistoryItem(record)
        }
    }
}

// ── Activity timeline (last 14 days) ────────────────────────────────────────

@Composable
private fun ActivityTimeline(history: List<UploadedFile>) {
    val dayFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val cal = remember { Calendar.getInstance() }

    // Build 14 day buckets ending today, counting only successful uploads.
    val buckets = remember(history) {
        val dayKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val counts = history
            .filter { it.status == "success" }
            .groupingBy { dayKeyFormat.format(Date(it.uploadDate)) }
            .eachCount()

        val today = Calendar.getInstance()
        (13 downTo 0).map { offset ->
            val c = today.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -offset)
            val key = dayKeyFormat.format(c.time)
            key to (counts[key] ?: 0)
        }
    }

    val maxCount = (buckets.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            buckets.forEach { (key, count) ->
                val barHeightFraction = count / maxCount.toFloat()
                val dayOfMonth = remember(key) {
                    try {
                        val d = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(key)
                        if (d != null) dayFormat.format(d) else ""
                    } catch (e: Exception) { "" }
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(if (count > 0) barHeightFraction.coerceAtLeast(0.06f) else 0.02f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (count > 0) Primary else BorderSubtle)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(dayOfMonth, fontSize = 8.sp, color = TextMuted)
                }
            }
        }
    }
}

// ── Stat card ──────────────────────────────────────────────────────────────

@Composable
private fun HistoryStatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                value,
                fontWeight = FontWeight.Black,
                fontSize   = 22.sp,
                color      = color
            )
            Text(
                label,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color      = color.copy(alpha = 0.7f)
            )
        }
    }
}

// ── History item ───────────────────────────────────────────────────────────

@Composable
private fun HistoryItem(record: UploadedFile) {
    val (statusIcon, statusLabel, statusColor) = remember(record.status) {
        when (record.status) {
            "success"   -> Triple(Icons.Default.CheckCircle, "Uploaded",  Success)
            "too_large" -> Triple(Icons.Default.Warning,     "Skipped",   Warning)
            else        -> Triple(Icons.Default.Error,        "Failed",   Destructive)
        }
    }

    val dateStr = remember(record.uploadDate) {
        SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(record.uploadDate))
    }

    val mediaIcon = remember(record.mimeType) {
        when {
            record.mimeType.startsWith("video/") -> Icons.Default.Videocam
            record.mimeType.startsWith("image/") -> Icons.Default.Photo
            else                                 -> Icons.Default.InsertDriveFile
        }
    }

    val accentBrush = remember(statusColor) {
        Brush.verticalGradient(listOf(statusColor, statusColor.copy(alpha = 0.2f)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
    ) {
        // Subtle left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentBrush)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Media type icon
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(20.dp))
            }

            // File info
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    record.fileName,
                    color      = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    maxLines   = 1,
                    overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(mediaIcon, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                    Text(dateStr, color = TextMuted, fontSize = 11.sp)
                    if (record.fileSize > 0) {
                        Text("·", color = TextHint, fontSize = 11.sp)
                        Text(formatBytes(record.fileSize), color = TextMuted, fontSize = 11.sp)
                    }
                }
            }

            // Status badge
            StatusBadge(statusLabel, statusColor)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    bytes >= 1_024     -> "${bytes / 1_024} KB"
    else               -> "$bytes B"
}
