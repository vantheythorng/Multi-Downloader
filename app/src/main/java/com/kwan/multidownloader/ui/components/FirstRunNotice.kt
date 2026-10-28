package com.kwan.multidownloader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kwan.multidownloader.R

/**
 * Shown once, before the first download: the app is for personal use and users must respect
 * copyright and each platform's terms. It can't be dismissed by tapping outside; [onCancel]
 * (share flow only) closes without accepting.
 */
@Composable
fun FirstRunNotice(onAccept: () -> Unit, onCancel: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = onCancel != null, dismissOnClickOutside = false),
        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
        title = { Text(stringResource(R.string.notice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier) {
                Text(stringResource(R.string.notice_personal_use))
                Text(stringResource(R.string.notice_copyright))
                Text(stringResource(R.string.notice_privacy))
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.notice_accept)) } },
        dismissButton = onCancel?.let { cancel -> { TextButton(onClick = cancel) { Text(stringResource(R.string.action_cancel)) } } },
    )
}
