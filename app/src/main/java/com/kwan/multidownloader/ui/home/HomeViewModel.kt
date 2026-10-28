package com.kwan.multidownloader.ui.home

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
import com.kwan.multidownloader.domain.Platform
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FetchState {
    data object Empty : FetchState
    data object Loading : FetchState
    data object UpdatingExtractor : FetchState
    data class Loaded(val info: MediaInfo) : FetchState
    /** [messageRes] wins over [message]; [canRetry] is false when retrying the same text can't help (no link). */
    data class Error(val message: String?, @param:StringRes val messageRes: Int? = null, val canRetry: Boolean = true) : FetchState
}

data class HomeUiState(
    val url: String = "",
    val link: ParsedLink? = null,
    val fetch: FetchState = FetchState.Empty,
    val showSheet: Boolean = false,
) {
    val platform: Platform? get() = link?.platform
}

sealed interface HomeEvent {
    data object Enqueued : HomeEvent
}

class HomeViewModel(
    private val fetcher: MediaInfoFetcher,
    private val repository: DownloadRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val settings: StateFlow<Settings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private var fetchJob: Job? = null
    private var warmedUp = false

    fun onUrlChange(text: String) {
        _state.update { it.copy(url = text, link = UrlParser.parse(text)) }
        warmUp()
    }

    fun clear() {
        fetchJob?.cancel()
        _state.value = HomeUiState()
    }

    fun fetch() {
        val link = UrlParser.parse(_state.value.url)
        if (link == null) {
            _state.update { it.copy(fetch = FetchState.Error(null, R.string.home_no_link, canRetry = false)) }
            return
        }
        fetchJob?.cancel()
        _state.update { it.copy(url = link.url, link = link, fetch = FetchState.Loading, showSheet = false) }
        fetchJob = viewModelScope.launch {
            val result = try {
                FetchState.Loaded(
                    fetcher.fetch(link.url) { _state.update { it.copy(fetch = FetchState.UpdatingExtractor) } }
                )
            } catch (e: EngineException) {
                FetchState.Error(e.message, e.kind.messageRes())
            }
            _state.update { it.copy(fetch = result, showSheet = result is FetchState.Loaded) }
        }
    }

    fun showSheet() = _state.update { it.copy(showSheet = it.fetch is FetchState.Loaded) }

    fun dismissSheet() = _state.update { it.copy(showSheet = false) }

    fun download(type: MediaType, quality: String) {
        val info = (_state.value.fetch as? FetchState.Loaded)?.info ?: return
        _state.update { it.copy(showSheet = false) }
        viewModelScope.launch {
            repository.enqueue(info, type, quality)
            _events.send(HomeEvent.Enqueued)
        }
    }

    /** Starts the Python runtime while the user is still typing, so the first fetch is faster. */
    private fun warmUp() {
        if (warmedUp) return
        warmedUp = true
        viewModelScope.launch { fetcher.warmUp() }
    }
}
