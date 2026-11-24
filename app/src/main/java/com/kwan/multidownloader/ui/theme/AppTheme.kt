package com.kwan.multidownloader.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kwan.multidownloader.data.settings.Settings
import com.kwan.multidownloader.data.settings.SettingsRepository
import com.kwan.multidownloader.data.settings.ThemeMode
import org.koin.compose.koinInject

/** [MultiDownloaderTheme] driven by the Theme and Dynamic colour settings; used by every activity. */
@Composable
fun AppTheme(
    settingsRepository: SettingsRepository = koinInject(),
    content: @Composable () -> Unit,
) {
    // Null for the few ms before DataStore loads; follow the system meanwhile.
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val dark = when (settings?.theme ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // enableEdgeToEdge() follows the system theme by default; match the app's so bar icons stay readable.
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, dark) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    MultiDownloaderTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: Settings().dynamicColor, content = content)
}
