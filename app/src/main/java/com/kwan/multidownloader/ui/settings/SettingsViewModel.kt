package com.kwan.multidownloader.ui.settings

import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kwan.multidownloader.R
import com.kwan.multidownloader.data.DownloadRepository
import com.kwan.multidownloader.data.cookies.CookieStore
import com.kwan.multidownloader.data.settings.Settings
import com.kwan.multidownloader.data.settings.SettingsRepository
import com.kwan.multidownloader.domain.Platform
import com.kwan.multidownloader.engine.DownloadEngine
import com.kwan.multidownloader.engine.ExtractorUpdater
import com.kwan.multidownloader.engine.UpdateResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A one-off message for a toast. */
sealed interface SettingsMessage {
    data class Text(@param:StringRes val res: Int, val args: List<Any> = emptyList()) : SettingsMessage
    data class Plural(@param:PluralsRes val res: Int, val quantity: Int) : SettingsMessage
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val downloads: DownloadRepository,
    private val updater: ExtractorUpdater,
    private val engine: DownloadEngine,
    private val cookieStore: CookieStore,
) : ViewModel() {
    val settings: StateFlow<Settings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val lastExtractorCheck: StateFlow<Long> =
        repository.lastExtractorCheck.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _extractorVersion = MutableStateFlow<String?>(null)
    /** Null while it is still the copy bundled in the APK. */
    val extractorVersion: StateFlow<String?> = _extractorVersion.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    val cookiePlatforms: List<Platform> = cookieStore.platforms
    val cookieCounts: StateFlow<Map<Platform, Int>> = cookieStore.counts

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            _extractorVersion.value = engine.version()
            cookieStore.refresh()
        }
    }

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            repository.update { it.copy(wifiOnly = enabled) }
            downloads.applyNetworkPolicy()
        }
    }

    fun updateExtractorNow() {
        if (_updating.value) return
        _updating.value = true
        viewModelScope.launch {
            val message = when (val result = updater.updateNow()) {
                is UpdateResult.Updated -> SettingsMessage.Text(R.string.settings_ytdlp_updated, listOf(result.version))
                is UpdateResult.AlreadyUpToDate -> SettingsMessage.Text(R.string.settings_ytdlp_up_to_date)
                is UpdateResult.Failed -> SettingsMessage.Text(R.string.settings_ytdlp_update_failed)
            }
            _extractorVersion.value = engine.version()
            _updating.value = false
            _messages.send(message)
        }
    }

    fun importCookies(platform: Platform, uri: Uri) {
        viewModelScope.launch {
            val message = when (val result = cookieStore.import(platform, uri)) {
                is CookieStore.ImportResult.Imported ->
                    SettingsMessage.Plural(R.plurals.settings_cookies_imported, result.cookieCount)
                CookieStore.ImportResult.NotNetscape -> SettingsMessage.Text(R.string.settings_cookies_not_netscape)
                CookieStore.ImportResult.NoCookiesForPlatform ->
                    SettingsMessage.Text(R.string.settings_cookies_wrong_site, listOf(platform.displayName))
                CookieStore.ImportResult.TooLarge -> SettingsMessage.Text(R.string.settings_cookies_too_large)
                CookieStore.ImportResult.Unreadable -> SettingsMessage.Text(R.string.settings_cookies_unreadable)
            }
            _messages.send(message)
        }
    }

    fun removeCookies(platform: Platform) {
        viewModelScope.launch {
            cookieStore.remove(platform)
            _messages.send(SettingsMessage.Text(R.string.settings_cookies_removed))
        }
    }
}
