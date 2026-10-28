package com.kwan.multidownloader.engine

import com.kwan.multidownloader.domain.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextCleanupTest {

    @Test fun decodes_named_and_numeric_entities_keeping_line_breaks() {
        assertEquals("323K views · 6.1K reactions", TextCleanup.decodeEntities("323K views &#xb7; 6.1K reactions"))
        assertEquals("Kwan & Be Brave\nNext line", TextCleanup.decodeEntities("Kwan &amp; Be Brave\nNext line"))
        assertEquals("<\"a\"> 'b' ©", TextCleanup.decodeEntities("&lt;&quot;a&quot;&gt; &apos;b&#39; &#169;"))
        assertEquals("&unknown; stays", TextCleanup.decodeEntities("&unknown; stays"))
    }

    @Test fun facebook_title_loses_its_stats_prefix() {
        assertEquals(
            "KWAN - Tiny camp (live) Confuse",
            TextCleanup.title("323K views &#xb7; 6.1K reactions | KWAN - Tiny camp (live) Confuse", Platform.FACEBOOK),
        )
        // Only Facebook titles are touched.
        assertEquals("1M views | Song", TextCleanup.title("1M views | Song", Platform.YOUTUBE))
    }

    @Test fun facebook_page_title_used_as_uploader_becomes_the_page_name() {
        val pageTitle = "323K views &#xb7; 6.1K reactions | KWAN - Tiny camp (live)\n\nProduced by: slaphouse\n" +
            "Executive Producers: Kwan &amp; Be Brave\nSpecial thanks to Templation | Kwan"
        assertEquals("Kwan", TextCleanup.uploader(pageTitle))
    }

    @Test fun facebook_full_caption_comes_from_the_page_title() {
        val pageTitle = "323K views &#xb7; 6.1K reactions | KWAN - Tiny camp (live) Confuse\n\nProduced by: slaphouse\n" +
            "Executive Producers: Kwan &amp; Be Brave | Kwan"
        assertEquals(
            "KWAN - Tiny camp (live) Confuse\n\nProduced by: slaphouse\nExecutive Producers: Kwan & Be Brave",
            TextCleanup.facebookCaption(pageTitle),
        )
        assertNull(TextCleanup.facebookCaption("Kwan"))
        assertNull(TextCleanup.facebookCaption(null))
    }

    @Test fun links_are_removed_from_titles_and_captions() {
        assertEquals(
            "KWAN - Tiny camp (live) Confuse",
            TextCleanup.title("323K views &#xb7; 6.1K reactions | KWAN - Tiny camp (live) Confuse https://youtu.be/U4korRD5lSY?si=g0Jm", Platform.FACEBOOK),
        )
        assertEquals(
            "Confuse\n\nProduced by: slaphouse\nMore at",
            TextCleanup.description("Confuse https://youtu.be/x?si=1\n\n\nProduced by: slaphouse\nMore at www.example.com/page"),
        )
        // Only a link: keep it rather than an empty heading; an only-link description is dropped.
        assertEquals("https://youtu.be/abc", TextCleanup.title("https://youtu.be/abc", Platform.YOUTUBE))
        assertNull(TextCleanup.description("https://youtu.be/abc"))
    }

    @Test fun longer_text_wins() {
        assertEquals("full caption text", TextCleanup.longer("full cap...", "full caption text"))
        assertEquals("only", TextCleanup.longer("only", null))
        assertNull(TextCleanup.longer(null, null))
    }

    @Test fun normal_uploaders_are_kept_and_unusable_ones_dropped() {
        assertEquals("Glomyy Vincent", TextCleanup.uploader("Glomyy Vincent"))
        assertNull(TextCleanup.uploader("a".repeat(200)))
        assertNull(TextCleanup.uploader("   "))
        assertNull(TextCleanup.uploader(null))
    }
}
