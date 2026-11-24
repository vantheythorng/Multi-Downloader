package com.kwan.multidownloader.ui.components

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kwan.multidownloader.R
import com.kwan.multidownloader.domain.Quality
import com.kwan.multidownloader.domain.Status

fun formatDuration(seconds: Int): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)

fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

@Composable
fun qualityLabel(quality: String): String = when {
    quality == Quality.BEST -> stringResource(R.string.format_quality_best)
    quality.endsWith("k") -> "${quality.removeSuffix("k")} kbps"
    else -> quality
}

@Composable
fun statusLabel(status: Status): String = stringResource(
    when (status) {
        Status.QUEUED -> R.string.status_queued
        Status.FETCHING -> R.string.status_fetching
        Status.DOWNLOADING -> R.string.status_downloading
        Status.CONVERTING -> R.string.status_converting
        Status.PAUSED -> R.string.status_paused
        Status.DONE -> R.string.status_done
        Status.FAILED -> R.string.status_failed
        Status.CANCELLED -> R.string.status_cancelled
    }
)
