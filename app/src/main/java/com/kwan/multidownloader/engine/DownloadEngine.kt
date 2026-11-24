package com.kwan.multidownloader.engine

import com.kwan.multidownloader.domain.DownloadRequest
import com.kwan.multidownloader.domain.MediaInfo
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Hides yt-dlp so the engine can be swapped (youtubedl-android or Chaquopy) without touching UI code. */
interface DownloadEngine {
    suspend fun init()
    suspend fun fetchInfo(url: String): MediaInfo          // yt-dlp --dump-single-json
    fun download(req: DownloadRequest): Flow<EngineEvent>  // Progress | Done | Error
    fun cancel(taskId: String)
    suspend fun updateExtractor(): UpdateResult            // yt-dlp self-update
    suspend fun version(): String?                         // null = the copy bundled in the APK
}

sealed interface EngineEvent {
    data class Progress(val pct: Float, val etaSec: Long, val line: String) : EngineEvent
    data class Done(val file: File) : EngineEvent
    data class Error(val message: String, val kind: ErrorKind) : EngineEvent {
        val retryable: Boolean get() = kind.retryable
    }
}

sealed interface UpdateResult {
    data class Updated(val version: String) : UpdateResult
    data class AlreadyUpToDate(val version: String) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/** Thrown by [DownloadEngine.fetchInfo]; [message] is already user-facing. */
class EngineException(message: String, val kind: ErrorKind = ErrorKind.OTHER, cause: Throwable? = null) :
    Exception(message, cause) {
    val retryable: Boolean get() = kind.retryable
}
