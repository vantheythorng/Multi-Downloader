package com.kwan.multidownloader.engine

import android.content.Context
import android.util.Log
import com.kwan.multidownloader.data.cookies.CookieStore
import com.kwan.multidownloader.domain.UrlParser
import com.kwan.multidownloader.domain.DownloadRequest
import com.kwan.multidownloader.domain.MediaInfo
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** [DownloadEngine] backed by youtubedl-android (embedded Python + yt-dlp + ffmpeg + aria2c). */
class YtDlpEngine(
    private val context: Context,
    private val cookies: CookieStore,
) : DownloadEngine {
    private val ytdl get() = YoutubeDL.getInstance()
    private val initLock = Mutex()
    private val updateLock = Mutex()
    @Volatile private var initialized = false

    /** The Python runtime takes 1 to 3 s to unpack on first use, so it starts lazily, off the main thread. */
    override suspend fun init() {
        if (initialized) return
        initLock.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                try {
                    ytdl.init(context)
                    FFmpeg.getInstance().init(context)
                    Ffmpeg16kFix.apply(context)
                    Aria2c.getInstance().init(context)
                } catch (e: Exception) {
                    throw EngineException("Couldn't start the download engine", cause = e)
                }
            }
            initialized = true
        }
    }

    override suspend fun fetchInfo(url: String): MediaInfo {
        init()
        return withContext(Dispatchers.IO) {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                cookiesFor(url)?.let { addOption("--cookies", it) }
            }
            val response = try {
                ytdl.execute(request)
            } catch (e: YoutubeDL.CanceledException) {
                throw EngineException("Cancelled", cause = e)
            } catch (e: Exception) {
                Log.w(TAG, "fetchInfo failed for $url: ${e.message}")
                val mapped = YtDlpErrors.map(e.message, UrlParser.detect(url))
                throw EngineException(mapped.message, mapped.kind, e)
            }
            try {
                YtDlpJson.parseInfo(response.out, url)
            } catch (e: Exception) {
                throw EngineException("Couldn't read media info", cause = e)
            }
        }
    }

    override fun download(req: DownloadRequest): Flow<EngineEvent> = callbackFlow {
        // execute() blocks until yt-dlp exits, so it runs in its own job and awaitClose kills the process on cancel.
        val job = launch(Dispatchers.IO) {
            val event = try {
                init()
                val outDir = File(req.outDir).apply { mkdirs() }
                val withCookies = req.copy(cookiesFile = cookiesFor(req.url))
                val request = YoutubeDLRequest(req.url).apply {
                    YtDlpOptions.build(withCookies).forEach { (key, value) ->
                        if (value == null) addOption(key) else addOption(key, value)
                    }
                }
                ensureActive()
                ytdl.execute(request, req.taskId) { pct, eta, line ->
                    trySend(EngineEvent.Progress(pct, eta, line))
                }
                findOutput(outDir, req.type.extension)
                    ?.let { EngineEvent.Done(it) }
                    ?: EngineEvent.Error("Download finished but no file was produced", ErrorKind.OTHER)
            } catch (e: YoutubeDL.CanceledException) {
                null
            } catch (e: EngineException) {
                EngineEvent.Error(e.message.orEmpty(), e.kind)
            } catch (e: Exception) {
                Log.w(TAG, "download ${req.taskId} failed: ${e.message}")
                val mapped = YtDlpErrors.map(e.message, UrlParser.detect(req.url))
                EngineEvent.Error(mapped.message, mapped.kind)
            }
            if (event != null) send(event)
            channel.close()
        }
        awaitClose {
            if (job.isActive) ytdl.destroyProcessById(req.taskId)
        }
    }

    override fun cancel(taskId: String) {
        ytdl.destroyProcessById(taskId)
    }

    override suspend fun updateExtractor(): UpdateResult {
        init()
        // Concurrent callers (worker self-heal, Home, periodic check) share one update.
        return updateLock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    when (ytdl.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)) {
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> UpdateResult.AlreadyUpToDate(version().orEmpty())
                        else -> UpdateResult.Updated(version().orEmpty())
                    }.also { Log.i(TAG, "yt-dlp update: $it") }
                } catch (e: Exception) {
                    Log.w(TAG, "yt-dlp update failed", e)
                    UpdateResult.Failed(e.message ?: "Update failed")
                }
            }
        }
    }

    /** Read from the library's prefs, so it doesn't start Python; null until the first update. */
    override suspend fun version(): String? =
        withContext(Dispatchers.IO) { ytdl.version(context)?.takeIf { it.isNotBlank() } }

    private fun cookiesFor(url: String): String? = cookies.fileFor(UrlParser.detect(url))?.absolutePath

    /** Each task has its own folder, so the largest file with the target extension is the result. */
    private fun findOutput(dir: File, extension: String): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            ?.maxByOrNull { it.length() }

    private companion object {
        const val TAG = "YtDlpEngine"
    }
}
