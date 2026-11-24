package com.kwan.multidownloader.engine

import com.kwan.multidownloader.domain.Platform

/** Tidies the text fields yt-dlp scrapes from web pages before the UI shows them. */
object TextCleanup {
    private val ENTITY = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);""")

    /** Facebook titles start with page stats: "323K views · 6.1K reactions | Actual title". */
    private val FACEBOOK_STATS_PREFIX =
        Regex("""^[^|\n]*\b(views|reactions|comments|shares|plays)\b[^|\n]*\|\s*""", RegexOption.IGNORE_CASE)

    private const val MAX_NAME_LENGTH = 60

    /** Links in titles and captions ("… https://youtu.be/…"): the format sheet shows media info, not URLs. */
    private val LINK = Regex("""(?i)\b(?:https?://|www\.)\S+""")
    private val SPACES = Regex("""[ \t]{2,}""")
    private val BLANK_LINES = Regex("""\n{3,}""")

    /** Removes links, then the double spaces and empty lines they leave behind. Keeps the remaining line breaks. */
    fun stripLinks(text: String): String =
        text.replace(LINK, "")
            .lines()
            .joinToString("\n") { it.replace(SPACES, " ").trim() }
            .replace(BLANK_LINES, "\n\n")
            .trim()

    /** Decodes the HTML entities Facebook and others leave in titles ("&#xb7;", "&amp;"). Keeps line breaks. */
    fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            else -> {
                val codePoint = if (entity[1] == 'x' || entity[1] == 'X') entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
                codePoint?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: match.value
            }
        }
    }

    fun title(raw: String, platform: Platform): String {
        val decoded = decodeEntities(raw).trim()
        val withoutStats = if (platform == Platform.FACEBOOK) decoded.replace(FACEBOOK_STATS_PREFIX, "").trim() else decoded
        // A title that was nothing but a link keeps it, rather than showing an empty heading.
        return stripLinks(withoutStats).ifEmpty { withoutStats.ifEmpty { decoded } }
    }

    fun description(raw: String?): String? = raw?.let { stripLinks(decodeEntities(it)) }?.takeIf { it.isNotEmpty() }

    /**
     * A channel or page name. When yt-dlp couldn't find one it sometimes returns the whole page title
     * ("…post text… | Page name"); take the part after the last " | ", or nothing if that isn't a name either.
     */
    fun uploader(raw: String?): String? {
        val decoded = raw?.let { decodeEntities(it).trim() }?.takeIf { it.isNotEmpty() } ?: return null
        if (decoded.looksLikeName()) return decoded
        return decoded.substringAfterLast(" | ", "").trim().takeIf { it.isNotEmpty() && it.looksLikeName() }
    }

    /**
     * Facebook: when yt-dlp returns the page title as the uploader ("stats | full caption | Page name"), the
     * middle is the post's complete caption with its line breaks; yt-dlp's own description is a cut-off preview.
     */
    fun facebookCaption(rawUploader: String?): String? {
        val decoded = rawUploader?.let { decodeEntities(it).trim() } ?: return null
        if (decoded.looksLikeName() || " | " !in decoded) return null
        return stripLinks(decoded.substringBeforeLast(" | ").replace(FACEBOOK_STATS_PREFIX, "")).takeIf { it.isNotEmpty() }
    }

    /** The fuller of two texts for the same post, e.g. Facebook's cut-off preview vs the full caption. */
    fun longer(a: String?, b: String?): String? = listOfNotNull(a, b).maxByOrNull { it.length }

    private fun String.looksLikeName() = length <= MAX_NAME_LENGTH && '\n' !in this
}
