package com.kwan.multidownloader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kwan.multidownloader.R
import com.kwan.multidownloader.domain.MediaInfo
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.Quality
import com.kwan.multidownloader.domain.SizeEstimator

/** Media info + format picker, shared by Home and (in M2) the share receiver. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatBottomSheet(
    info: MediaInfo,
    defaultType: MediaType,
    defaultQuality: (MediaType) -> String,
    onDownload: (MediaType, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        FormatSheetContent(info, defaultType, defaultQuality, onDownload)
    }
}

/** The sheet's body, for hosts (like the share receiver) that put their own states in the same sheet. */
@Composable
fun FormatSheetContent(
    info: MediaInfo,
    defaultType: MediaType,
    defaultQuality: (MediaType) -> String,
    onDownload: (MediaType, String) -> Unit,
) {
    val context = LocalContext.current
    var type by rememberSaveable(info.id) { mutableStateOf(defaultType) }
    var quality by rememberSaveable(info.id, type) { mutableStateOf(defaultQuality(type)) }
    val estimate = remember(info, type, quality) { SizeEstimator.estimate(info, type, quality) }

    // Scrolls so a long, expanded description never pushes the Download button off screen.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = info.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(info.uploader, info.durationSec?.let(::formatDuration)).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlatformIcon(info.platform, size = 20.dp)
                    Text(info.platform.displayName, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        info.extraDescription?.let { ExpandableText(it) }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MediaType.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = type == option,
                    onClick = { type = option },
                    shape = SegmentedButtonDefaults.itemShape(index, MediaType.entries.size),
                ) {
                    Text(stringResource(if (option == MediaType.AUDIO_MP3) R.string.format_audio else R.string.format_video))
                }
            }
        }

        QualityDropdown(type = type, quality = quality, onSelect = { quality = it })

        Text(
            text = estimate?.let { stringResource(R.string.format_size_estimate, formatSize(context, it)) }
                ?: stringResource(R.string.format_size_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = { onDownload(type, quality) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.format_download))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityDropdown(type: MediaType, quality: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = qualityLabel(quality),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.format_quality)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Quality.options(type).forEach { option ->
                DropdownMenuItem(
                    text = { Text(qualityLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
