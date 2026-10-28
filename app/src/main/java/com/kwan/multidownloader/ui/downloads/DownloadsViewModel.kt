package com.kwan.multidownloader.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kwan.multidownloader.data.DownloadRepository
import com.kwan.multidownloader.data.db.DownloadEntity
import com.kwan.multidownloader.domain.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DownloadsTab(val statuses: Set<Status>) {
    ACTIVE(Status.ACTIVE),
    COMPLETED(Status.COMPLETED),
    FAILED(Status.FAILED_OR_CANCELLED),
}

class DownloadsViewModel(private val repository: DownloadRepository) : ViewModel() {

    val tabs: Map<DownloadsTab, StateFlow<List<DownloadEntity>>> = DownloadsTab.entries.associateWith { tab ->
        repository.observe(tab.statuses).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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

    fun pause(id: String) = launch { repository.pause(id) }
    fun resume(id: String) = launch { repository.resume(id) }
    fun cancel(id: String) = launch { repository.cancel(id) }
    fun retry(id: String) = launch { repository.retry(id) }
    fun delete(id: String) = launch { repository.delete(id, deleteFile = true) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
