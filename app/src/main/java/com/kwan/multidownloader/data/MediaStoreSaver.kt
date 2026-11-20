package com.kwan.multidownloader.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.kwan.multidownloader.data.settings.SaveLocation
import com.kwan.multidownloader.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** [folder] is the public top-level folder it landed in: Music, Movies or Download. */
data class SavedFile(val uri: Uri, val mimeType: String, val sizeBytes: Long, val folder: String)

enum class RenameResult { RENAMED, UNCHANGED, INVALID, EXISTS, MISSING, NOT_ALLOWED, FAILED }

/** Copies a finished temp file into public storage so it shows up in Gallery and music apps (API 29+). */
class MediaStoreSaver(private val context: Context) {

    suspend fun save(file: File, displayName: String, type: MediaType, location: SaveLocation): SavedFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = collectionFor(type, location)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, type.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${rootFolder(type, location)}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create the file in shared storage")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Couldn't open the file in shared storage")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        SavedFile(uri, type.mimeType, file.length(), rootFolder(type, location))
    }

    /** Removes a file this app created; returns false if it was already gone. */
    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    /** The file's current name in shared storage, or null if it's gone. */
    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    /** Renames a file this app created, keeping its folder. */
    suspend fun rename(uri: Uri, newName: String): RenameResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            val folder = resolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: return@withContext RenameResult.MISSING
            val id = ContentUris.parseId(uri)
            val taken = resolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns._ID} != ?",
                arrayOf(folder, newName, id.toString()),
                null,
            )?.use { it.count > 0 } ?: false
            if (taken) return@withContext RenameResult.EXISTS
            val rows = resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }, null, null)
            if (rows > 0) RenameResult.RENAMED else RenameResult.MISSING
        } catch (e: SecurityException) {
            // No longer ours, e.g. after a reinstall.
            RenameResult.NOT_ALLOWED
        } catch (e: Exception) {
            Log.w(TAG, "Renaming $uri failed", e)
            RenameResult.FAILED
        }
    }

    private fun collectionFor(type: MediaType, location: SaveLocation): Uri = when {
        location == SaveLocation.DOWNLOADS -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        type == MediaType.AUDIO_MP3 -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    private fun rootFolder(type: MediaType, location: SaveLocation): String = when {
        location == SaveLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
        type == MediaType.AUDIO_MP3 -> Environment.DIRECTORY_MUSIC
        else -> Environment.DIRECTORY_MOVIES
    }

    companion object {
        const val FOLDER = "MultiDownloader"
        private const val TAG = "MediaStoreSaver"
    }
}
