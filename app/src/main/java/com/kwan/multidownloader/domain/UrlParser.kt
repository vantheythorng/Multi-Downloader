package com.kwan.multidownloader.domain

import java.net.URI

data class ParsedLink(val url: String, val platform: Platform) {
    /** Unknown hosts still go to yt-dlp but are flagged "unsupported, may fail". */
    val isSupported: Boolean get() = platform != Platform.OTHER
}

object UrlParser {
    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}>»\"'"
    private val TRACKING_PARAMS = setOf("si", "igsh", "feature", "fbclid")

    /** Share sheets often prepend a title, so take the first http(s) URL in the text. */
    fun extractFirstUrl(text: String): String? =
        URL_REGEX.find(text)?.value?.trimEnd { it in TRAILING_PUNCTUATION }

    /** Strips tracking params (si=, igsh=, utm_*) while keeping everything else in order. */
    fun normalize(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val fragmentStart = url.indexOf('#', queryStart).takeIf { it >= 0 } ?: url.length
        val base = url.substring(0, queryStart)
        val fragment = url.substring(fragmentStart)
        val kept = url.substring(queryStart + 1, fragmentStart)
            .split('&')
            .filter { it.isNotEmpty() }
            .filterNot { param ->
                val key = param.substringBefore('=').lowercase()
                key in TRACKING_PARAMS || key.startsWith("utm_")
            }
        return if (kept.isEmpty()) base + fragment else base + "?" + kept.joinToString("&") + fragment
    }

    fun detect(url: String): Platform {
        val uri = runCatching { URI(url) }.getOrNull() ?: return Platform.OTHER
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return Platform.OTHER
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery.orEmpty()
        return when {
            host == "music.youtube.com" -> Platform.YT_MUSIC
            host == "youtu.be" -> Platform.YOUTUBE
            host.endsWith("youtube.com") && isYouTubePath(path, query) -> Platform.YOUTUBE
            host == "fb.watch" -> Platform.FACEBOOK
            host.endsWith("facebook.com") && isFacebookPath(path, query) -> Platform.FACEBOOK
            host in TIKTOK_SHORT_HOSTS -> Platform.TIKTOK
            host.endsWith("tiktok.com") && (path.contains("/video/") || path.startsWith("/t/")) -> Platform.TIKTOK
            (host == "x.com" || host.endsWith("twitter.com")) && path.contains("/status/") -> Platform.X
            else -> Platform.OTHER
        }
    }

    fun parse(text: String): ParsedLink? {
        val url = extractFirstUrl(text) ?: return null
        val normalized = normalize(url)
        return ParsedLink(normalized, detect(normalized))
    }

    private val TIKTOK_SHORT_HOSTS = setOf("vm.tiktok.com", "vt.tiktok.com")

    private fun isYouTubePath(path: String, query: String): Boolean =
        (path == "/watch" && query.contains("v=")) ||
            (path == "/playlist" && query.contains("list=")) ||
            path.startsWith("/shorts/") ||
            path.startsWith("/live/")

    private fun isFacebookPath(path: String, query: String): Boolean =
        path.contains("/videos/") ||
            path.startsWith("/reel/") ||
            path.startsWith("/share/v/") ||
            path.startsWith("/share/r/") ||
            (path.startsWith("/watch") && query.contains("v="))
}
