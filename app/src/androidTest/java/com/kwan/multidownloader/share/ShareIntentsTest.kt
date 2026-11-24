package com.kwan.multidownloader.share

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentsTest {
    private val mp3 = Uri.parse("content://media/external_primary/audio/media/1")
    private val mp4 = Uri.parse("content://media/external_primary/video/media/2")

    private fun Intent.target(): Intent =
        checkNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_INTENT, Intent::class.java)) { "chooser has no target intent" }

    @Test fun single_file_share_grants_read_access_through_a_chooser() {
        val chooser = ShareIntents.share(mp3, "audio/mpeg", "Song")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.target()
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("audio/mpeg", send.type)
        assertEquals(mp3, IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun multiple_files_use_send_multiple_and_wildcard_type_when_mixed() {
        val send = ShareIntents.shareMultiple(listOf(mp3 to "audio/mpeg", mp4 to "video/mp4")).target()
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals("*/*", send.type)
        assertEquals(listOf(mp3, mp4), IntentCompat.getParcelableArrayListExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
        // Every URI must be in ClipData too, or receivers only get permission for the first one.
        assertEquals(2, send.clipData!!.itemCount)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun multiple_files_of_one_type_keep_that_type() {
        val send = ShareIntents.shareMultiple(listOf(mp3 to "audio/mpeg", mp3 to "audio/mpeg")).target()
        assertEquals("audio/mpeg", send.type)
    }

    @Test fun share_link_sends_the_url_as_text() {
        val chooser = ShareIntents.shareLink("https://youtu.be/abc", "Song")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.target()
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("https://youtu.be/abc", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Song", send.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test fun open_uses_view_with_read_permission() {
        val open = ShareIntents.open(mp4, "video/mp4")
        assertEquals(Intent.ACTION_VIEW, open.action)
        assertEquals(mp4, open.data)
        assertEquals("video/mp4", open.type)
        assertTrue(open.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
