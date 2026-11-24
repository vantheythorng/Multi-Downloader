package com.kwan.multidownloader.share

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.kwan.multidownloader.MainActivity
import com.kwan.multidownloader.R
import com.kwan.multidownloader.domain.MediaType

/**
 * Sharing shortcuts: "Download MP3" / "Download Video" appear as direct share targets, so one tap in the
 * share sheet queues the link in that format. They match the share-target in res/xml/shortcuts.xml.
 */
object ShareShortcuts {
    const val CATEGORY = "com.kwan.multidownloader.category.DOWNLOAD"
    private const val ID_MP3 = "download_mp3"
    private const val ID_VIDEO = "download_video"

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, ID_MP3, R.string.shortcut_download_mp3, R.drawable.ic_shortcut_audio),
            shortcut(context, ID_VIDEO, R.string.shortcut_download_video, R.drawable.ic_shortcut_video),
        )
        shortcuts.forEach { ShortcutManagerCompat.pushDynamicShortcut(context, it) }
    }

    /** The format a shortcut stands for, from the intent the share sheet delivers. */
    fun typeFor(intent: Intent): MediaType? = when (intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)) {
        ID_MP3 -> MediaType.AUDIO_MP3
        ID_VIDEO -> MediaType.VIDEO_MP4
        else -> null
    }

    private fun shortcut(context: Context, id: String, labelRes: Int, iconRes: Int): ShortcutInfoCompat {
        val label = context.getString(labelRes)
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setCategories(setOf(CATEGORY))
            .setLongLived(true)
            // Only used when tapped from the launcher's long-press menu, where there is no link: open the app.
            .setIntent(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java))
            .build()
    }
}
