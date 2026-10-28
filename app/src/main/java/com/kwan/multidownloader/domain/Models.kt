package com.kwan.multidownloader.domain

enum class Platform(val displayName: String) {
    YOUTUBE("YouTube"),
    YT_MUSIC("YouTube Music"),
    FACEBOOK("Facebook"),
    TIKTOK("TikTok"),
    X("X"),
    OTHER("Other"),
}

enum class MediaType(val extension: String, val mimeType: String) {
    AUDIO_MP3("mp3", "audio/mpeg"),
    VIDEO_MP4("mp4", "video/mp4"),
}

enum class Status {
    QUEUED, FETCHING, DOWNLOADING, CONVERTING, PAUSED, DONE, FAILED, CANCELLED;

    val isActive: Boolean get() = this in ACTIVE

    companion object {
        val ACTIVE = setOf(QUEUED, FETCHING, DOWNLOADING, CONVERTING, PAUSED)
        val COMPLETED = setOf(DONE)
        val FAILED_OR_CANCELLED = setOf(FAILED, CANCELLED)
    }
}

/** Quality options shown in the format sheet; [value] is what gets stored in Room. */
object Quality {
    const val BEST = "best"
    val AUDIO_BITRATES = listOf(128, 192, 320)
    val VIDEO_HEIGHTS = listOf(1080, 720, 480, 360)

    fun audio(kbps: Int) = "${kbps}k"
    fun video(height: Int) = "${height}p"

    fun options(type: MediaType): List<String> = when (type) {
        MediaType.AUDIO_MP3 -> AUDIO_BITRATES.map(::audio)
        MediaType.VIDEO_MP4 -> listOf(BEST) + VIDEO_HEIGHTS.map(::video)
    }

    /** "192k" -> 192, falls back to 192. */
    fun audioKbps(quality: String): Int = quality.removeSuffix("k").toIntOrNull() ?: 192

    /** "720p" -> 720, "best" -> null. */
    fun videoHeight(quality: String): Int? = quality.removeSuffix("p").toIntOrNull()
}

data class MediaFormat(
    val formatId: String,
    val ext: String?,
    val height: Int?,
    val vcodec: String?,
    val acodec: String?,
    val filesize: Long?,
    val tbrKbps: Double?,
) {
    val hasVideo: Boolean get() = vcodec != null && vcodec != "none"
    val hasAudio: Boolean get() = acodec != null && acodec != "none"
}

data class MediaInfo(
    val id: String,
    val url: String,
    val platform: Platform,
    val title: String,
    /** The post's full text (yt-dlp "description"); on TikTok/Facebook/X the title is often a shortened copy. */
    val description: String? = null,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSec: Int?,
    val formats: List<MediaFormat>,
) {
    /** The description, unless it only repeats the title (yt-dlp often uses a short caption for both). */
    val extraDescription: String?
        get() = description?.takeUnless { it.normalizedSpaces() == title.normalizedSpaces() }

    private fun String.normalizedSpaces() = trim().replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("""\s+""")
    }
}

data class DownloadRequest(
    val taskId: String,
    val url: String,
    val type: MediaType,
    val quality: String,
    val outDir: String,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val useAria2c: Boolean = false,
    /** Have yt-dlp write `<name>.info.json` next to the download, so title etc. come from the same run. */
    val writeInfoJson: Boolean = false,
    /** App-private Netscape cookies file for the link's platform, if the user imported one. */
    val cookiesFile: String? = null,
)
