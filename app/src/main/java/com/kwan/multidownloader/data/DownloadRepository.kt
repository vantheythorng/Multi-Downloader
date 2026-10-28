package com.kwan.multidownloader.data

import androidx.core.net.toUri
import com.kwan.multidownloader.data.db.DownloadDao
import com.kwan.multidownloader.data.db.DownloadEntity
import com.kwan.multidownloader.domain.MediaInfo
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.ParsedLink
import com.kwan.multidownloader.domain.Status
import com.kwan.multidownloader.work.DownloadNotifications
import com.kwan.multidownloader.work.DownloadScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Single entry point for the UI: every download is a Room row plus a unique WorkManager job. */
class DownloadRepository(
    private val dao: DownloadDao,
    private val scheduler: DownloadScheduler,
    private val saver: MediaStoreSaver,
    private val paths: DownloadPaths,
    private val notifications: DownloadNotifications,
) {
    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun observe(statuses: Set<Status>): Flow<List<DownloadEntity>> = dao.observeByStatuses(statuses)

    suspend fun enqueue(info: MediaInfo, type: MediaType, quality: String): String =
        insertAndSchedule(
            DownloadEntity(
                id = UUID.randomUUID().toString(),
                sourceUrl = info.url,
                platform = info.platform,
                title = info.title,
                uploader = info.uploader,
                thumbnailUrl = info.thumbnailUrl,
                durationSec = info.durationSec,
                type = type,
                quality = quality,
                status = Status.QUEUED,
                createdAt = System.currentTimeMillis(),
            )
        )

    /** Quick download: queue straight from a link; the worker fills in title and thumbnail when it starts. */
    suspend fun enqueue(link: ParsedLink, type: MediaType, quality: String): String =
        insertAndSchedule(
            DownloadEntity(
                id = UUID.randomUUID().toString(),
                sourceUrl = link.url,
                platform = link.platform,
                title = null,
                uploader = null,
                thumbnailUrl = null,
                durationSec = null,
                type = type,
                quality = quality,
                status = Status.QUEUED,
                createdAt = System.currentTimeMillis(),
            )
        )

    private suspend fun insertAndSchedule(entity: DownloadEntity): String {
        dao.upsert(entity)
        scheduler.enqueue(entity.id)
        return entity.id
    }

    /** Stops the job but keeps the partial file so yt-dlp's --continue can pick it up on resume. */
    suspend fun pause(id: String) {
        dao.updateStatus(id, Status.PAUSED)
        scheduler.cancel(id)
    }

    suspend fun resume(id: String) {
        dao.updateStatus(id, Status.QUEUED)
        notifications.cancelResult(id)
        scheduler.enqueue(id)
    }

    suspend fun retry(id: String) = resume(id)

    /**
     * Re-queues downloads that haven't started so they pick up the new Wi-Fi-only constraint.
     * Ones already downloading keep going under the old one.
     */
    suspend fun applyNetworkPolicy() {
        dao.idsByStatuses(listOf(Status.QUEUED)).forEach { scheduler.enqueue(it, replace = true) }
    }

    /** Cancel removes partial files. */
    suspend fun cancel(id: String) {
        dao.updateStatus(id, Status.CANCELLED)
        scheduler.cancel(id)
        paths.deleteTemp(id)
    }

    suspend fun delete(id: String, deleteFile: Boolean = true) {
        val entity = dao.get(id) ?: return
        if (entity.status.isActive) scheduler.cancel(id)
        if (deleteFile) entity.contentUri?.let { saver.delete(it.toUri()) }
        paths.deleteTemp(id)
        notifications.cancelResult(id)
        dao.delete(id)
    }
}
