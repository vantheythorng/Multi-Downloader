package com.kwan.multidownloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    private fun name(title: String?) = FileNames.forTitle(title, fallback = "abc123", extension = "mp4")

    @Test fun short_title_is_kept() {
        assertEquals("KWAN - Tiny camp (live) Confuse.mp4", name("KWAN - Tiny camp (live) Confuse"))
    }

    @Test fun long_title_is_cut_at_a_word() {
        val result = name("This is a very long caption that keeps going well past the limit of the name")
        assertEquals("This is a very long caption that keeps going well.mp4", result)
        assertTrue(result.removeSuffix(".mp4").length <= FileNames.MAX_CHARS)
    }

    @Test fun long_word_is_cut_mid_word() {
        assertEquals("a".repeat(FileNames.MAX_CHARS) + ".mp4", name("a".repeat(80)))
    }

    @Test fun illegal_characters_and_line_breaks_become_spaces() {
        assertEquals("AC DC Back in Black live.mp4", name("AC/DC: Back in \"Black\"\n\n*live*"))
    }

    @Test fun edges_lose_dots_and_separators() {
        assertEquals("Song.mp4", name(" | Song ... "))
    }

    @Test fun emoji_is_not_split() {
        val result = name("x".repeat(FileNames.MAX_CHARS - 1) + "😀 tail")
        assertEquals("x".repeat(FileNames.MAX_CHARS - 1) + ".mp4", result)
    }

    @Test fun cjk_title_is_capped_by_bytes() {
        val result = name("日本語".repeat(30))
        assertTrue(result.toByteArray().size <= 150 + ".mp4".length)
        assertEquals("日本語".repeat(16) + "日本.mp4", result)
    }

    @Test fun missing_or_empty_title_uses_the_fallback() {
        assertEquals("abc123.mp4", name(null))
        assertEquals("abc123.mp4", name(" ?/* "))
    }
}
