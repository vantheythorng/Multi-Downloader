package com.kwan.multidownloader.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kwan.multidownloader.R

/** The "Compact top and bottom bars" setting, provided at the app root for every screen's bars. */
val LocalCompactBars = staticCompositionLocalOf { true }

/** Material 2 app bar height; Material 3's top app bar is 64dp. */
val CompactBarHeight: Dp = 56.dp

/** Top bar height for the current mode: the only thing the compact setting changes about top bars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun topBarHeight(): Dp = if (LocalCompactBars.current) CompactBarHeight else TopAppBarDefaults.TopAppBarExpandedHeight

/** Screen title bar: a fixed, non-collapsing single row, 56dp compact or 64dp standard. [onBack] adds an up arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                }
            }
        },
        expandedHeight = topBarHeight(),
    )
}
