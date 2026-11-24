package com.kwan.multidownloader.ui.downloads

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxWidth
import com.kwan.multidownloader.R
import com.kwan.multidownloader.data.RenameResult

/** Edits a finished download's file name; the extension stays as it is. */
@Composable
fun RenameDialog(
    state: RenameState,
    onConfirm: (String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(state.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.name, selection = TextRange(0, state.name.length)))
    }
    val focus = remember { FocusRequester() }
    val canSave = value.text.isNotBlank() && !state.saving && state.error != RenameResult.MISSING

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        // Only Cancel or Save closes it, not a tap outside or Back.
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            // The dialog is its own window, so its focus manager has to be read in here.
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = value,
                onValueChange = {
                    if (it.text != value.text && state.error != null) onEdit()
                    value = it
                },
                label = { Text(stringResource(R.string.rename_label)) },
                suffix = state.extension.takeIf { it.isNotEmpty() }?.let { ext -> { Text(".$ext") } },
                singleLine = true,
                isError = state.error != null,
                supportingText = state.error?.let { error -> { Text(stringResource(error.messageRes)) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                // Done clears focus, which also closes the keyboard; the dialog's Save button renames.
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = canSave) { Text(stringResource(R.string.rename_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val RenameResult.messageRes: Int
    get() = when (this) {
        RenameResult.INVALID -> R.string.rename_error_invalid
        RenameResult.EXISTS -> R.string.rename_error_exists
        RenameResult.MISSING -> R.string.rename_error_missing
        RenameResult.NOT_ALLOWED -> R.string.rename_error_not_allowed
        RenameResult.RENAMED, RenameResult.UNCHANGED, RenameResult.FAILED -> R.string.rename_error_failed
    }
