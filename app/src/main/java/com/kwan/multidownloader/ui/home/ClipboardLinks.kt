package com.kwan.multidownloader.ui.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import com.kwan.multidownloader.domain.ParsedLink
import com.kwan.multidownloader.domain.UrlParser

/** Text on the clipboard and when it was copied ([ClipDescription.getTimestamp]), which identifies the clip. */
data class CopiedText(val text: String, val stamp: Long)

object ClipboardLinks {
    /** The link to suggest for copied [text]: only links to supported sites. */
    fun offer(text: String): ParsedLink? = UrlParser.parse(text)?.takeIf { it.isSupported }

    /** The link as the suggestion shows it, without scheme or "www.". */
    fun displayUrl(url: String): String = url.substringAfter("://").removePrefix("www.")

    /**
     * Reads what's on the clipboard; null means it hasn't changed since [seenStamp]. An empty clipboard, or a
     * clip that clearly holds no link, comes back as empty text so the old suggestion goes away. Android 12+
     * shows a "pasted" toast for every content read, so the description, which shows none, is checked first.
     * Only works while the app's window has focus (Android 10+).
     */
    fun read(context: Context, seenStamp: Long): CopiedText? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val description = clipboard.primaryClipDescription ?: return CopiedText("", 0L)
        val stamp = description.timestamp
        if (stamp == seenStamp) return null
        if (!description.hasMimeType("text/*")) return CopiedText("", stamp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
            description.getConfidenceScore(TextClassifier.TYPE_URL) == 0f
        ) return CopiedText("", stamp)
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        return CopiedText(text.orEmpty(), stamp)
    }
}
