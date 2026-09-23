# ── General ──────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# ── Suppress Warnings for Missing Annotations ──────────────────────────────
-dontwarn com.google.errorprone.annotations.**

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

# ── SQLCipher ───────────────────────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ── kotlinx.serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.dparadox.tgbackup.**$$serializer { *; }
-keepclassmembers class com.dparadox.tgbackup.** {
    *** Companion;
}
-keepclasseswithmembers class com.dparadox.tgbackup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
