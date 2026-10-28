package com.kwan.multidownloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlParserTest {

    private fun assertParsed(shareText: String, expectedUrl: String, expectedPlatform: Platform) {
        val link = UrlParser.parse(shareText)
        assertEquals("url for: $shareText", expectedUrl, link?.url)
        assertEquals("platform for: $shareText", expectedPlatform, link?.platform)
    }

    @Test fun youtube_watch_link_with_si_param() = assertParsed(
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=AbCdEf123",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        Platform.YOUTUBE,
    )

    @Test fun youtube_short_link_from_share_sheet() = assertParsed(
        "https://youtu.be/dQw4w9WgXcQ?si=xyz",
        "https://youtu.be/dQw4w9WgXcQ",
        Platform.YOUTUBE,
    )

    @Test fun youtube_shorts_mobile_and_live() {
        assertParsed("https://youtube.com/shorts/abc123?feature=share", "https://youtube.com/shorts/abc123", Platform.YOUTUBE)
        assertParsed("https://m.youtube.com/watch?v=abc", "https://m.youtube.com/watch?v=abc", Platform.YOUTUBE)
        assertParsed("https://www.youtube.com/live/abc123", "https://www.youtube.com/live/abc123", Platform.YOUTUBE)
    }

    @Test fun youtube_playlist() = assertParsed(
        "https://www.youtube.com/playlist?list=PL123",
        "https://www.youtube.com/playlist?list=PL123",
        Platform.YOUTUBE,
    )

    @Test fun youtube_music_is_not_plain_youtube() = assertParsed(
        "https://music.youtube.com/watch?v=abc123&si=tracking",
        "https://music.youtube.com/watch?v=abc123",
        Platform.YT_MUSIC,
    )

    @Test fun facebook_variants() {
        assertParsed("https://www.facebook.com/someone/videos/123456/", "https://www.facebook.com/someone/videos/123456/", Platform.FACEBOOK)
        assertParsed("https://www.facebook.com/reel/987654", "https://www.facebook.com/reel/987654", Platform.FACEBOOK)
        assertParsed("https://www.facebook.com/watch/?v=123", "https://www.facebook.com/watch/?v=123", Platform.FACEBOOK)
        assertParsed("https://www.facebook.com/share/v/AbC123/", "https://www.facebook.com/share/v/AbC123/", Platform.FACEBOOK)
        assertParsed("https://fb.watch/abcDEF/", "https://fb.watch/abcDEF/", Platform.FACEBOOK)
        assertParsed("https://m.facebook.com/watch/?v=123", "https://m.facebook.com/watch/?v=123", Platform.FACEBOOK)
    }

    @Test fun tiktok_variants() {
        assertParsed(
            "Check this out! https://www.tiktok.com/@user/video/7300000000000000000?is_from_webapp=1",
            "https://www.tiktok.com/@user/video/7300000000000000000?is_from_webapp=1",
            Platform.TIKTOK,
        )
        assertParsed("https://vm.tiktok.com/ZMabc123/", "https://vm.tiktok.com/ZMabc123/", Platform.TIKTOK)
        assertParsed("https://vt.tiktok.com/ZSabc/", "https://vt.tiktok.com/ZSabc/", Platform.TIKTOK)
        assertParsed("https://www.tiktok.com/t/ZTabc/", "https://www.tiktok.com/t/ZTabc/", Platform.TIKTOK)
    }

    @Test fun x_and_twitter_status() {
        assertParsed("https://x.com/user/status/1234567890?s=20", "https://x.com/user/status/1234567890?s=20", Platform.X)
        assertParsed("https://twitter.com/user/status/123", "https://twitter.com/user/status/123", Platform.X)
        assertParsed("https://mobile.twitter.com/user/status/123", "https://mobile.twitter.com/user/status/123", Platform.X)
    }

    @Test fun share_text_with_title_before_link() = assertParsed(
        "Never Gonna Give You Up - Rick Astley\nhttps://youtu.be/dQw4w9WgXcQ?si=abc",
        "https://youtu.be/dQw4w9WgXcQ",
        Platform.YOUTUBE,
    )

    @Test fun first_url_wins_and_trailing_punctuation_is_trimmed() = assertParsed(
        "Look (https://x.com/a/status/1). Also https://youtu.be/b",
        "https://x.com/a/status/1",
        Platform.X,
    )

    @Test fun utm_and_igsh_params_are_stripped_but_others_kept() {
        assertEquals(
            "https://example.com/v?id=5#t=10",
            UrlParser.normalize("https://example.com/v?utm_source=a&id=5&igsh=zzz&UTM_Campaign=b#t=10"),
        )
    }

    @Test fun unknown_host_is_flagged_unsupported() {
        val link = UrlParser.parse("https://vimeo.com/123")!!
        assertEquals(Platform.OTHER, link.platform)
        assertFalse(link.isSupported)
        assertTrue(UrlParser.parse("https://youtu.be/x")!!.isSupported)
    }

    @Test fun youtube_channel_page_is_not_a_video() {
        assertEquals(Platform.OTHER, UrlParser.detect("https://www.youtube.com/@channel"))
    }

    @Test fun text_without_link_returns_null() {
        assertNull(UrlParser.parse("no link here"))
        assertNull(UrlParser.parse(""))
    }
}
