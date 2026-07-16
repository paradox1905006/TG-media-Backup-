package com.dparadox.tgbackup.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * FileSyncEngine — scans media folders, hashes files, and coordinates uploads.
 */
class FileSyncEngine(private val context: Context) {

    companion object {
        private const val TAG = "FileSyncEngine"
        // Telegram Bot API hard limit: 50 MB
        const val MAX_UPLOAD_BYTES = 50L * 1024L * 1024L
    }

    private val hashCache = mutableMapOf<String, Pair<Long, String>>()

    data class MediaFile(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val dateModified: Long,
        val hash: String,
        val folderName: String = ""
    )

    /**
     * Scan the entire device for folders containing media.
     * Used for the Manual Folder Selection UI.
     */
    suspend fun getAllFoldersOnDevice(): List<String> = withContext(Dispatchers.IO) {
        val storage = android.os.Environment.getExternalStorageDirectory()
        val results = mutableSetOf<String>()
        
        fun walk(dir: File) {
            val files = dir.listFiles() ?: return
            var hasMedia = false
            for (f in files) {
                if (f.isDirectory) {
                    if (f.name.startsWith(".")) continue
                    walk(f)
                } else if (!hasMedia) {
                    val ext = f.extension.lowercase()
                    if (isMediaExtension(ext)) {
                        hasMedia = true
                    }
                }
            }
            if (hasMedia) {
                val relative = dir.absolutePath.removePrefix(storage.absolutePath).trim('/')
                if (relative.isNotEmpty()) results.add(relative)
                else results.add("Root")
            }
        }
        
        walk(storage)
        results.toList().sorted()
    }

    private fun isMediaExtension(ext: String) = ext in listOf("jpg", "jpeg", "png", "webp", "mp4", "mkv", "mov", "gif")

    /**
     * Scan ALL media (photos + videos) from both internal and external storage.
     * Ignore folder filters and backup everything found.
     *
     * @param onProgress Called after each file is hashed.
     */
    suspend fun scanAllMedia(
        watchedFolderUris: List<String> = emptyList(), // Parameter kept for binary compatibility, but ignored
        onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): List<MediaFile> = withContext(Dispatchers.IO) {

        val results = mutableListOf<MediaFile>()
        
        // Scan both INTERNAL and EXTERNAL content for both Images and Videos
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.INTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.INTERNAL_CONTENT_URI,
        )

        data class RawRow(val uri: Uri, val name: String, val mime: String, val size: Long, val modified: Long, val folder: String)
        val rows = mutableListOf<RawRow>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        )

        for (collection in collections) {
            try {
                context.contentResolver.query(
                    collection, projection, null, null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol       = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeCol     = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val folderCol   = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    }

                    while (cursor.moveToNext()) {
                        val id   = cursor.getLong(idCol)
                        val uri  = ContentUris.withAppendedId(collection, id)
                        val rawFolder = cursor.getString(folderCol) ?: ""
                        
                        val folderName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            rawFolder.trim('/')
                        } else {
                            // On older Android, DATA is full path. Hard to get relative without knowing root.
                            // We'll stick to last folder name for legacy or try to find a common root.
                            val path = rawFolder.substringBeforeLast('/')
                            path.substringAfterLast('/')
                        }
                        
                        if (folderName.isBlank()) continue

                        rows += RawRow(
                            uri      = uri,
                            name     = cursor.getString(nameCol) ?: "unknown",
                            mime     = cursor.getString(mimeCol) ?: "application/octet-stream",
                            size     = cursor.getLong(sizeCol),
                            modified = cursor.getLong(modifiedCol),
                            folder   = folderName
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying $collection", e)
            }
        }

        val total = rows.size
        var scanned = 0

        for (row in rows) {
            val hash = hashFile(row.uri, row.size, row.modified)
            if (hash != null) {
                results += MediaFile(
                    uri          = row.uri,
                    displayName  = row.name,
                    mimeType     = row.mime,
                    sizeBytes    = row.size,
                    dateModified = row.modified,
                    hash         = hash,
                    folderName   = row.folder
                )
            }
            scanned++
            onProgress(scanned, total)
        }

        results
    }

    /**
     * Scan a specific folder selected via Storage Access Framework (SAF).
     */
    suspend fun scanSafFolder(
        folderUri: Uri,
        onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MediaFile>()
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        val rootName = root.name ?: "Custom"
        
        val allFilesWithPaths = mutableListOf<Pair<DocumentFile, String>>()
        
        fun collectFiles(dir: DocumentFile, currentPath: String) {
            dir.listFiles().forEach {
                if (it.isDirectory) {
                    val nextPath = if (currentPath.isEmpty()) it.name ?: "" else "$currentPath/${it.name}"
                    collectFiles(it, nextPath)
                } else if (it.isFile) {
                    val mime = it.type ?: ""
                    if (mime.startsWith("image/") || mime.startsWith("video/")) {
                        allFilesWithPaths.add(it to currentPath)
                    }
                }
            }
        }
        
        collectFiles(root, rootName)
        
        val total = allFilesWithPaths.size
        var scanned = 0
        
        for ((file, path) in allFilesWithPaths) {
            val hash = hashFile(file.uri, file.length(), file.lastModified() / 1000L)
            if (hash != null) {
                results += MediaFile(
                    uri          = file.uri,
                    displayName  = file.name ?: "unknown",
                    mimeType     = file.type ?: "application/octet-stream",
                    sizeBytes    = file.length(),
                    dateModified = file.lastModified() / 1000L,
                    hash         = hash,
                    folderName   = path
                )
            }
            scanned++
            onProgress(scanned, total)
        }
        results
    }

    private fun hashFile(uri: Uri, size: Long, modifiedSeconds: Long): String? {
        val cacheKey = size xor (modifiedSeconds shl 20)
        val uriStr = uri.toString()

        hashCache[uriStr]?.let { (cachedKey, cachedHash) ->
            if (cachedKey == cacheKey) return cachedHash
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val digest = sha256(stream)
                hashCache[uriStr] = cacheKey to digest
                digest
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not hash $uri: ${e.message}")
            null
        }
    }

    private fun sha256(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65_536)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
