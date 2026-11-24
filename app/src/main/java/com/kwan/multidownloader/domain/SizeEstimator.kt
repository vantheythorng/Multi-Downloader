package com.kwan.multidownloader.domain

object SizeEstimator {

    /** Estimated output size in bytes, or null when yt-dlp gave us nothing to go on. */
    fun estimate(info: MediaInfo, type: MediaType, quality: String): Long? = when (type) {
        MediaType.AUDIO_MP3 -> info.durationSec?.let { it.toLong() * Quality.audioKbps(quality) * 1000 / 8 }
        MediaType.VIDEO_MP4 -> estimateVideo(info, Quality.videoHeight(quality))
    }

    private fun estimateVideo(info: MediaInfo, maxHeight: Int?): Long? {
        val video = info.formats
            .filter { it.hasVideo && (maxHeight == null || (it.height ?: 0) <= maxHeight) }
            .maxWithOrNull(compareBy<MediaFormat> { it.height ?: 0 }.thenBy { it.ext == "mp4" }.thenBy { it.tbrKbps ?: 0.0 })
            ?: return null
        val videoSize = sizeOf(video, info.durationSec) ?: return null
        if (video.hasAudio) return videoSize
        val audio = info.formats
            .filter { it.hasAudio && !it.hasVideo }
            .maxWithOrNull(compareBy<MediaFormat> { it.ext == "m4a" }.thenBy { it.tbrKbps ?: 0.0 })
        return videoSize + (audio?.let { sizeOf(it, info.durationSec) } ?: 0L)
    }

    private fun sizeOf(format: MediaFormat, durationSec: Int?): Long? =
        format.filesize ?: run {
            val tbr = format.tbrKbps ?: return null
            val duration = durationSec ?: return null
            (tbr * 1000 / 8 * duration).toLong()
        }
}
