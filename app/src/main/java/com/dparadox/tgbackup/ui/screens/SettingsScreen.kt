package com.dparadox.tgbackup.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.work.WorkInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.tgbackup.R
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.components.bounceClick
import com.dparadox.tgbackup.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val validationResult by viewModel.validationResult.collectAsStateWithLifecycle()
    val isValidating by viewModel.isValidating.collectAsStateWithLifecycle()
    val settings = viewModel.settings

    var botToken       by remember { mutableStateOf(settings.botToken) }
    var chatId         by remember { mutableStateOf(settings.chatId) }
    var showToken      by remember { mutableStateOf(false) }
    var uploadAsDoc    by remember { mutableStateOf(settings.uploadAsDocument) }
    var wifiOnly       by remember { mutableStateOf(settings.wifiOnly) }
    var autoSync       by remember { mutableStateOf(settings.autoSyncEnabled) }
    val fullDeviceSync by viewModel.fullDeviceSyncEnabled.collectAsStateWithLifecycle()
    var intervalHours  by remember { mutableIntStateOf(settings.syncIntervalHours) }
    var dbBackup       by remember { mutableStateOf(settings.dbBackupEnabled) }
    var dbInterval     by remember { mutableIntStateOf(settings.dbBackupIntervalHours) }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isOptimized by remember { 
        mutableStateOf(!powerManager.isIgnoringBatteryOptimizations(context.packageName)) 
    }

    val intervalOptions = listOf(1, 3, 6, 12, 24)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SectionLabel("TELEGRAM CREDENTIALS")

        Surface(shape = RoundedCornerShape(14.dp), color = com.dparadox.tgbackup.ui.theme.Surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                FieldLabel("Bot Token")
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("1234567890:ABCDef…", color = TextMuted, fontSize = 13.sp) },
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showToken = !showToken }) {
                            Text(if (showToken) "Hide" else "Show", color = Primary, fontSize = 12.sp)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    colors = credentialFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )

                FieldLabel("Group Chat ID")
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("-1001234567890", color = TextMuted, fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = credentialFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )

                validationResult?.let { result ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (result.ok) Success.copy(alpha = 0.12f) else Destructive.copy(alpha = 0.12f)
                    ) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (result.ok) "✅" else "❌", fontSize = 13.sp)
                            Text(result.message, color = if (result.ok) Success else Destructive, fontSize = 13.sp)
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.validateAndSaveCredentials(botToken.trim(), chatId.trim())
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp).bounceClick(
                        enabled = !isValidating && botToken.isNotBlank() && chatId.isNotBlank(),
                        onClick = { viewModel.validateAndSaveCredentials(botToken.trim(), chatId.trim()) }
                    ),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary
                    ),
                    enabled = !isValidating && botToken.isNotBlank() && chatId.isNotBlank()
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = OnPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test & Save Credentials", fontWeight = FontWeight.Bold)
                }
            }
        }

        SectionLabel("UPLOAD SETTINGS")

        Surface(shape = RoundedCornerShape(14.dp), color = com.dparadox.tgbackup.ui.theme.Surface) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ToggleRow(
                    label = "Auto Backup Service",
                    description = "Enable background scanning and uploading. Recommended to keep ON.",
                    checked = autoSync,
                    onCheckedChange = {
                        autoSync = it
                        settings.autoSyncEnabled = it
                        viewModel.schedulePeriodicSync()
                    }
                )
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                ToggleRow(
                    label = "Full Device Sync",
                    description = "Scan EVERY folder on your device. If OFF, you must select folders manually in the 'Folders' tab.",
                    checked = fullDeviceSync,
                    onCheckedChange = {
                        viewModel.setFullDeviceSync(it)
                    }
                )
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                ToggleRow(
                    label = "Upload as Document",
                    description = "Preserve original quality. Off = Telegram compresses the media.",
                    checked = uploadAsDoc,
                    onCheckedChange = {
                        uploadAsDoc = it
                        settings.uploadAsDocument = it
                    }
                )
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                ToggleRow(
                    label = "Wi-Fi Only",
                    description = "Only upload on Wi-Fi. Recommended to avoid mobile data usage.",
                    checked = wifiOnly,
                    onCheckedChange = {
                        wifiOnly = it
                        settings.wifiOnly = it
                        viewModel.schedulePeriodicSync()
                    }
                )
            }
        }

        SectionLabel("AUTO-SYNC INTERVAL")

        Surface(shape = RoundedCornerShape(14.dp), color = com.dparadox.tgbackup.ui.theme.Surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "How often the background media sync runs.",
                    color = TextMuted, fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    intervalOptions.forEach { h ->
                        val isSelected = intervalHours == h
                        Button(
                            onClick = {
                                intervalHours = h
                                settings.syncIntervalHours = h
                                viewModel.schedulePeriodicSync()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Primary else SurfaceAlt
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (h == 24) "1d" else "${h}h",
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) OnPrimary else TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        SectionLabel("AUTO-DATABASE BACKUP")

        Surface(shape = RoundedCornerShape(14.dp), color = com.dparadox.tgbackup.ui.theme.Surface) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ToggleRow(
                    label = "Auto Database Backup",
                    description = "Periodically upload your upload history to Telegram Cloud.",
                    checked = dbBackup,
                    onCheckedChange = {
                        dbBackup = it
                        settings.dbBackupEnabled = it
                        viewModel.scheduleDbBackup()
                    }
                )
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Backup Frequency", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    
                    val isCloudSyncing by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
                    Button(
                        onClick = { viewModel.backupToCloud { _ -> } },
                        enabled = !isCloudSyncing && settings.isConfigured(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary.copy(alpha = 0.1f), contentColor = Primary),
                        modifier = Modifier.height(32.dp)
                    ) {
                        if (isCloudSyncing) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = Primary, strokeWidth = 2.dp)
                        } else {
                            Text("Backup Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    val dbOptions = listOf(1, 5, 24)
                    dbOptions.forEach { h ->
                        val isSelected = dbInterval == h
                        Button(
                            onClick = {
                                dbInterval = h
                                settings.dbBackupIntervalHours = h
                                viewModel.scheduleDbBackup()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Primary else SurfaceAlt
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            enabled = dbBackup
                        ) {
                            Text(
                                if (h == 24) "1d" else "${h}h",
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) OnPrimary else if (dbBackup) TextMuted else TextMuted.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        SectionLabel("PERFORMANCE")
        Surface(shape = RoundedCornerShape(14.dp), color = com.dparadox.tgbackup.ui.theme.Surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "To prevent the app from stopping during long uploads, disable battery optimization.",
                    color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                )
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().bounceClick(
                        enabled = isOptimized,
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOptimized) Warning else Success.copy(alpha = 0.15f),
                        contentColor = if (isOptimized) Color.Black else Success
                    ),
                    enabled = isOptimized
                ) {
                    Icon(
                        Icons.Default.BatteryFull,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isOptimized) Color.Black else Success
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isOptimized) "Disable Battery Optimization" else "Optimization Disabled",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SectionLabel("LOCAL BACKUP")
        Surface(shape = RoundedCornerShape(14.dp), color = com.dparadox.tgbackup.ui.theme.Surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    uri?.let {
                        viewModel.exportBackup { data ->
                            if (data != null) {
                                context.contentResolver.openOutputStream(it)?.use { stream ->
                                    stream.write(data.toByteArray())
                                }
                            }
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            val data = stream.bufferedReader().readText()
                            viewModel.importBackup(data) { _ -> }
                        }
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                            exportLauncher.launch("TGxDParadox_Backup_$dateStr.json")
                        },
                        modifier = Modifier.weight(1f).bounceClick(onClick = {
                            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                            exportLauncher.launch("TGxDParadox_Backup_$dateStr.json")
                        }),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt)
                    ) {
                        Text("Export File", color = TextPrimary)
                    }

                    Button(
                        onClick = { importLauncher.launch("application/json") },
                        modifier = Modifier.weight(1f).bounceClick(onClick = {
                            importLauncher.launch("application/json")
                        }),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt)
                    ) {
                        Text("Import File", color = TextPrimary)
                    }
                }
            }
        }

        SectionLabel("CLOUD SYNC")
        Surface(shape = RoundedCornerShape(14.dp), color = com.dparadox.tgbackup.ui.theme.Surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val isCloudSyncing by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
                val downloadWorkInfos by viewModel.downloadWorkInfo.collectAsStateWithLifecycle()
                val isDownloading = downloadWorkInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

                Button(
                    onClick = { viewModel.backupToCloud { _ -> } },
                    modifier = Modifier.fillMaxWidth().bounceClick(
                        enabled = !isCloudSyncing && settings.isConfigured(),
                        onClick = { viewModel.backupToCloud { _ -> } }
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary.copy(alpha = 0.15f)),
                    enabled = !isCloudSyncing && settings.isConfigured()
                ) {
                    if (isCloudSyncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Push to Telegram Cloud", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { 
                            if (isDownloading) viewModel.pauseDownload() else viewModel.restoreFromCloud { _ -> } 
                        },
                        modifier = Modifier.weight(1f).bounceClick(
                            enabled = !isCloudSyncing && settings.isConfigured(),
                            onClick = { if (isDownloading) viewModel.pauseDownload() else viewModel.restoreFromCloud { _ -> } }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDownloading) Destructive else Border),
                        enabled = !isCloudSyncing && settings.isConfigured()
                    ) {
                        Icon(
                            if (isDownloading) Icons.Default.Pause else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (isDownloading) Destructive else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isDownloading) "Pause Download" else "Restore History",
                            color = if (isDownloading) Destructive else TextPrimary,
                            fontSize = 13.sp
                        )
                    }

                    if (!isDownloading) {
                        OutlinedButton(
                            onClick = { viewModel.downloadAll() },
                            modifier = Modifier.weight(0.8f).bounceClick(
                                enabled = settings.isConfigured(),
                                onClick = { viewModel.downloadAll() }
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                            enabled = settings.isConfigured()
                        ) {
                            Text("Download All", color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        SectionLabel("HELP & TUTORIAL")
        Surface(shape = RoundedCornerShape(14.dp), color = com.dparadox.tgbackup.ui.theme.Surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TutorialItem("1. Get Bot Token", "Search @BotFather on Telegram, send /newbot and follow instructions to get the token.")
                TutorialItem("2. Get Group Chat ID", "Add bot to your group as Admin, forward any message from group to @userinfobot to get the ID (starts with -100).")
                TutorialItem("3. Setup App", "Paste credentials above and tap 'Test & Save'. Use 'Sync Now' on Dashboard to start.")
                
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                
                val devUri = Uri.parse("https://t.me/paradox1905006")
                val githubUri = Uri.parse("https://github.com/paradox1905006/TG-media-Backup-.git")
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_telegram), contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Developer: ", color = TextPrimary, fontSize = 13.sp)
                    Text(
                        text = "paradox1905006",
                        color = Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, devUri))
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_github), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Source Code: ", color = TextPrimary, fontSize = 13.sp)
                    Text(
                        text = "GitHub Repository",
                        color = Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, githubUri))
                        }
                    )
                }
            }
        }

        SectionLabel("DANGER ZONE")
        var showResetDialog by remember { mutableStateOf(false) }

        OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Destructive),
            border = ButtonDefaults.outlinedButtonBorder.copy()
        ) {
            Text("🗑  Reset Upload Database", fontWeight = FontWeight.SemiBold)
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Upload Database") },
                text = { Text("This deletes all upload history and forces all files to be re-uploaded on next sync. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetDatabase()
                        showResetDialog = false
                    }) { Text("Reset", color = Destructive, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                },
                containerColor = com.dparadox.tgbackup.ui.theme.Surface,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.4f), letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(description, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, lineHeight = 18.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Primary,
                checkedTrackColor = Primary.copy(alpha = 0.3f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
private fun TutorialItem(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(description, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun credentialFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Primary,
    unfocusedBorderColor = Border,
    focusedTextColor     = TextPrimary,
    unfocusedTextColor   = TextPrimary,
    cursorColor          = Primary,
    focusedContainerColor   = SurfaceAlt,
    unfocusedContainerColor = SurfaceAlt,
)
