package com.kwan.multidownloader.share

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kwan.multidownloader.R
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.ui.components.FirstRunNotice
import com.kwan.multidownloader.ui.components.FormatSheetContent
import com.kwan.multidownloader.ui.theme.AppTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Receives links shared from other apps and shows the format sheet over them, so the user never leaves the source app. */
class ShareReceiverActivity : ComponentActivity() {
    private val viewModel: ShareViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AppTheme {
                ShareSheet(viewModel = viewModel, onFinish = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> viewModel.onShared(
                text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT),
                forcedType = ShareShortcuts.typeFor(intent),
            )
            // "Open with" on a supported link (needs "Open supported links" on Android 12+).
            Intent.ACTION_VIEW -> viewModel.onShared(intent.dataString)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(viewModel: ShareViewModel, onFinish: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinish() }

    val addedToQueue = stringResource(R.string.added_to_queue)
    val quickMp3 = stringResource(R.string.share_quick_queued_mp3)
    val quickVideo = stringResource(R.string.share_quick_queued_video)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                ShareEvent.Enqueued -> addedToQueue
                is ShareEvent.QuickEnqueued -> if (event.type == MediaType.AUDIO_MP3) quickMp3 else quickVideo
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            // Without this, a user who shares before ever opening the app gets no progress notification.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onFinish()
            }
        }
    }

    // Nothing is drawn while deciding, so a quick download only ever shows its toast.
    if (state == ShareState.Pending) return
    if (state == ShareState.NeedsNotice) {
        FirstRunNotice(onAccept = viewModel::acceptNotice, onCancel = onFinish)
        return
    }

    ModalBottomSheet(
        onDismissRequest = onFinish,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        when (val current = state) {
            ShareState.Pending, ShareState.NeedsNotice -> Unit
            ShareState.Loading -> SheetMessage { Progress(stringResource(R.string.home_loading)) }
            ShareState.UpdatingExtractor -> SheetMessage { Progress(stringResource(R.string.home_updating_extractor)) }
            is ShareState.Error -> SheetMessage {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    current.messageRes?.let { stringResource(it) } ?: current.message.orEmpty(),
                    modifier = Modifier.weight(1f),
                )
                if (current.canRetry) {
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                } else {
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.share_close)) }
                }
            }
            is ShareState.Ready -> FormatSheetContent(
                info = current.info,
                defaultType = settings.defaultTypeFor(current.info.platform),
                defaultQuality = settings::defaultQuality,
                onDownload = viewModel::download,
            )
        }
    }
}

@Composable
private fun SheetMessage(content: @Composable RowScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Progress(text: String) {
    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
    Spacer(Modifier.size(4.dp))
    Text(text)
}
