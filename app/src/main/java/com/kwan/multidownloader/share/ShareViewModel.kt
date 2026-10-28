package com.kwan.multidownloader.share

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kwan.multidownloader.R
import com.kwan.multidownloader.data.DownloadRepository
import com.kwan.multidownloader.data.settings.Settings
import com.kwan.multidownloader.data.settings.SettingsRepository
import com.kwan.multidownloader.domain.MediaInfo
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.ParsedLink
import com.kwan.multidownloader.domain.UrlParser
import com.kwan.multidownloader.engine.EngineException
import com.kwan.multidownloader.engine.MediaInfoFetcher
import com.kwan.multidownloader.ui.components.messageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ShareState {
    /** Deciding between quick download and the sheet; nothing is drawn yet so quick download never flashes a sheet. */
    data object Pending : ShareState
    /** First use of the app is a share: the copyright notice comes before anything is queued. */
    data object NeedsNotice : ShareState
    data object Loading : ShareState
    data object UpdatingExtractor : ShareState
    data class Ready(val info: MediaInfo) : ShareState
    data class Error(val message: String?, @param:StringRes val messageRes: Int? = null, val canRetry: Boolean) : ShareState
}

sealed interface ShareEvent {
    /** From the sheet's Download button. */
    data object Enqueued : ShareEvent
    /** Queued without the sheet ("Always download as …" or a sharing shortcut). */
    data class QuickEnqueued(val type: MediaType) : ShareEvent
}

class ShareViewModel(
    private val fetcher: MediaInfoFetcher,
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ShareState>(ShareState.Pending)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    val settings: StateFlow<Settings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _events = Channel<ShareEvent>(Channel.BUFFERED)
    val events: Flow<ShareEvent> = _events.receiveAsFlow()

    private var url: String? = null
    private var fetchJob: Job? = null
    /** The share waiting behind the first-run notice. */
    private var pending: Pair<ParsedLink, MediaType?>? = null

    /**
     * Shared text often has a title before the link, so take the first URL in it.
     * [forcedType] comes from a sharing shortcut ("Download MP3" / "Download Video") and always skips the sheet.
     */
    fun onShared(text: String?, forcedType: MediaType? = null) {
        val link = text?.let(UrlParser::parse)
        if (link == null) {
            fetchJob?.cancel()
            url = null
            _state.value = ShareState.Error(null, R.string.share_no_link, canRetry = false)
            return
        }
        if (forcedType == null && link.url == url && _state.value !is ShareState.Error) return
        url = link.url
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val current = settingsRepository.current()
            if (!current.noticeAccepted) {
                pending = link to forcedType
                _state.value = ShareState.NeedsNotice
                return@launch
            }
            proceed(link, forcedType, current)
        }
    }

    fun acceptNotice() {
        val (link, forcedType) = pending ?: return
        pending = null
        fetchJob = viewModelScope.launch {
            settingsRepository.update { it.copy(noticeAccepted = true) }
            _state.value = ShareState.Pending
            proceed(link, forcedType, settingsRepository.current())
        }
    }

    /** Quick download (setting or shortcut) queues straight away; otherwise fetch info for the sheet. */
    private suspend fun proceed(link: ParsedLink, forcedType: MediaType?, current: Settings) {
        val quickType = forcedType ?: current.quickDownload
        if (quickType != null) {
            repository.enqueue(link, quickType, current.defaultQuality(quickType))
            _events.send(ShareEvent.QuickEnqueued(quickType))
        } else {
            load()
        }
    }

    fun retry() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch { load() }
    }

    fun download(type: MediaType, quality: String) {
        val info = (_state.value as? ShareState.Ready)?.info ?: return
        viewModelScope.launch {
            repository.enqueue(info, type, quality)
            _events.send(ShareEvent.Enqueued)
        }
    }

    private suspend fun load() {
        val target = url ?: return
        _state.value = ShareState.Loading
        _state.value = try {
            ShareState.Ready(fetcher.fetch(target) { _state.value = ShareState.UpdatingExtractor })
        } catch (e: EngineException) {
            ShareState.Error(e.message, e.kind.messageRes(), canRetry = true)
        }
    }
}
