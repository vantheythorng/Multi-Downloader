package com.kwan.multidownloader.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kwan.multidownloader.BuildConfig
import com.kwan.multidownloader.R
import com.kwan.multidownloader.data.settings.SaveLocation
import com.kwan.multidownloader.data.settings.Settings
import com.kwan.multidownloader.data.settings.ThemeMode
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.Platform
import com.kwan.multidownloader.domain.Quality
import com.kwan.multidownloader.ui.components.qualityLabel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is SettingsMessage.Text -> resources.getString(message.res, *message.args.toTypedArray())
                is SettingsMessage.Plural -> resources.getQuantityString(message.res, message.quantity, message.quantity)
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        DownloadSection(settings, viewModel)
        SharingSection(settings, viewModel)
        CookiesSection(viewModel)
        ExtractorSection(settings, viewModel)
        SectionHeader(R.string.settings_background)
        BatteryUsageRow()
        AppearanceSection(settings, viewModel)
        PrivacySection()
        AboutSection(onOpenLicences)
    }
}

@Composable
private fun DownloadSection(settings: Settings, viewModel: SettingsViewModel) {
    SectionHeader(R.string.settings_downloads, divider = false)
    ChoiceRow(
        title = stringResource(R.string.settings_default_format),
        options = listOf(
            MediaType.AUDIO_MP3 to stringResource(R.string.format_audio),
            MediaType.VIDEO_MP4 to stringResource(R.string.format_video),
        ),
        selected = settings.defaultType,
        onSelect = { type -> viewModel.update { it.copy(defaultType = type) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_video_quality),
        options = Quality.options(MediaType.VIDEO_MP4).map { it to qualityLabel(it) },
        selected = settings.defaultVideoQuality,
        onSelect = { quality -> viewModel.update { it.copy(defaultVideoQuality = quality) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_mp3_bitrate),
        options = Quality.AUDIO_BITRATES.map { it to qualityLabel(Quality.audio(it)) },
        selected = settings.mp3Bitrate,
        onSelect = { kbps -> viewModel.update { it.copy(mp3Bitrate = kbps) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_save_location),
        options = listOf(
            SaveLocation.MUSIC_MOVIES to stringResource(R.string.settings_save_music_movies),
            SaveLocation.DOWNLOADS to stringResource(R.string.settings_save_downloads),
        ),
        selected = settings.saveLocation,
        onSelect = { location -> viewModel.update { it.copy(saveLocation = location) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_concurrent),
        options = (Settings.MIN_CONCURRENT..Settings.MAX_CONCURRENT).map { it to it.toString() },
        selected = settings.maxConcurrent,
        onSelect = { count -> viewModel.update { it.copy(maxConcurrent = count) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_wifi_only),
        summary = stringResource(R.string.settings_wifi_only_hint),
        checked = settings.wifiOnly,
        onCheckedChange = viewModel::setWifiOnly,
    )
    SwitchRow(
        title = stringResource(R.string.settings_embed_thumbnail),
        summary = stringResource(R.string.settings_embed_thumbnail_hint),
        checked = settings.embedThumbnail,
        onCheckedChange = { on -> viewModel.update { it.copy(embedThumbnail = on) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_embed_metadata),
        summary = stringResource(R.string.settings_embed_metadata_hint),
        checked = settings.embedMetadata,
        onCheckedChange = { on -> viewModel.update { it.copy(embedMetadata = on) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_aria2c),
        summary = stringResource(R.string.settings_aria2c_hint),
        checked = settings.useAria2c,
        onCheckedChange = { on -> viewModel.update { it.copy(useAria2c = on) } },
    )
}

@Composable
private fun SharingSection(settings: Settings, viewModel: SettingsViewModel) {
    SectionHeader(R.string.settings_sharing)
    ChoiceRow(
        title = stringResource(R.string.settings_quick_download),
        options = listOf(
            null to stringResource(R.string.settings_quick_ask),
            MediaType.AUDIO_MP3 to stringResource(R.string.settings_quick_mp3),
            MediaType.VIDEO_MP4 to stringResource(R.string.settings_quick_video),
        ),
        selected = settings.quickDownload,
        onSelect = { type -> viewModel.update { it.copy(quickDownload = type) } },
    )
    Hint(stringResource(R.string.settings_quick_hint))
}

@Composable
private fun CookiesSection(viewModel: SettingsViewModel) {
    val counts by viewModel.cookieCounts.collectAsStateWithLifecycle()
    // Which platform the file picker was opened for; survives the picker recreating the activity.
    var pickingFor by rememberSaveable { mutableStateOf<Platform?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val platform = pickingFor
        pickingFor = null
        if (uri != null && platform != null) viewModel.importCookies(platform, uri)
    }

    SectionHeader(R.string.settings_cookies)
    Hint(stringResource(R.string.settings_cookies_hint))
    viewModel.cookiePlatforms.forEach { platform ->
        val count = counts[platform]
        ListItem(
            headlineContent = {
                Text(
                    if (platform == Platform.YOUTUBE) stringResource(R.string.settings_cookies_youtube) else platform.displayName
                )
            },
            supportingContent = {
                Text(
                    if (count == null) stringResource(R.string.settings_cookies_none)
                    else pluralStringResource(R.plurals.settings_cookies_count, count, count)
                )
            },
            leadingContent = { Icon(Icons.Filled.Cookie, contentDescription = null) },
            trailingContent = {
                if (count != null) {
                    IconButton(onClick = { viewModel.removeCookies(platform) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_cookies_remove))
                    }
                }
            },
            modifier = Modifier.clickable {
                pickingFor = platform
                // Exported cookies.txt files often have no text/plain type, so allow any file.
                picker.launch(arrayOf("text/plain", "*/*"))
            },
        )
    }
}

@Composable
private fun ExtractorSection(settings: Settings, viewModel: SettingsViewModel) {
    val version by viewModel.extractorVersion.collectAsStateWithLifecycle()
    val lastCheck by viewModel.lastExtractorCheck.collectAsStateWithLifecycle()
    val updating by viewModel.updating.collectAsStateWithLifecycle()

    SectionHeader(R.string.settings_ytdlp)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_ytdlp_update_now)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_ytdlp_version, version ?: stringResource(R.string.settings_ytdlp_bundled)))
                Text(
                    if (lastCheck == 0L) stringResource(R.string.settings_ytdlp_never_checked)
                    else stringResource(
                        R.string.settings_ytdlp_last_checked,
                        DateUtils.getRelativeTimeSpanString(lastCheck, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                    )
                )
            }
        },
        leadingContent = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        trailingContent = {
            if (updating) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        },
        modifier = Modifier.clickable(enabled = !updating, onClick = viewModel::updateExtractorNow),
    )
    SwitchRow(
        title = stringResource(R.string.settings_ytdlp_auto),
        summary = stringResource(R.string.settings_ytdlp_auto_hint),
        checked = settings.autoUpdateExtractor,
        onCheckedChange = { on -> viewModel.update { it.copy(autoUpdateExtractor = on) } },
    )
}

@Composable
private fun AppearanceSection(settings: Settings, viewModel: SettingsViewModel) {
    SectionHeader(R.string.settings_appearance)
    ChoiceRow(
        title = stringResource(R.string.settings_theme),
        options = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
        ),
        selected = settings.theme,
        onSelect = { theme -> viewModel.update { it.copy(theme = theme) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_compact_bars),
        summary = stringResource(R.string.settings_compact_bars_hint),
        checked = settings.compactBars,
        onCheckedChange = { on -> viewModel.update { it.copy(compactBars = on) } },
    )
    // Wallpaper colours only exist on Android 12+.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchRow(
            title = stringResource(R.string.settings_dynamic_color),
            summary = stringResource(R.string.settings_dynamic_color_hint),
            checked = settings.dynamicColor,
            onCheckedChange = { on -> viewModel.update { it.copy(dynamicColor = on) } },
        )
    }
}

/** Plain-language summary of what the app does with data; keep it in line with the backup rules and network use. */
@Composable
private fun PrivacySection() {
    SectionHeader(R.string.settings_privacy)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_privacy_title)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf(
                    R.string.settings_privacy_no_collection,
                    R.string.settings_privacy_local,
                    R.string.settings_privacy_cookies,
                    R.string.settings_privacy_network,
                ).forEach { Text("• " + stringResource(it)) }
            }
        },
        leadingContent = { Icon(Icons.Filled.PrivacyTip, contentDescription = null) },
    )
}

@Composable
private fun AboutSection(onOpenLicences: () -> Unit) {
    val context = LocalContext.current
    SectionHeader(R.string.settings_about)
    ListItem(
        headlineContent = { Text(stringResource(R.string.app_name)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                Text(stringResource(R.string.settings_about_notice))
            }
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_about_developer)) },
        supportingContent = { Text(stringResource(R.string.settings_about_developer_name)) },
        leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.licences_title)) },
        supportingContent = { Text(stringResource(R.string.licences_summary)) },
        leadingContent = { Icon(Icons.Filled.Gavel, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onOpenLicences),
    )
    val sourceUrl = stringResource(R.string.settings_about_source_url)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_about_source)) },
        supportingContent = { Text(stringResource(R.string.settings_about_source_label)) },
        leadingContent = { Icon(Icons.Filled.Code, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        modifier = Modifier.clickable {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, sourceUrl.toUri()))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

// ---- Rows ----

@Composable
private fun SectionHeader(titleRes: Int, divider: Boolean = true) {
    if (divider) HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

/** Shows the current choice; tapping opens a dialog with all [options]. */
@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(options.firstOrNull { it.first == selected }?.second.orEmpty()) },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(value)
                                        open = false
                                    },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.share_close)) } },
        )
    }
}

@Composable
private fun BatteryUsageRow() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(BatteryUsage.mode(context)) }
    // The user changes this in a system dialog or settings page; re-read it when they come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { mode = BatteryUsage.mode(context) }

    val (statusRes, hintRes) = when (mode) {
        BatteryMode.UNRESTRICTED -> R.string.settings_battery_unrestricted to R.string.settings_battery_hint_unrestricted
        BatteryMode.OPTIMIZED -> R.string.settings_battery_optimized to R.string.settings_battery_hint_optimized
        BatteryMode.RESTRICTED -> R.string.settings_battery_restricted to R.string.settings_battery_hint_restricted
    }
    val icon: ImageVector = if (mode == BatteryMode.UNRESTRICTED) Icons.Filled.BatteryFull else Icons.Filled.BatteryAlert
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_battery)) },
        supportingContent = {
            Column {
                Text(
                    stringResource(statusRes),
                    color = if (mode == BatteryMode.UNRESTRICTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(stringResource(hintRes))
            }
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable {
            try {
                context.startActivity(BatteryUsage.intentFor(context, mode))
            } catch (e: ActivityNotFoundException) {
                // Some OEM builds drop the request dialog; the app info page always exists.
                context.startActivity(BatteryUsage.appDetails(context))
            }
        },
    )
}
