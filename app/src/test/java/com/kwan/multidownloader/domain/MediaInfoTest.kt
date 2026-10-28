package com.kwan.multidownloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaInfoTest {
    private fun info(title: String, description: String?) = MediaInfo(
        id = "x", url = "https://vt.tiktok.com/x/", platform = Platform.TIKTOK, title = title,
        description = description, uploader = null, thumbnailUrl = null, durationSec = null, formats = emptyList(),
    )

    @Test fun caption_used_as_title_is_not_shown_twice() {
        // Real TikTok case: same caption, title has a trailing space.
        assertNull(info("#fypシ゚viral #love #fyp #fy #xyzbca ", "#fypシ゚viral #love #fyp #fy #xyzbca").extraDescription)
        assertNull(info("Line one\nline two", "Line one  line two").extraDescription)
    }

    @Test fun a_fuller_description_is_shown() {
        val full = "A long caption that yt-dlp shortened for the title, with more words at the end"
        assertEquals(full, info("A long caption that yt-dlp shortened...", full).extraDescription)
    }

    @Test fun no_description_shows_nothing() {
        assertNull(info("Song", null).extraDescription)
    }
}
