package com.kwan.multidownloader.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/** Open/share intents for MediaStore content URIs; these are shareable as-is, no FileProvider needed. */
object ShareIntents {
    fun open(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun share(uri: Uri, mimeType: String, title: String? = null): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Shares the post's link as text (already stripped of tracking params like si= by UrlParser). */
    fun shareLink(url: String, title: String? = null): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
            .apply { if (title != null) putExtra(Intent.EXTRA_SUBJECT, title) }
        return Intent.createChooser(send, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** One intent with every file; the MIME type is shared if all files agree, otherwise the wildcard type. */
    fun shareMultiple(files: List<Pair<Uri, String>>): Intent {
        val uris = files.map { it.first }
        val mimeType = files.map { it.second }.distinct().singleOrNull() ?: "*/*"
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType(mimeType)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The read grant covers ClipData, so every URI goes in it, not only the extra.
        if (uris.isNotEmpty()) {
            send.clipData = ClipData.newRawUri(null, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
        }
        return Intent.createChooser(send, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
