package com.dparadox.tgbackup.gallery.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dparadox.tgbackup.gallery.duplicates.DuplicateGroup
import com.dparadox.tgbackup.gallery.duplicates.DuplicatesViewModel
import com.dparadox.tgbackup.ui.components.EmptyState
import com.dparadox.tgbackup.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    onBack: () -> Unit,
    viewModel: DuplicatesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingDeleteIntent by viewModel.pendingDeleteIntent.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeleteConfirmed()
        }
        viewModel.consumeDeleteIntent()
    }

    LaunchedEffect(pendingDeleteIntent) {
        pendingDeleteIntent?.let { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    LaunchedEffect(Unit) {
        if (!state.hasScanned && !state.isScanning) viewModel.scan()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SurfaceElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Delete ${state.selected.size} duplicate file(s)?", fontWeight = FontWeight.Bold) },
            text = { Text("This frees up ${formatBytesDup(viewModel.reclaimableBytes)} of on-device storage. Files already backed up to Telegram remain there.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; viewModel.deleteSelected() },
                    colors = ButtonDefaults.buttonColors(containerColor = Destructive, contentColor = Color.White)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        CenterAlignedTopAppBar(
            title = { Text("Duplicate Finder", fontWeight = FontWeight.Black, color = TextPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = { viewModel.scan() }, enabled = !state.isScanning) {
                    Icon(Icons.Default.Refresh, "Rescan", tint = Primary)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Background)
        )

        when {
            state.isScanning -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Primary)
                        Text(
                            if (state.totalToScan > 0) "Scanning ${state.scannedCount} / ${state.totalToScan}" else "Scanning device media…",
                            color = TextSecondary, fontSize = 13.sp
                        )
                    }
                }
            }
            state.groups.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        emoji = "✨",
                        title = "No duplicates found",
                        subtitle = "Every photo and video on this device is unique."
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PrimaryDim
                        ) {
                            Text(
                                "${state.groups.size} duplicate group(s) found · ${formatBytesDup(viewModel.reclaimableBytes)} selected for removal",
                                modifier = Modifier.padding(14.dp),
                                color = Primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    items(state.groups, key = { it.hash }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            selected = state.selected,
                            onToggle = { viewModel.toggleSelected(it) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (state.groups.isNotEmpty() && state.selected.isNotEmpty()) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomCenter) {
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Destructive, contentColor = Color.White)
            ) {
                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete ${state.selected.size} selected · ${formatBytesDup(viewModel.reclaimableBytes)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selected: Set<android.net.Uri>,
    onToggle: (android.net.Uri) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "${group.items.size} copies · ${formatBytesDup(group.items.firstOrNull()?.sizeBytes ?: 0)} each",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(group.items, key = { it.uri.toString() }) { file ->
                val isSelected = file.uri in selected
                val isNewest = file == group.items.first()
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggle(file.uri) }
                ) {
                    AsyncImage(
                        model = file.uri,
                        contentDescription = file.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    if (isSelected) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    }
                    if (isNewest) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Success.copy(alpha = 0.85f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("KEEP", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Destructive else Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isSelected) Icons.Default.Check else Icons.Default.Circle,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytesDup(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024         -> "${bytes / 1_024} KB"
    else                   -> "$bytes B"
}
