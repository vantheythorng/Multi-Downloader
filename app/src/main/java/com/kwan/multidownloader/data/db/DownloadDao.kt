package com.kwan.multidownloader.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kwan.multidownloader.domain.Status
import com.kwan.multidownloader.engine.ErrorKind
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY createdAt DESC")
    fun observeByStatuses(statuses: Collection<Status>): Flow<List<DownloadEntity>>

    @Query("SELECT id FROM downloads WHERE status IN (:statuses)")
    suspend fun idsByStatuses(statuses: Collection<Status>): List<String>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadEntity?

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, error = NULL, errorKind = NULL WHERE id = :id")
    suspend fun updateStatus(id: String, status: Status)

    @Query(
        "UPDATE downloads SET title = :title, uploader = :uploader, thumbnailUrl = :thumbnailUrl, durationSec = :durationSec " +
            "WHERE id = :id"
    )
    suspend fun updateMetadata(id: String, title: String, uploader: String?, thumbnailUrl: String?, durationSec: Int?)

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN (:statuses)")
    suspend fun countByStatuses(statuses: Collection<Status>): Int

    /** Worker-side update; never overrides a PAUSED/CANCELLED/terminal state set by the user meanwhile. */
    @Query(
        "UPDATE downloads SET status = :status, progress = :progress, speed = :speed, etaSec = :etaSec " +
            "WHERE id = :id AND status IN ('QUEUED', 'FETCHING', 'DOWNLOADING', 'CONVERTING')"
    )
    suspend fun updateProgress(id: String, status: Status, progress: Float, speed: String?, etaSec: Long?)

    @Query(
        "UPDATE downloads SET status = 'DONE', progress = 100, speed = NULL, etaSec = NULL, " +
            "contentUri = :contentUri, mimeType = :mimeType, sizeBytes = :sizeBytes, error = NULL, errorKind = NULL, completedAt = :completedAt " +
            "WHERE id = :id"
    )
    suspend fun markDone(id: String, contentUri: String, mimeType: String, sizeBytes: Long, completedAt: Long)

    @Query("UPDATE downloads SET status = 'FAILED', speed = NULL, etaSec = NULL, error = :error, errorKind = :errorKind WHERE id = :id")
    suspend fun markFailed(id: String, error: String, errorKind: ErrorKind)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)
}
