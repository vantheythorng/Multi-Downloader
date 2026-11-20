package com.kwan.multidownloader.ui.downloads

import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kwan.multidownloader.data.AudioClipper
import com.kwan.multidownloader.data.DownloadRepository
import com.kwan.multidownloader.data.RenameResult
import com.kwan.multidownloader.data.db.DownloadEntity
import com.kwan.multidownloader.domain.FileNames
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class DownloadsTab(val statuses: Set<Status>) {
    ACTIVE(Status.ACTIVE),
    COMPLETED(Status.COMPLETED),
    FAILED(Status.FAILED_OR_CANCELLED),
}

/**
 * The open Clip audio sheet for [item]. [durationMs] is 0 until known; [progress] is non-null while
 * the clip is being made; [failed] is set when the last try didn't work.
 */
data class ClipState(
    val item: DownloadEntity,
    val durationMs: Long,
    val startMs: Long,
    val endMs: Long,
    val progress: Float? = null,
    val failed: Boolean = false,
) {
    val lengthMs: Long get() = endMs - startMs
    val canSend: Boolean get() = progress == null && durationMs > 0 && lengthMs >= MIN_CLIP_MS

    companion object {
        const val MIN_CLIP_MS = 1_000L
        /** The range a new clip starts with, from the beginning. */
        const val DEFAULT_CLIP_MS = 30_000L
    }
}

/** A finished clip, ready for the share sheet. */
data class ClipReady(val file: File, val title: String?)

/** [name] is the file name without its [extension]; [error] is the last failed attempt, if any. */
data class RenameState(
    val id: String,
    val name: String,
    val extension: String,
    val error: RenameResult? = null,
    val saving: Boolean = false,
)

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val clipper: AudioClipper,
) : ViewModel() {

    private val _typeFilter = MutableStateFlow<MediaType?>(null)
    /** File type shown on every tab; null shows all. */
    val typeFilter: StateFlow<MediaType?> = _typeFilter.asStateFlow()

    private val _query = MutableStateFlow("")

    fun setQuery(text: String) {
        _query.value = text
    }

    /** Downloads on any tab matching the search page's text, newest first; none while it's blank. */
    val searchResults: StateFlow<List<DownloadEntity>> =
        combine(repository.observeAll(), _query) { all, query ->
            val words = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }
            if (words.isEmpty()) emptyList() else all.filter { it.matches(words) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every download per tab, before the type filter. */
    private val unfiltered: Map<DownloadsTab, StateFlow<List<DownloadEntity>>> = DownloadsTab.entries.associateWith { tab ->
        repository.observe(tab.statuses).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    val tabs: Map<DownloadsTab, StateFlow<List<DownloadEntity>>> = unfiltered.mapValues { (_, items) ->
        combine(items, _typeFilter) { all, type ->
            if (type == null) all else all.filter { it.type == type }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /** Whether the type filter hides downloads on a tab, so an empty list can say so. */
    val hidesSome: Map<DownloadsTab, StateFlow<Boolean>> = unfiltered.mapValues { (tab, items) ->
        combine(items, tabs.getValue(tab)) { all, shown -> all.size > shown.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    }

    /** Also clears the selection, so bulk share/delete never acts on rows the filter hides. */
    fun setTypeFilter(type: MediaType?) {
        _typeFilter.value = type
        clearSelection()
    }

    /** Multi-select on the Completed tab, for bulk share or delete. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())

    /** Ids still on the Completed tab; anything deleted meanwhile drops out. */
    val selection: StateFlow<Set<String>> =
        combine(_selection, tabs.getValue(DownloadsTab.COMPLETED)) { ids, completed ->
            ids intersect completed.mapTo(HashSet()) { it.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleSelection(id: String) = _selection.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun deleteSelected() {
        val ids = selection.value
        clearSelection()
        launch { ids.forEach { repository.delete(it, deleteFile = true) } }
    }

    private val _rename = MutableStateFlow<RenameState?>(null)
    /** The open Rename dialog, or null when it's closed. */
    val rename: StateFlow<RenameState?> = _rename.asStateFlow()

    fun startRename(id: String) = launch {
        val current = repository.fileName(id)
        if (current == null) {
            _rename.value = RenameState(id, name = "", extension = "", error = RenameResult.MISSING)
            return@launch
        }
        val dot = current.lastIndexOf('.')
        _rename.value = if (dot > 0) {
            RenameState(id, name = current.substring(0, dot), extension = current.substring(dot + 1))
        } else {
            RenameState(id, name = current, extension = "")
        }
    }

    fun confirmRename(newName: String) {
        val state = _rename.value ?: return
        if (state.saving) return
        _rename.value = state.copy(saving = true, error = null)
        launch {
            val result = repository.rename(state.id, newName)
            _rename.update { open ->
                when {
                    open?.id != state.id -> open
                    result == RenameResult.RENAMED || result == RenameResult.UNCHANGED -> null
                    else -> open.copy(saving = false, error = result)
                }
            }
        }
    }

    fun clearRenameError() = _rename.update { it?.copy(error = null) }

    fun dismissRename() {
        _rename.value = null
    }

    private val _clip = MutableStateFlow<ClipState?>(null)
    /** The open Clip audio sheet, or null when it's closed. */
    val clip: StateFlow<ClipState?> = _clip.asStateFlow()

    private val _clipReady = Channel<ClipReady>(Channel.BUFFERED)
    /** Each finished clip, once, for the screen to share. */
    val clipReady: Flow<ClipReady> = _clipReady.receiveAsFlow()

    private var clipJob: Job? = null

    fun startClip(item: DownloadEntity) {
        if (item.contentUri == null) return
        val duration = (item.durationSec ?: 0) * 1000L
        _clip.value = ClipState(item, duration, startMs = 0, endMs = minOf(duration, ClipState.DEFAULT_CLIP_MS))
    }

    /** The player's exact length replaces the one from the site; the range stays inside it. */
    fun setClipDuration(durationMs: Long) = _clip.update { state ->
        when {
            state == null || durationMs <= 0 || durationMs == state.durationMs -> state
            state.durationMs == 0L -> state.copy(durationMs = durationMs, startMs = 0, endMs = minOf(durationMs, ClipState.DEFAULT_CLIP_MS))
            else -> {
                val end = state.endMs.coerceAtMost(durationMs)
                state.copy(durationMs = durationMs, startMs = state.startMs.coerceAtMost(end), endMs = end)
            }
        }
    }

    /** Snapped to whole seconds (the very end excepted), so the labels show exactly what's sent. */
    fun setClipRange(startMs: Long, endMs: Long) = _clip.update { state ->
        if (state == null || state.progress != null) return@update state
        fun snap(ms: Long) = ((ms + 500) / 1000 * 1000).coerceIn(0, state.durationMs)
        val end = if (endMs >= state.durationMs) state.durationMs else snap(endMs)
        state.copy(startMs = snap(startMs).coerceAtMost(end), endMs = end, failed = false)
    }

    fun sendClip() {
        val state = _clip.value?.takeIf { it.canSend } ?: return
        val uri = state.item.contentUri?.toUri() ?: return
        _clip.value = state.copy(progress = 0f, failed = false)
        clipJob = viewModelScope.launch {
            try {
                // Same name as the saved file, as an .m4a; the title only if that file can't be read.
                val name = repository.fileName(state.item.id)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                    ?.let { "$it.$CLIP_EXTENSION" }
                    ?: FileNames.forTitle(state.item.title, fallback = "Clip", extension = CLIP_EXTENSION)
                val file = clipper.clip(
                    source = uri,
                    hasVideo = state.item.type == MediaType.VIDEO_MP4,
                    startMs = state.startMs,
                    endMs = state.endMs,
                    name = name,
                    onProgress = { p -> _clip.update { it?.copy(progress = p) } },
                )
                _clip.value = null
                _clipReady.send(ClipReady(file, state.item.title))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Clipping ${state.item.id} failed", e)
                _clip.update { it?.copy(progress = null, failed = true) }
            }
        }
    }

    /** Closing the sheet also stops a clip that's still being made. */
    fun dismissClip() {
        clipJob?.cancel()
        clipJob = null
        _clip.value = null
    }

    fun pause(id: String) = launch { repository.pause(id) }
    fun resume(id: String) = launch { repository.resume(id) }
    fun cancel(id: String) = launch { repository.cancel(id) }
    fun retry(id: String) = launch { repository.retry(id) }
    fun delete(id: String) = launch { repository.delete(id, deleteFile = true) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val TAG = "DownloadsViewModel"
        const val CLIP_EXTENSION = "m4a"
    }
}

private val WHITESPACE = Regex("""\s+""")

/** Every word appears in the title (or the link, before the title is known) or the uploader, ignoring case. */
private fun DownloadEntity.matches(words: List<String>): Boolean = words.all { word ->
    (title ?: sourceUrl).contains(word, ignoreCase = true) || uploader?.contains(word, ignoreCase = true) == true
}
