# TG × D.Paradox — Android (Native Kotlin)

**TG × D.Paradox** is a powerful, privacy-focused media backup solution for Android. It transforms a Telegram Group into your own personal, unlimited cloud storage, organizing your photos and videos into structured Topics that mirror your device's folder hierarchy.

---

## ✨ Key Features

- **📂 Folder-to-Topic Organization**: Automatically creates Telegram Forum Topics for every folder on your device. Your backup stays organized exactly like your phone.
- **🔍 File Manager Grade Scanning**: Uses `MANAGE_EXTERNAL_STORAGE` to discover media deep within your storage, including subfolders that standard gallery apps often miss.
- **🛡️ SHA-256 Deduplication**: Every file is hashed before upload. If you move or rename a file, the app recognizes it and skips re-uploading, saving data and time.
- **🔄 Organized Restore**: When downloading media back to your device, the app re-creates the original folder structure based on the Telegram Topic names.
- **☁️ Cloud History Sync**: Encrypts and uploads your upload database to your Telegram group as a pinned message. Switching phones? Just "Restore History" to pick up where you left off.
- **⚡ Background Engine**: Powered by Android WorkManager. Handles network retries, exponential backoff, and respects Wi-Fi/Battery constraints automatically.
- **🔐 Security First**: Telegram credentials are encrypted using **AES-256-GCM** via the Android Keystore System. Your tokens never leave your device.

---

## 🛠️ Technical Stack

- **UI**: Jetpack Compose (Material 3) with a Premium Pitch Black theme.
- **Database**: Room (SQLite) for tracking millions of file hashes efficiently.
- **Background**: WorkManager for robust periodic syncing.
- **Network**: OkHttp for multipart Telegram Bot API communication.
- **Security**: Android Jetpack Security (EncryptedSharedPreferences).
- **Language**: 100% Kotlin with Coroutines & Flow.

---

## 🚀 Getting Started

### 1. Requirements
- **Android Studio**: Hedgehog 2023.1.1+
- **Min Android**: 8.0 (API 26)
- **Target Android**: 14 (API 34)
- **Java**: JDK 17+

### 2. Telegram Setup
1. **Create a Bot**: Message [@BotFather](https://t.me/botfather) on Telegram, send `/newbot`, and save the **Bot Token**.
2. **Create a Group**: Create a new Telegram Group (or use an existing one). 
3. **Enable Topics**: Go to Group Settings → **Edit** → Enable **Topics** (Forum mode). This is required for folder organization.
4. **Add Bot**: Add your bot as an **Administrator** with permission to "Post Messages" and "Manage Topics".
5. **Get Chat ID**: Forward any message from your group to [@userinfobot](https://t.me/userinfobot) to get the Chat ID (starts with `-100`).

### 3. Build & Run
1. Clone this repository.
2. Open in Android Studio and let Gradle Sync finish.
3. Build and install on your device.
4. Enter your Bot Token and Chat ID in the **Settings** tab.
5. Grant **All Files Access** when prompted to allow the app to scan your folders.

---

## 📋 Permissions Explained

| Permission | Why? |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Required to scan all folders and subfolders for media, acting as a file manager. |
| `INTERNET` | To communicate with the Telegram Bot API. |
| `FOREGROUND_SERVICE` | Ensures the sync doesn't get killed by Android during long uploads. |
| `RECEIVE_BOOT_COMPLETED` | Automatically restarts the background sync schedule after you reboot your phone. |
| `POST_NOTIFICATIONS` | Shows real-time upload progress and completion status. |

---

## 📁 Project Structure

```text
app/src/main/java/com/dparadox/tgbackup/
├── data/
│   ├── AppDatabase.kt      — Room DB for upload tracking
│   ├── FileSyncEngine.kt   — Recursive storage scanning & hashing
│   └── SettingsManager.kt  — Encrypted credential storage
├── network/
│   └── TelegramApi.kt      — Topic creation & Media upload logic
├── worker/
│   ├── SyncWorker.kt       — The background backup engine
│   └── DownloadWorker.kt   — The organized restoration engine
└── ui/
    ├── MainViewModel.kt    — Reactive state management
    └── screens/
        ├── DashboardScreen.kt — Real-time stats & controls
        ├── FoldersScreen.kt   — Manual folder selection UI
        └── SettingsScreen.kt  — Configuration & Toggles
```

---

## ⚠️ Troubleshooting

- **Folders not showing?**: Ensure you have granted "All Files Access" in the Status tab. 
- **Topic creation failed?**: Make sure your Telegram Group has **Topics/Forum** mode enabled and the bot is an **Admin**.
- **Background sync delayed?**: Android may delay WorkManager tasks if Battery Optimization is on. Tap "Disable Battery Optimization" in Settings for instant background runs.

---

## ⚖️ License & Privacy
This app is provided "as-is" for personal backup purposes. We do not collect any data. All credentials and media paths stay on your device or in your private Telegram group.
