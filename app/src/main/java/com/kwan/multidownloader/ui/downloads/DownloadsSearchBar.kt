package com.kwan.multidownloader.ui.downloads

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kwan.multidownloader.R
import com.kwan.multidownloader.data.db.DownloadEntity
import kotlinx.coroutines.launch

/**
 * Downloads' top bar. Tapping it opens a full-screen search page (typing only works there) that
 * lists matching downloads from every tab as [resultRow]s; leaving the page clears the search.
 * [actions] sit after the bar, outside the search page.
 *
 * Always 64dp, even with compact bars: the field's own text padding needs a 56dp field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSearchBar(
    onQueryChange: (String) -> Unit,
    results: List<DownloadEntity>,
    actions: @Composable RowScope.() -> Unit = {},
    resultRow: @Composable (DownloadEntity) -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { currentOnQueryChange(it) }
    }
    // Each search starts fresh.
    LaunchedEffect(searchBarState.currentValue) {
        if (searchBarState.currentValue == SearchBarValue.Collapsed) textFieldState.clearText()
    }

    // Shared by the bar and the search page; read inside, so it gets the page's window when expanded.
    val inputField = @Composable {
        val focusManager = LocalFocusManager.current
        val expanded = searchBarState.currentValue == SearchBarValue.Expanded
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            // Results update as you type, so the keyboard's Search key just closes the keyboard.
            onSearch = { focusManager.clearFocus() },
            placeholder = { Text(stringResource(R.string.search_downloads)) },
            leadingIcon = {
                if (expanded) {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                    }
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            trailingIcon = if (textFieldState.text.isNotEmpty()) {
                {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            } else {
                null
            },
        )
    }

    AppBarWithSearch(
        state = searchBarState,
        inputField = inputField,
        actions = actions,
        // The app's Scaffold already pads for the status bar.
        windowInsets = WindowInsets(0),
    )
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        if (results.isEmpty()) {
            if (textFieldState.text.isNotBlank()) {
                Text(
                    stringResource(R.string.empty_search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        } else {
            LazyColumn {
                items(results, key = { it.id }) { resultRow(it) }
            }
        }
    }
}
