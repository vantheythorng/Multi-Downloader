package com.kwan.multidownloader.engine

import com.kwan.multidownloader.domain.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineParsingTest {

    @Test fun parses_dump_single_json() {
        val raw = """
            {"id":"abc","title":"Song","description":"  Full text\nwith two lines  ","uploader":"Artist","thumbnail":"https://i/x.jpg","duration":215.4,
             "webpage_url":"https://www.youtube.com/watch?v=abc","unknown_field":{"nested":true},
             "formats":[{"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","filesize":3400000,"abr":129.5},
                        {"format_id":"137","ext":"mp4","height":1080,"vcodec":"avc1","acodec":"none","filesize_approx":5.0E7,"tbr":4000.1}]}
        """.trimIndent()
        val info = YtDlpJson.parseInfo(raw, "https://youtu.be/abc")
        assertEquals("abc", info.id)
        assertEquals("Song", info.title)
        assertEquals("Full text\nwith two lines", info.description)
        assertEquals("Artist", info.uploader)
        assertEquals(215, info.durationSec)
        assertEquals(Platform.YOUTUBE, info.platform)
        assertEquals(2, info.formats.size)
        assertEquals(50_000_000L, info.formats[1].filesize)
        assertTrue(info.formats[1].hasVideo)
        assertFalse(info.formats[0].hasVideo)
    }

    @Test fun multi_video_post_uses_first_entry() {
        val raw = """{"_type":"playlist","id":"p","title":"Post","entries":[{"id":"v1","title":"Clip 1","formats":[{"format_id":"a"}]},{"id":"v2"}]}"""
        val info = YtDlpJson.parseInfo(raw, "https://x.com/u/status/1")
        assertEquals("v1", info.id)
        assertEquals("Clip 1", info.title)
        assertEquals(Platform.X, info.platform)
    }

    @Test fun maps_common_yt_dlp_errors() {
        assertEquals("Private video", YtDlpErrors.map("ERROR: [youtube] abc: Private video. Sign in if you've been granted access").message)
        assertEquals("Unsupported link", YtDlpErrors.map("ERROR: Unsupported URL: https://example.com").message)
        val network = YtDlpErrors.map("ERROR: Unable to download webpage: <urlopen error timed out>")
        assertEquals("Network error", network.message)
        assertTrue(network.retryable)
        val outdated = YtDlpErrors.map("ERROR: [tiktok] 1: Unable to extract universal data")
        assertEquals("Extractor outdated, update yt-dlp", outdated.message)
        assertEquals(ErrorKind.EXTRACTOR_OUTDATED, outdated.kind)
        assertFalse(outdated.retryable)
        assertEquals(ErrorKind.EXTRACTOR_OUTDATED, YtDlpErrors.map("ERROR: [youtube] x: Requested format is not available").kind)
        assertEquals(ErrorKind.NETWORK, network.kind)
        assertEquals(ErrorKind.PRIVATE, YtDlpErrors.map("ERROR: Private video").kind)
    }

    @Test fun http_errors_inside_webpage_failures_are_not_network_errors() {
        val notFound = YtDlpErrors.map("ERROR: [generic] clip: Unable to download webpage: HTTP Error 404: Not Found")
        assertEquals(ErrorKind.LINK_NOT_FOUND, notFound.kind)
        assertEquals("Link not found", notFound.message)
        assertFalse(notFound.retryable)
        assertEquals(
            ErrorKind.EXTRACTOR_OUTDATED,
            YtDlpErrors.map("ERROR: [youtube] x: Unable to download webpage: HTTP Error 403: Forbidden").kind,
        )
        assertTrue(YtDlpErrors.map("ERROR: Unable to download webpage: HTTP Error 503: Service Unavailable").retryable)
        val limited = YtDlpErrors.map("ERROR: Unable to download webpage: HTTP Error 429: Too Many Requests")
        assertEquals(ErrorKind.RATE_LIMITED, limited.kind)
        assertTrue(limited.retryable)
        assertEquals("Something odd", YtDlpErrors.map("WARNING: x\nERROR: Something odd").message)
        assertEquals("Download failed", YtDlpErrors.map(null).message)
    }

    @Test fun parses_progress_lines() {
        assertEquals("2.31MiB/s", ProgressLine.speed("[download]  12.3% of ~ 10.00MiB at  2.31MiB/s ETA 00:04"))
        assertNull(ProgressLine.speed("[download]  12.3% of 10.00MiB at Unknown B/s ETA Unknown"))
        assertTrue(ProgressLine.isPostProcessing("[ExtractAudio] Destination: a.mp3"))
        assertTrue(ProgressLine.isPostProcessing("[Merger] Merging formats into \"a.mp4\""))
        assertFalse(ProgressLine.isPostProcessing("[download] Destination: a.webm"))
        assertTrue(ProgressLine.isDownloading("[download]  12.3% of ~ 10.00MiB at  2.31MiB/s ETA 00:04"))
        assertFalse(ProgressLine.isDownloading("[youtube] abc: Downloading webpage"))
    }
}
