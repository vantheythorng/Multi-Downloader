package com.kwan.multidownloader.engine

import com.kwan.multidownloader.domain.Platform

/**
 * Why a fetch or download failed. Stored with each failed download; the UI shows translatable
 * text per kind (see ui/components/ErrorText.kt) and falls back to the raw message for [OTHER].
 */
enum class ErrorKind {
    PRIVATE,
    UNSUPPORTED,
    UNAVAILABLE,
    LINK_NOT_FOUND,
    RATE_LIMITED,
    LOGIN_REQUIRED,
    /** A photo-only post (Instagram, Pinterest): there is nothing to save as MP4 or MP3. */
    NO_VIDEO,
    NETWORK,
    EXTRACTOR_OUTDATED,
    /** Still failing on the newest yt-dlp: the site changed and no fix is released yet. */
    SITE_CHANGED,
    SAVE_FAILED,
    OTHER;

    /** Worth retrying automatically: the next attempt may well succeed. */
    val retryable: Boolean get() = this == NETWORK || this == RATE_LIMITED
}

/** Turns raw yt-dlp stderr into an [ErrorKind] plus an English fallback message. */
object YtDlpErrors {
    data class Mapped(val message: String, val kind: ErrorKind) {
        val retryable: Boolean get() = kind.retryable
    }

    private val NETWORK_HINTS = listOf(
        "unable to download webpage", "timed out", "connection reset", "connection refused",
        "network is unreachable", "temporary failure in name resolution", "getaddrinfo", "remote end closed",
    )
    private val LOGIN_HINTS = listOf("sign in to confirm", "login required", "requires authentication", "use --cookies")
    private val OUTDATED_HINTS = listOf(
        "unable to extract", "please report this issue", "http error 403", "nsig extraction failed",
        "requested format is not available", "only images are available", "signature extraction failed",
    )

    /** Instagram's own message for a photo post. */
    private const val INSTAGRAM_NO_VIDEO = "there is no video in this post"
    /** yt-dlp's generic message, e.g. for a Pinterest image pin; elsewhere it can mean a broken extractor. */
    private const val NO_FORMATS = "no video formats found"
    private val PHOTO_PLATFORMS = setOf(Platform.INSTAGRAM, Platform.PINTEREST)

    private val HTTP_STATUS = Regex("""http error (\d{3})""")

    /** [platform] is the link's platform; it decides whether "no video formats" means a photo post. */
    fun map(raw: String?, platform: Platform? = null): Mapped {
        val text = raw.orEmpty()
        val lower = text.lowercase()
        // "Unable to download webpage" wraps both network failures and HTTP errors; only 5xx and no status are transient.
        val httpStatus = HTTP_STATUS.find(lower)?.groupValues?.get(1)?.toInt()
        return when {
            "private video" in lower -> Mapped("Private video", ErrorKind.PRIVATE)
            "unsupported url" in lower -> Mapped("Unsupported link", ErrorKind.UNSUPPORTED)
            "video unavailable" in lower || "has been removed" in lower -> Mapped("Video unavailable", ErrorKind.UNAVAILABLE)
            httpStatus == 404 || httpStatus == 410 -> Mapped("Link not found", ErrorKind.LINK_NOT_FOUND)
            httpStatus == 429 -> Mapped("Too many requests, try again later", ErrorKind.RATE_LIMITED)
            LOGIN_HINTS.any { it in lower } -> Mapped("Login required for this content", ErrorKind.LOGIN_REQUIRED)
            INSTAGRAM_NO_VIDEO in lower || (NO_FORMATS in lower && platform in PHOTO_PLATFORMS) ->
                Mapped("This post has no video", ErrorKind.NO_VIDEO)
            NETWORK_HINTS.any { it in lower } && (httpStatus == null || httpStatus >= 500) ->
                Mapped("Network error", ErrorKind.NETWORK)
            OUTDATED_HINTS.any { it in lower } -> Mapped(OUTDATED_MESSAGE, ErrorKind.EXTRACTOR_OUTDATED)
            else -> Mapped(lastErrorLine(text) ?: "Download failed", ErrorKind.OTHER)
        }
    }

    const val OUTDATED_MESSAGE = "Extractor outdated, update yt-dlp"

    const val SITE_CHANGED_MESSAGE = "This site changed and yt-dlp doesn't support it yet, try again later"

    private fun lastErrorLine(text: String): String? =
        text.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("ERROR:") }
            ?.removePrefix("ERROR:")
            ?.trim()
            ?.take(160)
}
