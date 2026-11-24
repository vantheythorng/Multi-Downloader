package com.kwan.multidownloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SizeEstimatorTest {

    private fun format(id: String, height: Int?, vcodec: String?, acodec: String?, size: Long?, ext: String = "mp4", tbr: Double? = null) =
        MediaFormat(id, ext, height, vcodec, acodec, size, tbr)

    private fun info(duration: Int?, formats: List<MediaFormat>) = MediaInfo(
        id = "x", url = "https://youtu.be/x", platform = Platform.YOUTUBE, title = "t",
        uploader = null, thumbnailUrl = null, durationSec = duration, formats = formats,
    )

    private val formats = listOf(
        format("137", 1080, "avc1", "none", 50_000_000),
        format("136", 720, "avc1", "none", 20_000_000),
        format("135", 480, "avc1", "none", 10_000_000),
        format("140", null, "none", "mp4a", 3_000_000, ext = "m4a"),
        format("251", null, "none", "opus", 2_500_000, ext = "webm"),
    )

    @Test fun mp3_from_duration_and_bitrate() {
        // 100 s at 192 kbps = 2,400,000 bytes
        assertEquals(2_400_000L, SizeEstimator.estimate(info(100, emptyList()), MediaType.AUDIO_MP3, "192k"))
    }

    @Test fun mp3_unknown_without_duration() {
        assertNull(SizeEstimator.estimate(info(null, emptyList()), MediaType.AUDIO_MP3, "192k"))
    }

    @Test fun mp4_720_is_video_plus_m4a_audio() {
        assertEquals(23_000_000L, SizeEstimator.estimate(info(100, formats), MediaType.VIDEO_MP4, "720p"))
    }

    @Test fun mp4_best_takes_highest_resolution() {
        assertEquals(53_000_000L, SizeEstimator.estimate(info(100, formats), MediaType.VIDEO_MP4, "best"))
    }

    @Test fun mp4_falls_back_to_bitrate_when_size_missing() {
        val noSize = listOf(format("22", 720, "avc1", "mp4a", null, tbr = 800.0))
        // 800 kbps * 10 s / 8 = 1,000,000 bytes
        assertEquals(1_000_000L, SizeEstimator.estimate(info(10, noSize), MediaType.VIDEO_MP4, "best"))
    }

    @Test fun mp4_unknown_without_formats() {
        assertNull(SizeEstimator.estimate(info(100, emptyList()), MediaType.VIDEO_MP4, "720p"))
    }
}
