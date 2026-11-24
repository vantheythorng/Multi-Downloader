package com.kwan.multidownloader.domain

/** Turns a media title into a short name that's safe for shared storage. */
object FileNames {
    const val MAX_CHARS = 50

    /** Keeps CJK and emoji titles well under the 255-byte file name limit. */
    private const val MAX_BYTES = 150

    /** A cut at a space is only used if it keeps at least this much; otherwise cut mid-word. */
    private const val MIN_WORD_CUT = 25

    private val ILLEGAL = Regex("""[/\\:*?"<>|\p{Cntrl}]""")
    private val WHITESPACE = Regex("""\s+""")
    private const val EDGE_CHARS = ".-|·_"

    /** "<title>.<extension>", or [fallback] when the title has nothing usable left. */
    fun forTitle(title: String?, fallback: String, extension: String): String {
        val name = title?.let { shorten(it.replace(ILLEGAL, " ").replace(WHITESPACE, " ").trimEdges()) }
        return "${name?.takeIf { it.isNotEmpty() } ?: fallback}.$extension"
    }

    private fun shorten(name: String): String {
        var end = 0
        var bytes = 0
        while (end < name.length) {
            val codePoint = name.codePointAt(end)
            val next = end + Character.charCount(codePoint)
            bytes += utf8Size(codePoint)
            if (next > MAX_CHARS || bytes > MAX_BYTES) break
            end = next
        }
        if (end == name.length) return name
        val hard = name.substring(0, end)
        val atWord = if (name[end] == ' ') hard else hard.substringBeforeLast(' ', "")
        return (if (atWord.length >= MIN_WORD_CUT) atWord else hard).trimEdges()
    }

    private fun utf8Size(codePoint: Int) = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    private fun String.trimEdges() = trim { it.isWhitespace() || it in EDGE_CHARS }
}
