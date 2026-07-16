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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.tgbackup.R
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.theme.*

/**
 * Folders screen — Manually select which folders to back up or use Full Device Backup.
 */
@Composable
fun FoldersScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val folders by viewModel.discoveredFolders.collectAsStateWithLifecycle()
    val fullDeviceSync by viewModel.fullDeviceSyncEnabled.collectAsStateWithLifecycle()
    val watchedFolders by viewModel.watchedFolderUris.collectAsStateWithLifecycle()

    var hasFullAccess by remember { 
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        })
    }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasFullAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (hasFullAccess) viewModel.refreshFolders()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Media Folders",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Manage your backup sources",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
            
            IconButton(
                onClick = { viewModel.refreshFolders() },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Primary.copy(alpha = 0.1f), contentColor = Primary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(Modifier.height(20.dp))

        if (!hasFullAccess) {
            PermissionPrompt {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    storageLauncher.launch(intent)
                } else {
                    // This is simplified, usually you'd use a permission launcher for < Android 11
                }
            }
        } else {
            if (fullDeviceSync) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Success.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Success)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Full Device Sync Active", color = Success, fontWeight = FontWeight.Bold)
                            Text("All folders are being backed up automatically. To select folders manually, disable 'Full Device Sync' in Settings.", color = Success.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text("DEVICE FOLDERS (${folders.size})", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp)
                }
                
                items(folders) { folderPath ->
                    val isSelected = watchedFolders.contains(folderPath)
                    FolderRow(folderPath, isSelected, fullDeviceSync) { viewModel.toggleFolder(it) }
                }
            }
        }
    }
}

@Composable
fun FolderRow(path: String, isSelected: Boolean, fullDeviceSync: Boolean, onToggle: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF151515),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected || fullDeviceSync) Primary else Color(0xFF252525)),
        modifier = Modifier.fillMaxWidth().clickable(enabled = !fullDeviceSync) {
            onToggle(path)
        }
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder, 
                    contentDescription = null, 
                    tint = if (isSelected || fullDeviceSync) Primary else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    path, 
                    color = if (isSelected || fullDeviceSync) Color.White else Color.White.copy(alpha = 0.7f), 
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            if (!fullDeviceSync) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle(path) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Primary, 
                        uncheckedColor = Color.White.copy(alpha = 0.2f),
                        checkmarkColor = Color.White
                    )
                )
            } else {
                Text("AUTO", color = Success, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF151515),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(20.dp))
            Text("File Manager Access", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "To show all your folders and subfolders, this app needs \"All Files Access\". This allows us to discover media deep in your storage just like a File Manager.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Grant All Files Access", fontWeight = FontWeight.Bold)
            }
        }
    }
}
