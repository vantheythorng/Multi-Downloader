package com.kwan.multidownloader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.Platform
import com.kwan.multidownloader.domain.Quality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class SaveLocation { MUSIC_MOVIES, DOWNLOADS }

/** How a saved file is named: the shortened title, or the media ID. */
enum class FileNameStyle { TITLE, ID }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val defaultType: MediaType = MediaType.VIDEO_MP4,
    val defaultVideoQuality: String = Quality.BEST,
    val mp3Bitrate: Int = 192,
    val saveLocation: SaveLocation = SaveLocation.MUSIC_MOVIES,
    val fileNameStyle: FileNameStyle = FileNameStyle.TITLE,
    val maxConcurrent: Int = 2,
    val wifiOnly: Boolean = false,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val useAria2c: Boolean = false,
    /** "Always download as …": a shared link is queued in this format without the sheet; null = ask. */
    val quickDownload: MediaType? = null,
    /** On opening the app, offer a supported link found on the clipboard. */
    val suggestCopiedLinks: Boolean = true,
    /** Daily background yt-dlp update check; the update-and-retry on "extractor outdated" runs regardless. */
    val autoUpdateExtractor: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Material You colours from the wallpaper (Android 12+); off by default so the app keeps its own colours. */
    val dynamicColor: Boolean = false,
    /** 56dp Material 2-height top and bottom bars instead of Material 3's taller ones. */
    val compactBars: Boolean = true,
    /** The first-run "personal use, respect copyright" notice was acknowledged. */
    val noticeAccepted: Boolean = false,
) {
    /** YouTube Music defaults to audio; everything else uses the saved default. */
    fun defaultTypeFor(platform: Platform): MediaType =
        if (platform == Platform.YT_MUSIC) MediaType.AUDIO_MP3 else defaultType

    fun defaultQuality(type: MediaType): String = when (type) {
        MediaType.AUDIO_MP3 -> Quality.audio(mp3Bitrate)
        MediaType.VIDEO_MP4 -> defaultVideoQuality
    }

    companion object {
        const val MIN_CONCURRENT = 1
        const val MAX_CONCURRENT = 3
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** DataStore-backed preferences, edited on the Settings screen. */
class SettingsRepository(context: Context) {
    private val store: DataStore<Preferences> = context.settingsDataStore

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.DEFAULT_TYPE] = next.defaultType.name
            prefs[Keys.DEFAULT_VIDEO_QUALITY] = next.defaultVideoQuality
            prefs[Keys.MP3_BITRATE] = next.mp3Bitrate
            prefs[Keys.SAVE_LOCATION] = next.saveLocation.name
            prefs[Keys.FILE_NAME_STYLE] = next.fileNameStyle.name
            prefs[Keys.MAX_CONCURRENT] = next.maxConcurrent.coerceIn(Settings.MIN_CONCURRENT, Settings.MAX_CONCURRENT)
            prefs[Keys.WIFI_ONLY] = next.wifiOnly
            prefs[Keys.EMBED_THUMBNAIL] = next.embedThumbnail
            prefs[Keys.EMBED_METADATA] = next.embedMetadata
            prefs[Keys.USE_ARIA2C] = next.useAria2c
            prefs[Keys.QUICK_DOWNLOAD] = next.quickDownload?.name ?: QUICK_DOWNLOAD_OFF
            prefs[Keys.SUGGEST_COPIED_LINKS] = next.suggestCopiedLinks
            prefs[Keys.AUTO_UPDATE_EXTRACTOR] = next.autoUpdateExtractor
            prefs[Keys.THEME] = next.theme.name
            prefs[Keys.DYNAMIC_COLOR] = next.dynamicColor
            prefs[Keys.COMPACT_BARS] = next.compactBars
            prefs[Keys.NOTICE_ACCEPTED] = next.noticeAccepted
        }
    }

    /** Epoch millis of the last yt-dlp update check, 0 if never. */
    val lastExtractorCheck: Flow<Long> = store.data.map { it[Keys.LAST_EXTRACTOR_CHECK] ?: 0L }

    suspend fun lastExtractorCheck(): Long = lastExtractorCheck.first()

    suspend fun setLastExtractorCheck(epochMillis: Long) {
        store.edit { it[Keys.LAST_EXTRACTOR_CHECK] = epochMillis }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            defaultType = enumOrNull<MediaType>(this[Keys.DEFAULT_TYPE]) ?: defaults.defaultType,
            defaultVideoQuality = this[Keys.DEFAULT_VIDEO_QUALITY] ?: defaults.defaultVideoQuality,
            mp3Bitrate = this[Keys.MP3_BITRATE] ?: defaults.mp3Bitrate,
            saveLocation = enumOrNull<SaveLocation>(this[Keys.SAVE_LOCATION]) ?: defaults.saveLocation,
            fileNameStyle = enumOrNull<FileNameStyle>(this[Keys.FILE_NAME_STYLE]) ?: defaults.fileNameStyle,
            maxConcurrent = (this[Keys.MAX_CONCURRENT] ?: defaults.maxConcurrent).coerceIn(Settings.MIN_CONCURRENT, Settings.MAX_CONCURRENT),
            wifiOnly = this[Keys.WIFI_ONLY] ?: defaults.wifiOnly,
            embedThumbnail = this[Keys.EMBED_THUMBNAIL] ?: defaults.embedThumbnail,
            embedMetadata = this[Keys.EMBED_METADATA] ?: defaults.embedMetadata,
            useAria2c = this[Keys.USE_ARIA2C] ?: defaults.useAria2c,
            quickDownload = enumOrNull<MediaType>(this[Keys.QUICK_DOWNLOAD]),
            suggestCopiedLinks = this[Keys.SUGGEST_COPIED_LINKS] ?: defaults.suggestCopiedLinks,
            autoUpdateExtractor = this[Keys.AUTO_UPDATE_EXTRACTOR] ?: defaults.autoUpdateExtractor,
            theme = enumOrNull<ThemeMode>(this[Keys.THEME]) ?: defaults.theme,
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            compactBars = this[Keys.COMPACT_BARS] ?: defaults.compactBars,
            noticeAccepted = this[Keys.NOTICE_ACCEPTED] ?: defaults.noticeAccepted,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private object Keys {
        val DEFAULT_TYPE = stringPreferencesKey("default_type")
        val DEFAULT_VIDEO_QUALITY = stringPreferencesKey("default_video_quality")
        val MP3_BITRATE = intPreferencesKey("mp3_bitrate")
        val SAVE_LOCATION = stringPreferencesKey("save_location")
        val FILE_NAME_STYLE = stringPreferencesKey("file_name_style")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val EMBED_THUMBNAIL = booleanPreferencesKey("embed_thumbnail")
        val EMBED_METADATA = booleanPreferencesKey("embed_metadata")
        val USE_ARIA2C = booleanPreferencesKey("use_aria2c")
        val LAST_EXTRACTOR_CHECK = longPreferencesKey("last_extractor_check")
        val QUICK_DOWNLOAD = stringPreferencesKey("quick_download")
        val SUGGEST_COPIED_LINKS = booleanPreferencesKey("suggest_copied_links")
        val AUTO_UPDATE_EXTRACTOR = booleanPreferencesKey("auto_update_extractor")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        // Key predates the top bar joining this setting; kept so saved choices carry over.
        val COMPACT_BARS = booleanPreferencesKey("compact_navigation")
        val NOTICE_ACCEPTED = booleanPreferencesKey("notice_accepted")
    }

    private companion object {
        const val QUICK_DOWNLOAD_OFF = "OFF"
    }
}
