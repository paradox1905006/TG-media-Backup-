package com.dparadox.tgbackup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.tgbackup.data.UploadedFile
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * History screen — scrollable log of every upload attempt with status badge.
 */
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val history by viewModel.uploadHistory.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Upload History", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryBadge(stats.uploaded.toString(), "Backed up", Success, Modifier.weight(1f))
                    SummaryBadge(stats.failed.toString(),   "Failed",    Destructive, Modifier.weight(1f))
                    SummaryBadge(stats.tooLarge.toString(), ">50 MB",    Warning, Modifier.weight(1f))
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🕐", fontSize = 44.sp)
                    Text("No history yet", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                    Text(
                        "Uploaded files will appear here after your first sync.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp
                    )
                }
            }
        } else {
            items(history, key = { it.hash }) { record ->
                HistoryItem(record)
            }
        }
    }
}

@Composable
private fun SummaryBadge(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.2f), modifier = modifier) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp, color = color)
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun HistoryItem(record: UploadedFile) {
    val (icon, label, color) = when (record.status) {
        "success"   -> Triple("✅", "Uploaded",  Success)
        "too_large" -> Triple("⚠️", ">50 MB",    Warning)
        else        -> Triple("❌", "Failed",     Destructive)
    }

    val dateStr = remember(record.uploadDate) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(record.uploadDate))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = com.dparadox.tgbackup.ui.theme.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status icon box
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text(icon, fontSize = 16.sp) }
            }

            // File info
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(record.fileName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(dateStr, color = TextMuted, fontSize = 11.sp)
                    if (record.fileSize > 0) {
                        Text("· ${formatSize(record.fileSize)}", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }

            // Status badge
            Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return if (bytes < 1024 * 1024) "${bytes / 1024} KB"
    else "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
}
