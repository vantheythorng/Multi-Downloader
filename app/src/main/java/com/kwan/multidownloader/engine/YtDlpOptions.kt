package com.kwan.multidownloader.engine

import com.kwan.multidownloader.domain.DownloadRequest
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.Quality

/** Builds the yt-dlp options for one download; a null value means a flag without an argument. */
object YtDlpOptions {
    /** Temp name only; the saved file is renamed after the title or kept as the ID (see FileNameStyle). */
    const val OUTPUT_TEMPLATE = "%(id)s.%(ext)s"
    const val FORMAT_MP4_BEST = "bv*[ext=mp4][vcodec^=avc]+ba[ext=m4a]/bv*+ba/b"

    fun formatForHeight(height: Int) = "bv*[height<=$height][ext=mp4]+ba[ext=m4a]/b[height<=$height]"

    fun build(req: DownloadRequest): List<Pair<String, String?>> = buildList {
        when (req.type) {
            MediaType.AUDIO_MP3 -> {
                add("-x" to null)
                add("--audio-format" to "mp3")
                add("--audio-quality" to "${Quality.audioKbps(req.quality)}K")
                if (req.embedThumbnail) {
                    add("--embed-thumbnail" to null)
                    add("--convert-thumbnails" to "jpg")
                }
            }
            MediaType.VIDEO_MP4 -> {
                val height = Quality.videoHeight(req.quality)
                add("-f" to (height?.let(::formatForHeight) ?: FORMAT_MP4_BEST))
                add("--merge-output-format" to "mp4")
            }
        }
        if (req.embedMetadata) add("--embed-metadata" to null)
        add("--no-playlist" to null)
        add("--no-mtime" to null)
        add("--newline" to null)
        add("--continue" to null)
        if (req.useAria2c) add("--downloader" to "aria2c")
        if (req.writeInfoJson) add("--write-info-json" to null)
        req.cookiesFile?.let { add("--cookies" to it) }
        add("-o" to "${req.outDir}/$OUTPUT_TEMPLATE")
    }

    /** Flattened argv, handy for logging and tests. */
    fun toArgs(options: List<Pair<String, String?>>): List<String> =
        options.flatMap { (key, value) -> listOfNotNull(key, value) }
}
