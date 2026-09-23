package com.dparadox.tgbackup.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.dparadox.tgbackup.ui.components.*
import com.dparadox.tgbackup.ui.theme.*

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context           = LocalContext.current
    val validationResult  by viewModel.validationResult.collectAsStateWithLifecycle()
    val isValidating      by viewModel.isValidating.collectAsStateWithLifecycle()
    val cloudFolders      by viewModel.cloudFolders.collectAsStateWithLifecycle()
    val isCloudSyncing    by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
    val storageStats      by viewModel.storageStats.collectAsStateWithLifecycle()
    val settings           = viewModel.settings

    // Cloud folder dialog (also accessible from Settings for full-feature restore flow)
    cloudFolders?.let { folders ->
        CloudRestoreDialog(
            folderCounts = folders,
            onConfirm    = { viewModel.confirmCloudRestore(it) },
            onDismiss    = { viewModel.cancelCloudRestore() }
        )
    }

    var botToken      by remember { mutableStateOf(settings.botToken) }
    var chatId        by remember { mutableStateOf(settings.chatId) }
    var showToken     by remember { mutableStateOf(false) }
    var uploadAsDoc   by remember { mutableStateOf(settings.uploadAsDocument) }
    var splitUploadEnabled by remember { mutableStateOf(settings.splitUploadEnabled) }
    var wifiOnly      by remember { mutableStateOf(settings.wifiOnly) }
    var quietHoursEnabled by remember { mutableStateOf(settings.quietHoursEnabled) }
    var quietStart    by remember { mutableIntStateOf(settings.quietHoursStart) }
    var quietEnd      by remember { mutableIntStateOf(settings.quietHoursEnd) }
    var autoSync      by remember { mutableStateOf(settings.autoSyncEnabled) }
    var intervalHours by remember { mutableIntStateOf(settings.syncIntervalHours) }
    var dbBackup      by remember { mutableStateOf(settings.dbBackupEnabled) }
    var dbInterval    by remember { mutableIntStateOf(settings.dbBackupIntervalHours) }
    var dbAutoDeleteOld by remember { mutableStateOf(settings.dbBackupAutoDeleteOld) }
    var encryptionEnabled by remember { mutableStateOf(settings.encryptionEnabled) }
    var darkModeEnabled by remember { mutableStateOf(settings.darkModeEnabled) }
    var includeMusicBackup by remember { mutableStateOf(settings.includeMusicBackup) }
    val intervalOptions = listOf(1, 3, 6, 12, 24)

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isOptimized by remember {
        mutableStateOf(!powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Page title ─────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("Configure your backup engine", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }

        // ── Telegram credentials ───────────────────────────────
        SettingsSection(
            label = "TELEGRAM CREDENTIALS",
            icon  = Icons.Default.Key
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Bot token
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Bot Token")
                    OutlinedTextField(
                        value              = botToken,
                        onValueChange      = { botToken = it },
                        modifier           = Modifier.fillMaxWidth(),
                        placeholder        = { Text("1234567890:ABCDef…", color = TextMuted, fontSize = 13.sp) },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon       = {
                            TextButton(onClick = { showToken = !showToken }) {
                                Text(if (showToken) "Hide" else "Show", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        keyboardOptions    = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine         = true,
                        colors             = credentialFieldColors(),
                        shape              = RoundedCornerShape(12.dp)
                    )
                }

                // Chat ID
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Group / Channel Chat ID")
                    OutlinedTextField(
                        value           = chatId,
                        onValueChange   = { chatId = it },
                        modifier        = Modifier.fillMaxWidth(),
                        placeholder     = { Text("-100 1234567890", color = TextMuted, fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine      = true,
                        colors          = credentialFieldColors(),
                        shape           = RoundedCornerShape(12.dp)
                    )
                }

                // Validation result
                AnimatedVisibility(visible = validationResult != null) {
                    validationResult?.let { result ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (result.ok) SuccessDim else DestructiveDim)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    if (result.ok) Icons.Default.CheckCircle else Icons.Default.Error,
                                    null,
                                    tint     = if (result.ok) Success else Destructive,
                                    modifier = Modifier.size(18.dp).padding(top = 1.dp)
                                )
                                Text(
                                    result.message,
                                    color    = if (result.ok) Success else Destructive,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Save button
                Button(
                    onClick  = { viewModel.validateAndSaveCredentials(botToken.trim(), chatId.trim()) },
                    enabled  = !isValidating && botToken.isNotBlank() && chatId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(13.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor        = Primary,
                        contentColor          = Color.White,
                        disabledContainerColor = SurfaceElevated,
                        disabledContentColor  = TextMuted
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Validating…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Verified, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test & Save Credentials", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Appearance ──────────────────────────────────────────
        SettingsSection(label = "APPEARANCE", icon = Icons.Default.DarkMode) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                PremiumToggle(
                    label       = "Dark Mode",
                    description = "Switch off for a clean light theme across the whole app.",
                    checked     = darkModeEnabled,
                    onChecked   = { darkModeEnabled = it; viewModel.setDarkModeEnabled(it) }
                )
            }
        }

        // ── Upload settings ────────────────────────────────────
        SettingsSection(label = "UPLOAD SETTINGS", icon = Icons.Default.CloudUpload) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                PremiumToggle(
                    label       = "Auto Backup Service",
                    description = "Continuously scan and upload new media in the background.",
                    checked     = autoSync,
                    onChecked   = { autoSync = it; settings.autoSyncEnabled = it; viewModel.schedulePeriodicSync() }
                )
                GradientDivider(Modifier.padding(horizontal = 14.dp))
                PremiumToggle(
                    label       = "Wi-Fi Only",
                    description = "Only upload when connected to Wi-Fi — saves mobile data.",
                    checked     = wifiOnly,
                    onChecked   = { wifiOnly = it; settings.wifiOnly = it }
                )
                GradientDivider(Modifier.padding(horizontal = 14.dp))
                PremiumToggle(
                    label       = "Upload as File",
                    description = "Send via Telegram's document mode — preserves original quality, no compression. Off uses photo/video mode (compressed, faster).",
                    checked     = uploadAsDoc,
                    onChecked   = { uploadAsDoc = it; settings.uploadAsDocument = it }
                )
                GradientDivider(Modifier.padding(horizontal = 14.dp))
                PremiumToggle(
                    label       = "Split-Upload Large Files",
                    description = "Files over 50MB are chunked and uploaded automatically, then reassembled on restore. Off: large files are skipped and shown as \"Too Large\" in history instead.",
                    checked     = splitUploadEnabled,
                    onChecked   = { splitUploadEnabled = it; settings.splitUploadEnabled = it }
                )
                GradientDivider(Modifier.padding(horizontal = 14.dp))
                PremiumToggle(
                    label       = "Client-Side Encryption (AES)",
                    description = "Encrypt files before upload. Safe but slower restoration.",
                    checked     = encryptionEnabled,
                    onChecked   = { encryptionEnabled = it; settings.encryptionEnabled = it; viewModel.setEncryptionEnabled(it) }
                )
                
                // Encryption Status Pane (MT-Manager Style)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // Internal Storage Pane
                    StoragePane(
                        label = "Internal",
                        encrypted = storageStats.internalEncrypted,
                        plain = storageStats.internalPlain,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (storageStats.hasExternalStorage) {
                        // Vertical Divider
                        Box(Modifier.fillMaxHeight().width(1.dp).background(Border))
                        
                        // External Storage Pane
                        StoragePane(
                            label = "External",
                            encrypted = storageStats.externalEncrypted,
                            plain = storageStats.externalPlain,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                GradientDivider(Modifier.padding(horizontal = 14.dp))
                PremiumToggle(
                    label       = "Music Backup",
                    description = "Also scan and back up audio files (MP3, M4A, WAV, FLAC…) alongside photos and videos.",
                    checked     = includeMusicBackup,
                    onChecked   = { includeMusicBackup = it; viewModel.setIncludeMusicBackup(it) }
                )

                if (autoSync) {
                    GradientDivider(Modifier.padding(horizontal = 14.dp))
                    // Interval selector
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FieldLabel("Sync Interval")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            intervalOptions.forEach { h ->
                                val selected = intervalHours == h
                                FilterChip(
                                    selected = selected,
                                    onClick  = { intervalHours = h; settings.syncIntervalHours = h; viewModel.schedulePeriodicSync() },
                                    label    = { Text("${h}h", fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary,
                                        selectedLabelColor     = Color.White,
                                        containerColor         = SurfaceAlt,
                                        labelColor             = TextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        selectedBorderColor = Primary,
                                        borderColor = Border
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }

                GradientDivider(Modifier.padding(horizontal = 14.dp))
                PremiumToggle(
                    label       = "Quiet-Hours Backup Window",
                    description = "Only run the scheduled backup between these hours (e.g. overnight). Manual \"Start Sync\" always runs right away.",
                    checked     = quietHoursEnabled,
                    onChecked   = { quietHoursEnabled = it; settings.quietHoursEnabled = it }
                )
                if (quietHoursEnabled) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FieldLabel("Starts At")
                                HourStepper(
                                    hour = quietStart,
                                    onChange = { quietStart = it; settings.quietHoursStart = it }
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FieldLabel("Ends At")
                                HourStepper(
                                    hour = quietEnd,
                                    onChange = { quietEnd = it; settings.quietHoursEnd = it }
                                )
                            }
                        }
                        Text(
                            "Scheduled backups will only run between ${formatHour(quietStart)} and ${formatHour(quietEnd)}.",
                            color = TextMuted, fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // ── Battery ────────────────────────────────────────────
        SettingsSection(label = "BATTERY", icon = Icons.Default.BatteryFull) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Disable battery optimisation so the backup engine can run reliably in the background.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
                Button(
                    onClick = {
                        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"))
                        context.startActivity(i)
                        isOptimized = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    },
                    shape  = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOptimized) Warning else SuccessDim,
                        contentColor   = if (isOptimized) Color.Black else Success
                    ),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled  = isOptimized,
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(
                        if (isOptimized) Icons.Default.BatteryAlert else Icons.Default.BatteryFull,
                        null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isOptimized) "Disable Battery Optimisation" else "Optimisation Already Disabled",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Auto DB backup ─────────────────────────────────────
        SettingsSection(label = "DATABASE BACKUP", icon = Icons.Default.Storage) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                PremiumToggle(
                    label       = "Auto DB Backup",
                    description = "Periodically push an encrypted copy of your history to Telegram.",
                    checked     = dbBackup,
                    onChecked   = { dbBackup = it; settings.dbBackupEnabled = it; viewModel.scheduleDbBackup() }
                )
                if (dbBackup) {
                    GradientDivider(Modifier.padding(horizontal = 14.dp))
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FieldLabel("Backup Interval")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6, 12, 24, 48).forEach { h ->
                                val selected = dbInterval == h
                                FilterChip(
                                    selected = selected,
                                    onClick  = { dbInterval = h; settings.dbBackupIntervalHours = h; viewModel.scheduleDbBackup() },
                                    label    = { Text("${h}h", fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary,
                                        selectedLabelColor     = Color.White,
                                        containerColor         = SurfaceAlt,
                                        labelColor             = TextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        selectedBorderColor = Primary,
                                        borderColor = Border
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }
                // Always visible — applies to every DB push (auto-scheduled,
                // manual "Backup DB" tap, and the push that runs right after
                // a sync uploads new media), not just the auto-scheduled one,
                // so it isn't gated behind "Auto DB Backup" being on.
                GradientDivider(Modifier.padding(horizontal = 14.dp))
                PremiumToggle(
                    label       = "Keep Only Latest Backup",
                    description = "Delete the previous backup message (and its \"pinned a message\" notice, best-effort) each time a new one is pushed — applies to every backup push (auto, manual, or after new uploads), so the group doesn't fill up with old snapshots.",
                    checked     = dbAutoDeleteOld,
                    onChecked   = { dbAutoDeleteOld = it; settings.dbBackupAutoDeleteOld = it }
                )
            }
        }

        // ── Migrate to a new bot (after the old one is revoked/lost) ────
        MigrationSection(viewModel)

        // ── Local backup / export ──────────────────────────────
        SettingsSection(label = "LOCAL BACKUP", icon = Icons.Default.SaveAlt) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    uri?.let {
                        viewModel.exportBackup { data ->
                            if (data != null) context.contentResolver.openOutputStream(it)?.use { s -> s.write(data.toByteArray()) }
                        }
                    }
                }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        context.contentResolver.openInputStream(it)?.use { s ->
                            viewModel.importBackup(s.bufferedReader().readText()) {}
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick  = { exportLauncher.launch("TGxBackup_${System.currentTimeMillis()}.json") },
                        shape    = RoundedCornerShape(13.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick  = { importLauncher.launch("application/json") },
                        shape    = RoundedCornerShape(13.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        // ── How to set up ──────────────────────────────────────
        SettingsSection(label = "HOW TO SET UP", icon = Icons.Default.HelpOutline) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SetupStep(
                    number = "1",
                    title  = "Create a Telegram Bot",
                    desc   = "Open @BotFather, send /newbot and follow the steps. Copy the API token."
                )
                GradientDivider()
                SetupStep(
                    number = "2",
                    title  = "Create a Forum Group",
                    desc   = "Create a Telegram group, enable Topics in settings, and add your bot as admin with 'Manage Topics' permission."
                )
                GradientDivider()
                SetupStep(
                    number = "3",
                    title  = "Get the Chat ID",
                    desc   = "Forward any group message to @GetIDsBot to retrieve the chat ID (starts with -100…)."
                )
                GradientDivider()
                SetupStep(
                    number = "4",
                    title  = "Paste & Test",
                    desc   = "Enter your bot token and chat ID above, then tap Test & Save."
                )
            }
        }

        // ── About ──────────────────────────────────────────────
        SettingsSection(label = "ABOUT", icon = Icons.Default.Info) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Version", color = TextSecondary, fontSize = 14.sp)
                    // FIX: this used to be a hardcoded "3.0.0" string that never
                    // matched the actual installed app version (e.g. it still
                    // said 3.0.0 after the app was built/installed as 3.1.0).
                    // Reading it from PackageManager keeps it truthful without
                    // needing to remember to bump a second copy of the version
                    // number by hand on every release.
                    val appVersion = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
                        } catch (e: Exception) { "—" }
                    }
                    StatusBadge(appVersion, Primary)
                }
                GradientDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/F1905006")))
                    }
                ) {
                    Icon(painterResource(R.drawable.ic_telegram), null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Developer", color = TextSecondary, fontSize = 12.sp)
                        Text("@F1905006", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
                    }
                    Icon(Icons.Default.OpenInNew, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
                GradientDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/paradox1905006/TG-media-Backup-.git")))
                    }
                ) {
                    Icon(painterResource(R.drawable.ic_github), null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Source Code", color = TextSecondary, fontSize = 12.sp)
                        Text("GitHub Repository", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
                    }
                    Icon(Icons.Default.OpenInNew, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ── Danger zone ────────────────────────────────────────
        var showResetDialog by remember { mutableStateOf(false) }
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Database?", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text  = { Text("This permanently removes all upload history from your device. This cannot be undone.", color = TextSecondary, lineHeight = 20.sp) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.resetDatabase(); showResetDialog = false },
                        colors  = ButtonDefaults.buttonColors(containerColor = Destructive),
                        shape   = RoundedCornerShape(12.dp)
                    ) { Text("Reset", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = TextSecondary) }
                },
                containerColor = SurfaceElevated,
                shape = RoundedCornerShape(20.dp)
            )
        }

        OutlinedButton(
            onClick  = { showResetDialog = true },
            shape    = RoundedCornerShape(14.dp),
            border   = androidx.compose.foundation.BorderStroke(1.dp, Destructive.copy(alpha = 0.4f)),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = Destructive),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset Upload Database", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Migrate to a new bot ─────────────────────────────────────────────────
// Shown when the old bot token gets revoked/lost. Recovers everything from
// the group's pinned DB backup (or a manually imported copy) and re-links
// every file to a new bot — no old bot token needed. See MigrationWorker.

@Composable
private fun MigrationSection(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings = viewModel.settings
    val migrationState by viewModel.migrationState.collectAsStateWithLifecycle()

    var expanded      by remember { mutableStateOf(false) }
    var newBotToken   by remember { mutableStateOf("") }
    var migrateChatId by remember { mutableStateOf(settings.chatId) }
    var showNewToken  by remember { mutableStateOf(false) }
    var manualFilePath by remember { mutableStateOf<String?>(null) }
    var manualFileName by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val path = viewModel.importManualBackupFile(uri)
            if (path != null) {
                manualFilePath = path
                manualFileName = uri.lastPathSegment ?: "backup.json"
            }
        }
    }

    val isRunning = migrationState?.running == true

    SettingsSection(label = "MIGRATE TO NEW BOT", icon = Icons.Default.SwapHoriz) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            PremiumToggle(
                label       = "Old Bot Revoked or Lost?",
                description = "Recover your full backup history under a brand-new bot — no old bot token needed.",
                checked     = expanded,
                onChecked   = { expanded = it }
            )
            if (expanded) {
                GradientDivider(Modifier.padding(horizontal = 14.dp))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Add the new bot as admin (with delete-messages permission) to the SAME group, then enter its token below. " +
                            "The app will find your old DB backup pinned in the group, re-link every file to the new bot, and push a fresh backup — the old bot's token is never needed.",
                        fontSize   = 12.sp,
                        lineHeight = 17.sp,
                        color      = TextSecondary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("New Bot Token")
                        OutlinedTextField(
                            value              = newBotToken,
                            onValueChange      = { newBotToken = it },
                            modifier           = Modifier.fillMaxWidth(),
                            placeholder        = { Text("1234567890:ABCDef…", color = TextMuted, fontSize = 13.sp) },
                            visualTransformation = if (showNewToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon       = {
                                TextButton(onClick = { showNewToken = !showNewToken }) {
                                    Text(if (showNewToken) "Hide" else "Show", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                            keyboardOptions    = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine         = true,
                            enabled            = !isRunning,
                            colors             = credentialFieldColors(),
                            shape              = RoundedCornerShape(12.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Group / Channel Chat ID (same old group)")
                        OutlinedTextField(
                            value           = migrateChatId,
                            onValueChange   = { migrateChatId = it },
                            modifier        = Modifier.fillMaxWidth(),
                            placeholder     = { Text("-100 1234567890", color = TextMuted, fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine      = true,
                            enabled         = !isRunning,
                            colors          = credentialFieldColors(),
                            shape           = RoundedCornerShape(12.dp)
                        )
                    }

                    GradientDivider(Modifier)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Can't find the old backup automatically? Import it manually")
                        OutlinedButton(
                            onClick  = { filePicker.launch("application/json") },
                            enabled  = !isRunning,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border   = androidx.compose.foundation.BorderStroke(1.dp, Border)
                        ) {
                            Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(manualFileName ?: "Choose backup .json file", fontSize = 13.sp)
                        }
                    }

                    migrationState?.let { state ->
                        if (!state.running) {
                            val message = state.error
                                ?: "Migration complete — ${state.migrated} file(s) migrated, ${state.skipped} skipped."
                            val isError = state.error != null
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (isError) DestructiveDim else SuccessDim)
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(
                                        if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                        null,
                                        tint = if (isError) Destructive else Success,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(message, color = if (isError) Destructive else Success, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }

                    Button(
                        onClick  = { viewModel.startMigration(newBotToken.trim(), migrateChatId.trim(), manualFilePath) },
                        enabled  = !isRunning && newBotToken.isNotBlank() && migrateChatId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(13.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor          = Primary,
                            contentColor            = Color.White,
                            disabledContainerColor  = SurfaceElevated,
                            disabledContentColor    = TextMuted
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Migrating…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start Migration", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── Settings section card ───────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    label: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(14.dp))
            Text(
                label,
                fontSize  = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                color = TextMuted
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(18.dp))
        ) {
            content()
        }
    }
}

// ── Toggle row ──────────────────────────────────────────────────────────────

@Composable
private fun PremiumToggle(
    label: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = Primary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceElevated
            )
        )
    }
}

// ── Setup step ─────────────────────────────────────────────────────────────

@Composable
private fun SetupStep(number: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PrimaryDim),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Primary, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

// ── Private helpers ────────────────────────────────────────────────────────

@Composable
private fun StoragePane(label: String, encrypted: Int, plain: Int, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(SurfaceAlt)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(encrypted.toString(), color = Success, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Encrypted", color = TextMuted, fontSize = 8.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(plain.toString(), color = Warning, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Plain", color = TextMuted, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, letterSpacing = 0.3.sp)
}

@Composable
private fun credentialFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Primary,
    unfocusedBorderColor    = Border,
    focusedTextColor        = TextPrimary,
    unfocusedTextColor      = TextPrimary,
    cursorColor             = Primary,
    focusedContainerColor   = SurfaceAlt,
    unfocusedContainerColor = SurfaceAlt,
    focusedLabelColor       = Primary
)

// ── Cloud restore dialog (also used from SettingsScreen) ──────────────────

@Composable
fun CloudRestoreDialog(
    folderCounts: Map<String, Int>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val folders         = remember(folderCounts) { folderCounts.keys.sorted() }
    val selectedFolders = remember { mutableStateOf(folders.toSet()) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(26.dp))
                .background(SurfaceElevated)
                .border(1.dp, Border, RoundedCornerShape(26.dp))
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(PrimaryDim, Color.Transparent)))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Select Folders to Restore", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
                    Text("${folders.size} folders found in cloud backup", fontSize = 12.sp, color = TextSecondary)
                }
                GradientDivider()

                // Select all
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedFolders.value = if (selectedFolders.value.size == folders.size) emptySet() else folders.toSet()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (selectedFolders.value.size == folders.size) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            null, tint = if (selectedFolders.value.size == folders.size) Primary else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Select All", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Text("${selectedFolders.value.size}/${folders.size}", color = TextMuted, fontSize = 12.sp)
                }
                GradientDivider()

                // Folder list
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(folders) { folder ->
                        val selected  = folder in selectedFolders.value
                        val count     = folderCounts[folder] ?: 0
                        val name      = folder.ifBlank { "Uncategorised" }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFolders.value = if (selected) selectedFolders.value - folder else selectedFolders.value + folder
                                }
                                .background(if (selected) PrimaryDim else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                null, tint = if (selected) Primary else TextMuted, modifier = Modifier.size(20.dp))
                            Icon(Icons.Default.Folder, null, tint = if (selected) Primary else TextMuted, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp, maxLines = 1)
                                Text("$count files", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                GradientDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss, shape = RoundedCornerShape(13.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Cancel", fontWeight = FontWeight.SemiBold) }

                    Button(
                        onClick = { onConfirm(selectedFolders.value) },
                        enabled = selectedFolders.value.isNotEmpty(),
                        shape   = RoundedCornerShape(13.dp),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Primary, contentColor = Color.White,
                            disabledContainerColor = SurfaceAlt, disabledContentColor = TextMuted
                        ),
                        modifier = Modifier.weight(2f).height(48.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        val total = selectedFolders.value.sumOf { folderCounts[it] ?: 0 }
                        Text("Download · $total files", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Quiet-hours hour stepper ─────────────────────────────────────────────

private fun formatHour(hour: Int): String {
    val h = hour % 24
    val period = if (h < 12) "AM" else "PM"
    val display = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$display:00 $period"
}

@Composable
private fun HourStepper(hour: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceAlt)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onChange((hour + 23) % 24) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Remove, null, tint = Primary, modifier = Modifier.size(16.dp))
        }
        Text(
            formatHour(hour),
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        IconButton(onClick = { onChange((hour + 1) % 24) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, null, tint = Primary, modifier = Modifier.size(16.dp))
        }
    }
}
