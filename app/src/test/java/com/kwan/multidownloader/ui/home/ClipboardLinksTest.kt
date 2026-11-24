package com.kwan.multidownloader.ui.home

import com.kwan.multidownloader.domain.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardLinksTest {

    @Test fun supported_link_is_offered_cleaned() {
        val link = ClipboardLinks.offer("Watch this https://youtu.be/abc?si=tracking")
        assertEquals("https://youtu.be/abc", link?.url)
        assertEquals(Platform.YOUTUBE, link?.platform)
    }

    @Test fun unsupported_site_is_not_offered() =
        assertNull(ClipboardLinks.offer("https://example.com/video/1"))

    @Test fun text_without_link_is_not_offered() =
        assertNull(ClipboardLinks.offer("just some copied words"))

    @Test fun display_url_drops_scheme_and_www() {
        assertEquals("youtube.com/watch?v=abc", ClipboardLinks.displayUrl("https://www.youtube.com/watch?v=abc"))
        assertEquals("youtu.be/abc", ClipboardLinks.displayUrl("https://youtu.be/abc"))
    }
}
