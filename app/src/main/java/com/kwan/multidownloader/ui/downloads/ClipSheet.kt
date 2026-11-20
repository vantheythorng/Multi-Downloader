package com.kwan.multidownloader.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kwan.multidownloader.R
import com.kwan.multidownloader.ui.components.formatDuration
import kotlinx.coroutines.delay

/**
 * Picks a stretch of a finished download and sends its audio as a clip, without saving it.
 * Preview plays just that stretch; the player lives only as long as the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipSheet(
    state: ClipState,
    onDuration: (Long) -> Unit,
    onRangeChange: (Long, Long) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnDuration by rememberUpdatedState(onDuration)
    var playing by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            // Audio only, so an MP4's picture is never decoded.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.duration != C.TIME_UNSET) currentOnDuration(player.duration)
                if (playbackState == Player.STATE_ENDED) playing = false
            }
        }
        player.addListener(listener)
        state.item.contentUri?.let { player.setMediaItem(MediaItem.fromUri(it.toUri())) }
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Plays from the start of the range and stops at its end.
    LaunchedEffect(playing) {
        if (!playing) {
            player.pause()
            return@LaunchedEffect
        }
        player.seekTo(state.startMs)
        player.play()
        while (player.currentPosition < state.endMs && player.playbackState != Player.STATE_ENDED) delay(50)
        playing = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.clip_title), style = MaterialTheme.typography.titleLarge)
            Text(
                state.item.title ?: state.item.sourceUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (state.durationMs > 0) {
                RangeSlider(
                    value = state.startMs.toFloat()..state.endMs.toFloat(),
                    onValueChange = { range ->
                        playing = false
                        onRangeChange(range.start.toLong(), range.endInclusive.toLong())
                    },
                    valueRange = 0f..state.durationMs.toFloat(),
                    enabled = state.progress == null,
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(clipTime(state.startMs), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.clip_length, clipTime(state.lengthMs)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(clipTime(state.endMs), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // Until the player knows the length.
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
            }

            when {
                state.progress != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.clip_making), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                state.failed -> Text(
                    stringResource(R.string.clip_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(
                    onClick = { playing = !playing },
                    enabled = state.durationMs > 0 && state.progress == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(if (playing) R.string.clip_stop else R.string.clip_preview))
                }
                Button(
                    onClick = {
                        playing = false
                        onSend()
                    },
                    enabled = state.canSend,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.clip_send))
                }
            }
        }
    }
}

private fun clipTime(ms: Long): String = formatDuration((ms / 1000).toInt())
