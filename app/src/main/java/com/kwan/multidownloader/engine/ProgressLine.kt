package com.kwan.multidownloader.engine

/** Helpers for yt-dlp's `--newline` progress output, e.g. `[download]  12.3% of ~10.00MiB at 2.31MiB/s ETA 00:04`. */
object ProgressLine {
    private val SPEED = Regex("""\bat\s+(\S+/s)""")
    private val POST_PROCESSORS = listOf(
        "[ExtractAudio]", "[Merger]", "[EmbedThumbnail]", "[Metadata]",
        "[ThumbnailsConvertor]", "[VideoConvertor]", "[FixupM3u8]", "[FixupM4a]",
    )

    fun speed(line: String): String? =
        SPEED.find(line)?.groupValues?.get(1)?.takeUnless { it.startsWith("Unknown") }

    fun isPostProcessing(line: String): Boolean = POST_PROCESSORS.any { line.startsWith(it) }

    /** yt-dlp's own downloader, or aria2c's summary lines when it is the external downloader. */
    fun isDownloading(line: String): Boolean = line.startsWith("[download]") || line.startsWith("[#")
}
