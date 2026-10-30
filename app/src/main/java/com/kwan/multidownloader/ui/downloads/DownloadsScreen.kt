package com.kwan.multidownloader.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kwan.multidownloader.R
import com.kwan.multidownloader.data.db.DownloadEntity
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.Status
import com.kwan.multidownloader.share.ShareIntents
import com.kwan.multidownloader.ui.components.PlatformIcon
import com.kwan.multidownloader.ui.components.errorText
import com.kwan.multidownloader.ui.components.formatSize
import com.kwan.multidownloader.ui.components.topBarHeight
import com.kwan.multidownloader.ui.components.qualityLabel
import com.kwan.multidownloader.ui.components.statusLabel
import com.kwan.multidownloader.work.DownloadNotifications
import org.koin.androidx.compose.koinViewModel

@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    var selected by rememberSaveable { mutableStateOf(DownloadsTab.ACTIVE) }
    val items by viewModel.tabs.getValue(selected).collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
    val hidesSome by viewModel.hidesSome.getValue(selected).collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selecting = selection.isNotEmpty()

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Column(modifier = modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selection.size,
                onClear = viewModel::clearSelection,
                onShare = {
                    val files = items.filter { it.id in selection }.mapNotNull { item ->
                        item.contentUri?.let { it.toUri() to (item.mimeType ?: item.type.mimeType) }
                    }
                    context.startSafely(ShareIntents.shareMultiple(files))
                },
                onDelete = viewModel::deleteSelected,
            )
        }
        PrimaryTabRow(selectedTabIndex = selected.ordinal) {
            DownloadsTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selected,
                    onClick = {
                        selected = tab
                        viewModel.clearSelection()
                    },
                    text = { Text(stringResource(tab.titleRes)) },
                )
            }
        }
        TypeFilterRow(selected = typeFilter, onSelect = viewModel::setTypeFilter)
        if (items.isEmpty()) {
            EmptyTab(selected, filtered = hidesSome)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    // Only finished files can be multi-selected (long-press to start).
                    val selectable = item.status == Status.DONE && item.contentUri != null
                    DownloadRow(
                        item = item,
                        actions = if (selecting) emptyList() else rowActions(item, viewModel, context),
                        selected = if (selecting && selectable) item.id in selection else null,
                        onClick = { if (selecting && selectable) viewModel.toggleSelection(item.id) },
                        onLongClick = { if (selectable) viewModel.toggleSelection(item.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.share_selected, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.share_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
        windowInsets = WindowInsets(0),
        expandedHeight = topBarHeight(),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

/** All / MP3 / MP4 chips; the choice applies to every tab. */
@Composable
private fun TypeFilterRow(selected: MediaType?, onSelect: (MediaType?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(null, MediaType.AUDIO_MP3, MediaType.VIDEO_MP4).forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = {
                    Text(
                        when (type) {
                            null -> stringResource(R.string.filter_all)
                            MediaType.AUDIO_MP3 -> "MP3"
                            MediaType.VIDEO_MP4 -> "MP4"
                        },
                    )
                },
            )
        }
    }
}

/** [filtered]: the tab has downloads, just none of the chosen file type. */
@Composable
private fun EmptyTab(tab: DownloadsTab, filtered: Boolean) {
    val icon = when (tab) {
        DownloadsTab.ACTIVE -> Icons.Filled.Download
        DownloadsTab.COMPLETED -> Icons.Filled.CheckCircle
        DownloadsTab.FAILED -> Icons.Filled.ErrorOutline
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
        Text(
            stringResource(if (filtered) R.string.empty_filtered else tab.emptyRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (tab == DownloadsTab.ACTIVE && !filtered) {
            Text(
                stringResource(R.string.empty_active_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val DownloadsTab.titleRes: Int
    get() = when (this) {
        DownloadsTab.ACTIVE -> R.string.tab_active
        DownloadsTab.COMPLETED -> R.string.tab_completed
        DownloadsTab.FAILED -> R.string.tab_failed
    }

private val DownloadsTab.emptyRes: Int
    get() = when (this) {
        DownloadsTab.ACTIVE -> R.string.empty_active
        DownloadsTab.COMPLETED -> R.string.empty_completed
        DownloadsTab.FAILED -> R.string.empty_failed
    }

private data class RowAction(val icon: ImageVector, val labelRes: Int, val onClick: () -> Unit)

private fun rowActions(item: DownloadEntity, viewModel: DownloadsViewModel, context: Context): List<RowAction> {
    val shareLink = RowAction(Icons.Filled.Link, R.string.action_share_link) {
        context.startSafely(ShareIntents.shareLink(item.sourceUrl, item.title))
    }
    return when (item.status) {
        Status.PAUSED -> listOf(
            RowAction(Icons.Filled.PlayArrow, R.string.action_resume) { viewModel.resume(item.id) },
            RowAction(Icons.Filled.Close, R.string.action_cancel) { viewModel.cancel(item.id) },
        )
        Status.QUEUED, Status.FETCHING, Status.DOWNLOADING, Status.CONVERTING -> listOf(
            RowAction(Icons.Filled.Pause, R.string.action_pause) { viewModel.pause(item.id) },
            RowAction(Icons.Filled.Close, R.string.action_cancel) { viewModel.cancel(item.id) },
        )
        Status.DONE -> listOfNotNull(
            item.contentUri?.let { uri ->
                RowAction(Icons.AutoMirrored.Filled.OpenInNew, R.string.action_open) {
                    context.startSafely(ShareIntents.open(uri.toUri(), item.mimeType ?: item.type.mimeType))
                }
            },
            item.contentUri?.let { uri ->
                RowAction(Icons.Filled.Share, R.string.action_share) {
                    context.startSafely(ShareIntents.share(uri.toUri(), item.mimeType ?: item.type.mimeType, item.title))
                }
            },
            shareLink,
            RowAction(Icons.Filled.Delete, R.string.action_delete) { viewModel.delete(item.id) },
        )
        Status.FAILED, Status.CANCELLED -> listOf(
            RowAction(Icons.Filled.Refresh, R.string.action_retry) { viewModel.retry(item.id) },
            shareLink,
            RowAction(Icons.Filled.Delete, R.string.action_delete) { viewModel.delete(item.id) },
        )
    }
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}

/** [selected] is null outside selection mode (or for rows that can't be selected), else the checkbox state. */
@Composable
private fun DownloadRow(
    item: DownloadEntity,
    actions: List<RowAction>,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) {
                Checkbox(checked = selected, onCheckedChange = null)
            }
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title ?: item.sourceUrl,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlatformIcon(item.platform, size = 18.dp)
                    val format = if (item.type == MediaType.AUDIO_MP3) "MP3" else "MP4"
                    val details = listOfNotNull(
                        "$format ${qualityLabel(item.quality)}",
                        item.sizeBytes?.takeIf { item.status == Status.DONE }?.let { formatSize(context, it) },
                    ).joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (item.status.isActive) {
            ActiveProgress(item)
        } else if (item.status != Status.DONE) {
            Text(
                if (item.status == Status.FAILED) errorText(LocalResources.current, item.errorKind, item.error)
                else statusLabel(item.status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            actions.forEach { action ->
                IconButton(onClick = action.onClick) {
                    Icon(action.icon, contentDescription = stringResource(action.labelRes))
                }
            }
        }
    }
}

@Composable
private fun ActiveProgress(item: DownloadEntity) {
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item.status == Status.DOWNLOADING) {
            LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth())
        } else if (item.status != Status.PAUSED) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        val details = listOfNotNull(
            statusLabel(item.status),
            "${item.progress.toInt()}%".takeIf { item.status == Status.DOWNLOADING || item.status == Status.PAUSED },
            item.speed?.takeIf { item.status == Status.DOWNLOADING },
            item.etaSec?.takeIf { item.status == Status.DOWNLOADING }
                ?.let { stringResource(R.string.eta, DownloadNotifications.formatEta(it)) },
        ).joinToString(" · ")
        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
