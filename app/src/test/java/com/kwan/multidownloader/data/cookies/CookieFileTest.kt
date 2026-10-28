package com.kwan.multidownloader.data.cookies

import com.kwan.multidownloader.domain.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieFileTest {
    private val youtube = listOf(
        "# Netscape HTTP Cookie File",
        "# This is a generated file! Do not edit.",
        "",
        ".youtube.com\tTRUE\t/\tTRUE\t1893456000\tPREF\tf6=40000000",
        "#HttpOnly_.youtube.com\tTRUE\t/\tTRUE\t1893456000\tLOGIN_INFO\tabc",
        ".google.com\tTRUE\t/\tTRUE\t1893456000\tSID\txyz",
        ".example.com\tTRUE\t/\tFALSE\t0\tother\t1",
    ).joinToString("\n")

    @Test fun counts_only_cookies_for_the_platform_including_http_only() {
        val parsed = CookieFile.parse(youtube, Platform.YOUTUBE)
        assertTrue(parsed is CookieFile.Parsed.Valid)
        assertEquals(3, (parsed as CookieFile.Parsed.Valid).cookieCount)
        assertTrue(parsed.text.startsWith(CookieFile.HEADER))
    }

    @Test fun youtube_music_uses_youtube_domains() {
        assertTrue(CookieFile.parse(youtube, Platform.YT_MUSIC) is CookieFile.Parsed.Valid)
    }

    @Test fun cookies_for_another_site_are_rejected() {
        assertEquals(CookieFile.Parsed.NoCookiesForPlatform, CookieFile.parse(youtube, Platform.TIKTOK))
    }

    @Test fun x_accepts_twitter_domain() {
        val text = ".twitter.com\tTRUE\t/\tTRUE\t1893456000\tauth_token\tt"
        assertTrue(CookieFile.parse(text, Platform.X) is CookieFile.Parsed.Valid)
    }

    @Test fun header_is_added_and_crlf_normalised() {
        val windows = ".facebook.com\tTRUE\t/\tTRUE\t1893456000\tc_user\t1\r\n.facebook.com\tTRUE\t/\tTRUE\t1893456000\txs\t2\r\n"
        val parsed = CookieFile.parse(windows, Platform.FACEBOOK) as CookieFile.Parsed.Valid
        assertEquals(2, parsed.cookieCount)
        assertTrue(parsed.text.startsWith(CookieFile.HEADER + "\n"))
        assertFalse(parsed.text.contains('\r'))
    }

    @Test fun json_or_random_text_is_not_netscape() {
        assertEquals(CookieFile.Parsed.NotNetscape, CookieFile.parse("""[{"domain":".youtube.com","name":"SID"}]""", Platform.YOUTUBE))
        assertEquals(CookieFile.Parsed.NotNetscape, CookieFile.parse("youtube.com SID abc", Platform.YOUTUBE))
        assertEquals(CookieFile.Parsed.NotNetscape, CookieFile.parse("", Platform.YOUTUBE))
    }

    @Test fun count_matches_stored_file() {
        assertEquals(4, CookieFile.count(youtube))
    }
}
