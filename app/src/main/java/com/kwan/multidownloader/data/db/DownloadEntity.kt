package com.kwan.multidownloader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.Platform
import com.kwan.multidownloader.domain.Status
import com.kwan.multidownloader.engine.ErrorKind

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,          // UUID, also the WorkManager unique name
    val sourceUrl: String,
    val platform: Platform,
    val title: String?,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSec: Int?,
    val type: MediaType,
    val quality: String,                 // "192k", "720p", "best"
    val status: Status,
    val progress: Float = 0f,            // 0..100
    val speed: String? = null,           // e.g. "2.31MiB/s", only while downloading
    val etaSec: Long? = null,
    val sizeBytes: Long? = null,
    val contentUri: String? = null,      // MediaStore URI when DONE
    val mimeType: String? = null,
    val error: String? = null,           // English fallback, shown when errorKind is OTHER
    val errorKind: ErrorKind? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
)
