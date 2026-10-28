package com.kwan.multidownloader.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.kwan.multidownloader.R

/** One bundled component: [licence] is the SPDX-style name, [url] its project or licence page. */
private data class Component(val name: String, val licence: String, val usedForRes: Int, val url: String)

// Native parts come from youtubedl-android's AARs; the Java/Kotlin list matches releaseRuntimeClasspath.
private val components = listOf(
    Component("yt-dlp", "Unlicense", R.string.licence_use_ytdlp, "https://github.com/yt-dlp/yt-dlp"),
    Component("youtubedl-android", "GPL-3.0", R.string.licence_use_youtubedl, "https://github.com/yausername/youtubedl-android"),
    Component("FFmpeg", "LGPL-2.1 / GPL-2.0", R.string.licence_use_ffmpeg, "https://ffmpeg.org/legal.html"),
    Component("libwebp", "BSD-3-Clause", R.string.licence_use_libwebp, "https://chromium.googlesource.com/webm/libwebp"),
    Component("Python", "PSF License", R.string.licence_use_python, "https://docs.python.org/3/license.html"),
    Component("aria2", "GPL-2.0", R.string.licence_use_aria2, "https://github.com/aria2/aria2"),
    Component("QuickJS", "MIT", R.string.licence_use_quickjs, "https://bellard.org/quickjs/"),
    Component("Android Jetpack & Compose", "Apache-2.0", R.string.licence_use_androidx, "https://developer.android.com/jetpack/androidx"),
    Component("Kotlin, kotlinx.coroutines & kotlinx.serialization", "Apache-2.0", R.string.licence_use_kotlin, "https://kotlinlang.org"),
    Component("Koin", "Apache-2.0", R.string.licence_use_koin, "https://insert-koin.io"),
    Component("Coil", "Apache-2.0", R.string.licence_use_coil, "https://github.com/coil-kt/coil"),
    Component("OkHttp & Okio", "Apache-2.0", R.string.licence_use_okhttp, "https://square.github.io/okhttp/"),
    Component("Jackson", "Apache-2.0", R.string.licence_use_jackson, "https://github.com/FasterXML/jackson"),
    Component("Apache Commons IO & Compress", "Apache-2.0", R.string.licence_use_commons, "https://commons.apache.org"),
    Component("Accompanist, JSpecify & Stately", "Apache-2.0", R.string.licence_use_support, "https://www.apache.org/licenses/LICENSE-2.0"),
)

@Composable
fun LicencesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.licences_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(components, key = { it.name }) { component ->
            ListItem(
                headlineContent = { Text(component.name) },
                supportingContent = { Text("${component.licence} · ${stringResource(component.usedForRes)}") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                modifier = Modifier.clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, component.url.toUri()))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            HorizontalDivider()
        }
    }
}
