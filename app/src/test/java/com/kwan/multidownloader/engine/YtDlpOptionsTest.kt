package com.kwan.multidownloader.engine

import com.kwan.multidownloader.domain.DownloadRequest
import com.kwan.multidownloader.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpOptionsTest {

    private fun request(type: MediaType, quality: String, aria2c: Boolean = false, embedThumbnail: Boolean = true) =
        DownloadRequest(
            taskId = "t1",
            url = "https://youtu.be/x",
            type = type,
            quality = quality,
            outDir = "/cache/downloads/t1",
            embedThumbnail = embedThumbnail,
            useAria2c = aria2c,
        )

    private fun args(req: DownloadRequest) = YtDlpOptions.toArgs(YtDlpOptions.build(req))

    private fun List<String>.valueOf(flag: String): String? = indexOf(flag).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

    @Test fun mp3_uses_requested_bitrate_and_embeds_art_and_tags() {
        listOf("128k" to "128K", "192k" to "192K", "320k" to "320K").forEach { (quality, expected) ->
            val args = args(request(MediaType.AUDIO_MP3, quality))
            assertTrue("-x" in args)
            assertEquals("mp3", args.valueOf("--audio-format"))
            assertEquals(expected, args.valueOf("--audio-quality"))
            assertTrue("--embed-thumbnail" in args)
            assertEquals("jpg", args.valueOf("--convert-thumbnails"))
            assertTrue("--embed-metadata" in args)
            assertFalse("-f" in args)
        }
    }

    @Test fun mp3_without_thumbnail_skips_thumbnail_flags() {
        val args = args(request(MediaType.AUDIO_MP3, "192k", embedThumbnail = false))
        assertFalse("--embed-thumbnail" in args)
        assertFalse("--convert-thumbnails" in args)
    }

    @Test fun mp4_best_prefers_h264_and_merges_to_mp4() {
        val args = args(request(MediaType.VIDEO_MP4, "best"))
        assertEquals("bv*[ext=mp4][vcodec^=avc]+ba[ext=m4a]/bv*+ba/b", args.valueOf("-f"))
        assertEquals("mp4", args.valueOf("--merge-output-format"))
        assertFalse("-x" in args)
    }

    @Test fun mp4_720p_caps_height() {
        val args = args(request(MediaType.VIDEO_MP4, "720p"))
        assertEquals("bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720]", args.valueOf("-f"))
        assertEquals("mp4", args.valueOf("--merge-output-format"))
    }

    @Test fun common_options_and_output_template() {
        val args = args(request(MediaType.VIDEO_MP4, "480p"))
        listOf("--no-playlist", "--no-mtime", "--newline", "--continue").forEach { assertTrue(it in args) }
        assertEquals("/cache/downloads/t1/%(id)s.%(ext)s", args.valueOf("-o"))
        assertFalse("--downloader" in args)
    }

    @Test fun info_json_only_when_requested() {
        assertFalse("--write-info-json" in args(request(MediaType.AUDIO_MP3, "192k")))
        val req = request(MediaType.AUDIO_MP3, "192k").copy(writeInfoJson = true)
        assertTrue("--write-info-json" in args(req))
    }

    @Test fun cookies_only_when_a_file_is_set() {
        assertFalse("--cookies" in args(request(MediaType.VIDEO_MP4, "best")))
        val req = request(MediaType.VIDEO_MP4, "best").copy(cookiesFile = "/data/files/cookies/youtube.txt")
        assertEquals("/data/files/cookies/youtube.txt", args(req).valueOf("--cookies"))
    }

    @Test fun aria2c_flag_when_enabled() {
        assertEquals("aria2c", args(request(MediaType.VIDEO_MP4, "best", aria2c = true)).valueOf("--downloader"))
    }
}
