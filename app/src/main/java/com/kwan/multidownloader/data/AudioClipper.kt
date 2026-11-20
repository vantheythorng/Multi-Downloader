package com.kwan.multidownloader.data

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cuts a stretch of a download's audio into an AAC .m4a in the app's cache, for sharing without
 * saving: nothing goes to shared storage. Android has no MP3 encoder, so MP3 sources are re-encoded.
 */
@OptIn(UnstableApi::class)
class AudioClipper(private val context: Context) {

    private val dir: File get() = File(context.cacheDir, DIR)

    /**
     * Writes [startMs]..[endMs] of [source] to "clips/[name]" and returns the file. [hasVideo] drops
     * the picture from MP4s. Only the newest clip is kept. Cancelling stops the export and deletes it.
     */
    suspend fun clip(
        source: Uri,
        hasVideo: Boolean,
        startMs: Long,
        endMs: Long,
        name: String,
        onProgress: (Float) -> Unit,
    ): File {
        val out = withContext(Dispatchers.IO) {
            dir.deleteRecursively()
            dir.mkdirs()
            File(dir, name)
        }
        // Transformer must be used from one thread with a Looper: the main thread here.
        return withContext(Dispatchers.Main) {
            val item = MediaItem.Builder()
                .setUri(source)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build(),
                )
                .build()
            val edited = EditedMediaItem.Builder(item).setRemoveVideo(hasVideo).build()
            val done = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                // Streamable output reserves ~400 KB of empty space up front; clips are sent whole.
                .setMuxerFactory(InAppMp4Muxer.Factory().setAttemptStreamableOutputEnabled(false))
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        done.completeExceptionally(exportException)
                    }
                })
                .build()
            transformer.start(edited, out.path)
            val progress = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    delay(PROGRESS_INTERVAL_MS)
                }
            }
            try {
                done.await()
                out
            } catch (e: Throwable) {
                // Covers cancellation too: stop the export and drop the half-written file.
                if (!done.isCompleted) transformer.cancel()
                withContext(Dispatchers.IO) { out.delete() }
                throw e
            } finally {
                progress.cancel()
            }
        }
    }

    /** Clears clips left by an earlier run; recent ones stay, as an app may still be reading one. */
    fun deleteOldClips() {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    companion object {
        /** Also in res/xml/file_paths.xml, which lets [com.kwan.multidownloader.share.ShareIntents] share them. */
        const val DIR = "clips"
        const val MIME_TYPE = "audio/mp4"
        private const val PROGRESS_INTERVAL_MS = 100L
        private const val KEEP_MS = 60 * 60 * 1000L
    }
}
