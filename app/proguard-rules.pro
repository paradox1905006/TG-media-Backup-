# Telegram API responses are parsed with JSONObject — no obfuscation needed.
# Room generates code via KSP — keep entity classes.
-keep class com.dparadox.tgbackup.data.UploadedFile { *; }
-keep class com.dparadox.tgbackup.network.** { *; }
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
