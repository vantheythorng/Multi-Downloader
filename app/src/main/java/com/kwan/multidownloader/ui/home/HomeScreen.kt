package com.kwan.multidownloader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kwan.multidownloader.R
import com.kwan.multidownloader.domain.ParsedLink
import com.kwan.multidownloader.domain.Platform
import com.kwan.multidownloader.ui.components.FormatBottomSheet
import com.kwan.multidownloader.ui.components.PlatformIcon
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onEnqueued: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var fieldFocused by remember { mutableStateOf(false) }
    // Android 10+ only lets a focused window read the clipboard. Window focus also catches a link copied in
    // another app while the field kept its focus, and an emptied field one copied before clearing it.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val fieldEmpty = state.url.isBlank()

    LaunchedEffect(fieldFocused, windowFocused, fieldEmpty) {
        if (fieldFocused && windowFocused && fieldEmpty && viewModel.wantsClipboard()) {
            ClipboardLinks.read(context, viewModel.seenClipStamp)?.let { viewModel.onClipboardText(it.text, it.stamp) }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.Enqueued -> onEnqueued()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The copied-link suggestion sits right under the field, like a browser's address bar suggestion.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text(stringResource(R.string.home_url_label)) },
                placeholder = { Text(stringResource(R.string.home_url_placeholder)) },
                singleLine = true,
                leadingIcon = state.platform?.let { platform -> { PlatformIcon(platform, size = 24.dp) } },
                trailingIcon = {
                    if (state.url.isEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val text = clipboard.getClipEntry()?.clipData
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.text?.toString()
                                if (!text.isNullOrBlank()) viewModel.onUrlChange(text)
                            }
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.home_paste))
                        }
                    } else {
                        IconButton(onClick = viewModel::clear) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.home_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focusManager.clearFocus()
                    viewModel.fetch()
                }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { fieldFocused = it.isFocused },
            )

            val copied = state.clipboardLink
            if (copied != null && fieldFocused && state.url.isBlank() && settings.suggestCopiedLinks) {
                CopiedLinkSuggestion(
                    link = copied,
                    onUse = {
                        focusManager.clearFocus()
                        viewModel.useClipboardLink()
                    },
                    onDismiss = viewModel::dismissClipboardLink,
                )
            }
        }

        if (state.link?.isSupported == false) {
            Text(
                stringResource(R.string.home_unsupported_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SupportedPlatforms(detected = state.platform)

        Button(
            onClick = {
                focusManager.clearFocus()
                viewModel.fetch()
            },
            enabled = state.url.isNotBlank() && state.fetch != FetchState.Loading &&
                state.fetch != FetchState.UpdatingExtractor,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(R.string.home_fetch))
        }

        when (val fetch = state.fetch) {
            FetchState.Empty -> EmptyHint()
            FetchState.Loading -> LoadingInfo(stringResource(R.string.home_loading))
            FetchState.UpdatingExtractor -> LoadingInfo(stringResource(R.string.home_updating_extractor))
            is FetchState.Error -> ErrorCard(
                message = fetch.messageRes?.let { stringResource(it) } ?: fetch.message.orEmpty(),
                onRetry = viewModel::fetch.takeIf { fetch.canRetry },
            )
            is FetchState.Loaded -> OutlinedButton(onClick = viewModel::showSheet, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_show_formats))
            }
        }
    }

    val loaded = state.fetch as? FetchState.Loaded
    if (state.showSheet && loaded != null) {
        val info = loaded.info
        FormatBottomSheet(
            info = info,
            defaultType = settings.defaultTypeFor(info.platform),
            defaultQuality = settings::defaultQuality,
            onDownload = viewModel::download,
            onDismiss = viewModel::dismissSheet,
        )
    }
}

@Composable
private fun CopiedLinkSuggestion(link: ParsedLink, onUse: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        onClick = onUse,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlatformIcon(link.platform, size = 24.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_clipboard_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    ClipboardLinks.displayUrl(link.url),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_clipboard_dismiss))
            }
        }
    }
}

@Composable
private fun SupportedPlatforms(detected: Platform?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.home_supported),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Platform.entries.filter { it != Platform.OTHER }.forEach { platform ->
                PlatformIcon(
                    platform = platform,
                    size = 36.dp,
                    highlighted = detected == null || detected == platform,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Text(
        stringResource(R.string.home_empty_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    )
}

@Composable
private fun LoadingInfo(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Spacer(Modifier.size(12.dp))
        Text(text)
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(message, modifier = Modifier.weight(1f))
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}
