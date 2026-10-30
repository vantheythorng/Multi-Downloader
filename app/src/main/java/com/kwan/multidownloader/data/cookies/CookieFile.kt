package com.kwan.multidownloader.data.cookies

import com.kwan.multidownloader.domain.Platform

/** Validates and normalises a Netscape `cookies.txt` (the format yt-dlp's `--cookies` reads). */
object CookieFile {
    const val HEADER = "# Netscape HTTP Cookie File"
    private val HEADERS = listOf(HEADER, "# HTTP Cookie File")
    private const val HTTP_ONLY_PREFIX = "#HttpOnly_"

    /** Domains whose cookies a platform needs; YouTube logins live on google.com too. */
    fun domainsFor(platform: Platform): List<String> = when (platform) {
        Platform.YOUTUBE, Platform.YT_MUSIC -> listOf("youtube.com", "google.com")
        Platform.FACEBOOK -> listOf("facebook.com")
        Platform.TIKTOK -> listOf("tiktok.com")
        Platform.X -> listOf("x.com", "twitter.com")
        Platform.INSTAGRAM -> listOf("instagram.com")
        Platform.PINTEREST -> listOf("pinterest.com")
        Platform.OTHER -> emptyList()
    }

    sealed interface Parsed {
        /** [text] is ready for yt-dlp: LF line endings and the header it expects. */
        data class Valid(val text: String, val cookieCount: Int) : Parsed
        data object NotNetscape : Parsed
        data object NoCookiesForPlatform : Parsed
    }

    fun parse(raw: String, platform: Platform): Parsed {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').lines()
        val cookieDomains = lines.mapNotNull(::cookieDomain)
        if (cookieDomains.isEmpty()) return Parsed.NotNetscape
        val wanted = domainsFor(platform)
        val matching = cookieDomains.count { domain -> wanted.any { domain == it || domain.endsWith(".$it") } }
        if (matching == 0) return Parsed.NoCookiesForPlatform
        val body = lines.dropWhile { it.isBlank() }
        val text = if (body.firstOrNull()?.let { first -> HEADERS.any { first.startsWith(it) } } == true) {
            body.joinToString("\n")
        } else {
            (listOf(HEADER, "") + body).joinToString("\n")
        }
        return Parsed.Valid(text.trimEnd() + "\n", matching)
    }

    /** Counts cookies already stored for display; the file was validated on import. */
    fun count(text: String): Int = text.lines().count { cookieDomain(it) != null }

    /** A cookie line has 7 tab-separated fields: domain, subdomains flag, path, secure, expiry, name, value. */
    private fun cookieDomain(line: String): String? {
        val content = when {
            line.startsWith(HTTP_ONLY_PREFIX) -> line.removePrefix(HTTP_ONLY_PREFIX)
            line.startsWith("#") || line.isBlank() -> return null
            else -> line
        }
        val fields = content.split('\t')
        if (fields.size != 7) return null
        val (domain, subdomains, _, secure, expiry) = fields
        val flags = setOf("TRUE", "FALSE")
        if (subdomains.uppercase() !in flags || secure.uppercase() !in flags || expiry.toLongOrNull() == null) return null
        return domain.lowercase().removePrefix(".")
    }
}
