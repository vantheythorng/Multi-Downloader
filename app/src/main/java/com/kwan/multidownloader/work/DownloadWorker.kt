package com.kwan.multidownloader.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.kwan.multidownloader.data.DownloadPaths
import com.kwan.multidownloader.data.MediaStoreSaver
import com.kwan.multidownloader.data.db.DownloadDao
import com.kwan.multidownloader.data.db.DownloadEntity
import com.kwan.multidownloader.data.settings.FileNameStyle
import com.kwan.multidownloader.data.settings.SettingsRepository
import com.kwan.multidownloader.domain.DownloadRequest
import com.kwan.multidownloader.domain.FileNames
import com.kwan.multidownloader.domain.Status
import com.kwan.multidownloader.engine.DownloadEngine
import com.kwan.multidownloader.engine.EngineEvent
import com.kwan.multidownloader.engine.ErrorKind
import com.kwan.multidownloader.engine.ExtractorUpdater
import com.kwan.multidownloader.engine.ProgressLine
import com.kwan.multidownloader.engine.UpdateResult
import com.kwan.multidownloader.engine.YtDlpErrors
import com.kwan.multidownloader.engine.YtDlpJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/** Runs one download as foreground `dataSync` work; the only component that touches the Python engine. */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val dao: DownloadDao,
    private val engine: DownloadEngine,
    private val saver: MediaStoreSaver,
    private val settingsRepository: SettingsRepository,
    private val notifications: DownloadNotifications,
    private val gate: ConcurrencyGate,
    private val paths: DownloadPaths,
    private val updater: ExtractorUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val task = dao.get(taskId) ?: return Result.failure()
        if (!task.status.isActive || task.status == Status.PAUSED) return Result.success()

        notifications.cancelResult(taskId)
        promoteToForeground(task, Status.QUEUED, task.progress)
        return try {
            gate.withPermit {
                notifications.refreshSummary(runningCount())
                runDownload(task)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                when (dao.get(taskId)?.status) {
                    // Paused keeps the partial file for --continue; cancelled throws it away.
                    Status.PAUSED -> Unit
                    Status.CANCELLED, null -> paths.deleteTemp(taskId)
                    // Stopped by the system (constraints lost, quota): WorkManager reschedules it.
                    else -> dao.updateProgress(taskId, Status.QUEUED, task.progress, null, null)
                }
            }
            throw e
        } finally {
            withContext(NonCancellable) {
                notifications.cancelProgress(taskId)
                notifications.refreshSummary(runningCount())
            }
        }
    }

    private suspend fun runDownload(queued: DownloadEntity): Result {
        report(queued, Status.FETCHING, queued.progress, null, null)
        var task = queued
        val settings = settingsRepository.current()
        val request = DownloadRequest(
            taskId = task.id,
            url = task.sourceUrl,
            type = task.type,
            quality = task.quality,
            outDir = paths.tempDir(task.id).absolutePath,
            embedThumbnail = settings.embedThumbnail,
            embedMetadata = settings.embedMetadata,
            useAria2c = settings.useAria2c,
            // Quick downloads are queued without a title; take it from this run instead of a second extraction.
            writeInfoJson = task.title == null,
        )

        var attempt = downloadOnce(task, request)
        task = attempt.task
        var latestExtractor = false
        // Stale extractor: fetch the latest yt-dlp and try once more before reporting the failure.
        if (attempt.outcome.let { it is EngineEvent.Error && it.kind == ErrorKind.EXTRACTOR_OUTDATED }) {
            report(task, Status.FETCHING, task.progress, null, null)
            val update = updater.updateAfterFailure()
            latestExtractor = update is UpdateResult.Updated || update is UpdateResult.AlreadyUpToDate
            if (update is UpdateResult.Updated) {
                attempt = downloadOnce(task, request)
                task = attempt.task
            }
        }
        val outcome = attempt.outcome

        return when (val result = outcome) {
            is EngineEvent.Done -> save(task, result)
            is EngineEvent.Error ->
                if (result.kind == ErrorKind.EXTRACTOR_OUTDATED && latestExtractor) {
                    fail(task, ErrorKind.SITE_CHANGED, YtDlpErrors.SITE_CHANGED_MESSAGE)
                } else {
                    fail(task, result.kind, result.message)
                }
            // The process was killed (pause/cancel) and the coroutine is about to see the cancellation.
            else -> fail(task, ErrorKind.OTHER, "Download stopped")
        }
    }

    /** [outcome] is Done, Error, or null if the process was killed; [task] carries any title picked up on the way. */
    private class Attempt(val outcome: EngineEvent?, val task: DownloadEntity)

    /** Runs yt-dlp once, mirroring progress into Room and the notification. */
    private suspend fun downloadOnce(queued: DownloadEntity, request: DownloadRequest): Attempt {
        var task = queued
        var lastStatus = Status.FETCHING
        var lastUpdate = 0L
        var outcome: EngineEvent? = null
        engine.download(request).collect { event ->
            when (event) {
                is EngineEvent.Progress -> {
                    if (task.title == null) task = readMetadata(task, request.outDir) ?: task
                    // Extractor chatter ("[youtube] Extracting URL") keeps the previous status.
                    val status = when {
                        ProgressLine.isPostProcessing(event.line) -> Status.CONVERTING
                        ProgressLine.isDownloading(event.line) -> Status.DOWNLOADING
                        else -> lastStatus
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (status != lastStatus || now - lastUpdate >= UPDATE_INTERVAL_MS) {
                        lastStatus = status
                        lastUpdate = now
                        report(
                            task,
                            status,
                            event.pct.coerceIn(0f, 100f),
                            ProgressLine.speed(event.line),
                            event.etaSec.takeIf { it > 0 },
                        )
                    }
                }
                is EngineEvent.Done, is EngineEvent.Error -> outcome = event
            }
        }
        if (task.title == null) task = readMetadata(task, request.outDir) ?: task
        return Attempt(outcome, task)
    }

    /**
     * Picks up the `.info.json` yt-dlp writes before downloading (see [DownloadRequest.writeInfoJson]) and stores
     * title, uploader, thumbnail and duration. Returns null until the file exists and parses completely.
     */
    private suspend fun readMetadata(task: DownloadEntity, outDir: String): DownloadEntity? {
        val info = withContext(Dispatchers.IO) {
            val file = File(outDir).listFiles()?.firstOrNull { it.name.endsWith(".info.json") } ?: return@withContext null
            runCatching { YtDlpJson.parseInfo(file.readText(), task.sourceUrl) }.getOrNull()
        } ?: return null
        dao.updateMetadata(task.id, info.title, info.uploader, info.thumbnailUrl, info.durationSec)
        return task.copy(
            title = info.title,
            uploader = info.uploader,
            thumbnailUrl = info.thumbnailUrl,
            durationSec = info.durationSec,
        )
    }

    private suspend fun save(task: DownloadEntity, done: EngineEvent.Done): Result {
        report(task, Status.CONVERTING, 100f, null, null)
        return try {
            val settings = settingsRepository.current()
            val name = when (settings.fileNameStyle) {
                FileNameStyle.TITLE -> FileNames.forTitle(task.title, done.file.nameWithoutExtension, done.file.extension)
                // yt-dlp already names the temp file "<id>.<ext>".
                FileNameStyle.ID -> done.file.name
            }
            val saved = saver.save(done.file, name, task.type, settings.saveLocation)
            paths.deleteTemp(task.id)
            dao.markDone(task.id, saved.uri.toString(), saved.mimeType, saved.sizeBytes, System.currentTimeMillis())
            notifications.showCompleted(task, saved)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Saving ${task.id} failed", e)
            fail(task, ErrorKind.SAVE_FAILED, "Couldn't save the file")
        }
    }

    /** [message] is the English fallback kept for [ErrorKind.OTHER]; the UI words the other kinds itself. */
    private suspend fun fail(task: DownloadEntity, kind: ErrorKind, message: String): Result {
        if (kind.retryable && runAttemptCount < MAX_ATTEMPTS - 1) {
            dao.updateProgress(task.id, Status.QUEUED, task.progress, null, null)
            return Result.retry()
        }
        val current = dao.get(task.id)?.status
        if (current == Status.PAUSED || current == Status.CANCELLED) return Result.success()
        dao.markFailed(task.id, message, kind)
        notifications.showError(task, kind, message)
        return Result.failure()
    }

    private suspend fun report(task: DownloadEntity, status: Status, pct: Float, speed: String?, etaSec: Long?) {
        dao.updateProgress(task.id, status, pct, speed, etaSec)
        notifications.updateProgress(task, status, pct, speed, etaSec)
    }

    /** Best effort: starting a foreground service from the background can be refused on Android 12+. */
    private suspend fun promoteToForeground(task: DownloadEntity, status: Status, pct: Float) {
        try {
            setForeground(
                ForegroundInfo(
                    notifications.progressId(task.id),
                    notifications.progress(task, status, pct, null, null),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Couldn't start foreground for ${task.id}", e)
        }
    }

    private suspend fun runningCount(): Int =
        dao.countByStatuses(listOf(Status.FETCHING, Status.DOWNLOADING, Status.CONVERTING))

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val TAG = "DownloadWorker"
        private const val UPDATE_INTERVAL_MS = 500L
        private const val MAX_ATTEMPTS = 3
    }
}
