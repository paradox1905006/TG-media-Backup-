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
     * Scan the entire device for folders containing media using MediaStore.
     * This is much faster than walking the file system.
     */
    suspend fun getAllFoldersOnDevice(): List<String> = withContext(Dispatchers.IO) {
        val results = mutableSetOf<String>()
        
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.INTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.INTERNAL_CONTENT_URI,
        )

        val projection = arrayOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        )

        for (collection in collections) {
            try {
                context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                    val folderCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    }

                    while (cursor.moveToNext()) {
                        val rawFolder = cursor.getString(folderCol) ?: ""
                        var folderName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            rawFolder.trim('/')
                        } else {
                            val path = rawFolder.substringBeforeLast('/')
                            path.substringAfterLast('/')
                        }
                        if (folderName.isBlank()) folderName = "Root"
                        results.add(folderName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying folders from $collection", e)
            }
        }
        
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
        onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> },
        dbLookup: (suspend (size: Long, modified: Long) -> String?)? = null,
        limitToFolder: String? = null
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

        val selection = if (limitToFolder != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            } else {
                "${MediaStore.MediaColumns.DATA} LIKE ?"
            }
        } else null

        val selectionArgs = if (limitToFolder != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                arrayOf("%$limitToFolder%")
            } else {
                arrayOf("%/$limitToFolder/%")
            }
        } else null

        for (collection in collections) {
            try {
                context.contentResolver.query(
                    collection, projection, selection, selectionArgs,
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
                        
                        var folderName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            rawFolder.trim('/')
                        } else {
                            val path = rawFolder.substringBeforeLast('/')
                            path.substringAfterLast('/')
                        }
                        
                        if (folderName.isBlank()) folderName = "Root"

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
        val seenUris = mutableSetOf<Uri>()

        for (row in rows) {
            try {
                if (seenUris.contains(row.uri)) {
                    scanned++
                    continue
                }
                
                val existingHash = dbLookup?.invoke(row.size, row.modified)
                val hash = existingHash ?: hashFile(row.uri, row.size, row.modified)

                if (hash != null) {
                    seenUris.add(row.uri)
                    if (limitToFolder == null || row.folder == limitToFolder) {
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing file: ${row.uri}", e)
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
        onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> },
        dbLookup: (suspend (size: Long, modified: Long) -> String?)? = null
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
            val size = file.length()
            val modified = file.lastModified() / 1000L
            val existingHash = dbLookup?.invoke(size, modified)
            val hash = existingHash ?: hashFile(file.uri, size, modified)
            
            if (hash != null) {
                results += MediaFile(
                    uri          = file.uri,
                    displayName  = file.name ?: "unknown",
                    mimeType     = file.type ?: "application/octet-stream",
                    sizeBytes    = size,
                    dateModified = modified,
                    hash         = hash,
                    folderName   = path
                )
            }
            scanned++
            onProgress(scanned, total)
        }
        results
    }

    suspend fun getThumbnailForFolder(folderName: String): Uri? = withContext(Dispatchers.IO) {
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.INTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.INTERNAL_CONTENT_URI,
        )
        
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?"
        }
        
        val selectionArgs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            arrayOf("%$folderName%")
        } else {
            arrayOf("%/$folderName/%")
        }

        for (collection in collections) {
            context.contentResolver.query(
                collection, projection, selection, selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return@withContext ContentUris.withAppendedId(collection, id)
                }
            }
        }
        null
    }

    private fun hashFile(uri: Uri, size: Long, modifiedSeconds: Long): String? {
        // FIX: The original cache key used `size xor (modifiedSeconds shl 20)`.
        //      For typical Unix timestamps (~1.7 billion), shl 20 overflows Long and
        //      produces a value indistinguishable from other (size, modified) pairs,
        //      creating spurious cache hits that return the wrong hash for a file.
        //      The new key mixes both values without bit-loss.
        val cacheKey = size * 1_000_003L + modifiedSeconds
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
