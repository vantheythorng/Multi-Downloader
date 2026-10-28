package com.kwan.multidownloader.share

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kwan.multidownloader.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Share-in without network: text with no link must show the error sheet instead of fetching anything. */
@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun shared_text_without_a_link_shows_no_link_error() {
        val intent = Intent(context, ShareReceiverActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "just some words, no link")
        ActivityScenario.launch<ShareReceiverActivity>(intent).use {
            compose.onNodeWithText(context.getString(R.string.share_no_link)).assertExists()
            compose.onNodeWithText(context.getString(R.string.share_close)).assertExists()
        }
    }
}
