package com.kwan.multidownloader.engine

import com.kwan.multidownloader.domain.MediaFormat
import com.kwan.multidownloader.domain.MediaInfo
import com.kwan.multidownloader.domain.Platform
import com.kwan.multidownloader.domain.UrlParser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class YtDlpInfo(
    val id: String = "",
    val title: String? = null,
    val description: String? = null,
    val uploader: String? = null,
    val channel: String? = null,
    val artist: String? = null,
    val thumbnail: String? = null,
    val duration: Double? = null,
    @SerialName("webpage_url") val webpageUrl: String? = null,
    val formats: List<YtDlpFormat> = emptyList(),
    val entries: List<YtDlpInfo> = emptyList(),
)

@Serializable
internal data class YtDlpFormat(
    @SerialName("format_id") val formatId: String = "",
    val ext: String? = null,
    val height: Int? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val filesize: Double? = null,
    @SerialName("filesize_approx") val filesizeApprox: Double? = null,
    val tbr: Double? = null,
)

object YtDlpJson {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /** Maps `yt-dlp --dump-single-json` output; a multi-video post (e.g. X) uses its first entry. */
    fun parseInfo(raw: String, sourceUrl: String): MediaInfo {
        val root = json.decodeFromString<YtDlpInfo>(raw)
        val info = if (root.formats.isEmpty() && root.entries.isNotEmpty()) root.entries.first() else root
        val platform = UrlParser.detect(sourceUrl)
        val rawUploader = info.artist ?: info.uploader ?: info.channel
        val caption = if (platform == Platform.FACEBOOK) TextCleanup.facebookCaption(rawUploader) else null
        return MediaInfo(
            id = info.id,
            url = sourceUrl,
            platform = platform,
            title = TextCleanup.title(info.title ?: root.title ?: info.id, platform),
            description = TextCleanup.longer(TextCleanup.description(info.description ?: root.description), caption),
            uploader = TextCleanup.uploader(rawUploader),
            thumbnailUrl = info.thumbnail ?: root.thumbnail,
            durationSec = info.duration?.toInt(),
            formats = info.formats.map { f ->
                MediaFormat(
                    formatId = f.formatId,
                    ext = f.ext,
                    height = f.height,
                    vcodec = f.vcodec,
                    acodec = f.acodec,
                    filesize = (f.filesize ?: f.filesizeApprox)?.toLong(),
                    tbrKbps = f.tbr,
                )
            },
        )
    }
}
