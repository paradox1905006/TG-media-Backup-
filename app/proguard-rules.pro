# ── General ──────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# ── Room Database ───────────────────────────────────────────────────────────
-keep class com.dparadox.tgbackup.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── Models / Data Classes ──────────────────────────────────────────────────
# Keep all data classes to prevent issues with reflection or state preservation
-keepclassmembers class com.dparadox.tgbackup.data.UploadedFile { *; }
-keepclassmembers class com.dparadox.tgbackup.data.SelectedMedia { *; }
-keepclassmembers class com.dparadox.tgbackup.data.FileSyncEngine$MediaFile { *; }

# ── OkHttp ───────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ── Coil ────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── WorkManager ─────────────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class com.dparadox.tgbackup.worker.** { *; }

# ── Kotlin Coroutines ───────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }
