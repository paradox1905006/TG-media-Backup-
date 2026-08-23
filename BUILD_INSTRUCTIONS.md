# How to Build Your APK

## Prerequisites (install once)

1. **JDK 17+** — https://adoptium.net/  
   (Android Studio bundles it — if Studio is installed you already have it)
2. **Android Studio** — https://developer.android.com/studio  
   (needed to accept Android SDK licenses the first time)

---

## Quickest path — Android Studio GUI

1. Open Android Studio → `File → Open` → select this folder
2. Wait for Gradle sync (first time ~5 min, downloads everything)
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`
4. Click **"locate"** in the popup → your APK is in `app/build/outputs/apk/debug/`

---

## Command-line (Mac/Linux)

```bash
# 1. Accept Android SDK licenses (only needed once)
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses

# 2. Build a debug APK (no signing needed — sideload directly)
./gradlew assembleDebug

# APK output:
#   app/build/outputs/apk/debug/app-debug.apk
```

## Command-line (Windows)

```bat
gradlew.bat assembleDebug
```

---

## Install on your Android phone (no Play Store)

```bash
# Enable "Install from unknown sources" in Android Settings first, then:
adb install app/build/outputs/apk/debug/app-debug.apk
```
Or just copy the APK to your phone and open it in a file manager.

---

## Release / Signed APK (optional — only needed for Play Store)

```bash
# 1. Generate a keystore (do this once, keep it safe!)
keytool -genkeypair -v \
  -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias tg-paradox

# 2. Build signed release APK
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=my-release-key.jks \
  -Pandroid.injected.signing.store.password=YOUR_STORE_PASSWORD \
  -Pandroid.injected.signing.key.alias=tg-paradox \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASSWORD

# Output: app/build/outputs/apk/release/app-release.apk
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME` env var, or create `local.properties` with `sdk.dir=/path/to/Android/sdk` |
| `License not accepted` | Run `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses` |
| `java: command not found` | Install JDK 17+ and add to PATH |
| Gradle sync fails in Studio | `File → Invalidate Caches → Invalidate and Restart` |
