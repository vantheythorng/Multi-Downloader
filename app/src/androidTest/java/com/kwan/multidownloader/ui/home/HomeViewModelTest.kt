package com.kwan.multidownloader.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kwan.multidownloader.R
import com.kwan.multidownloader.data.DownloadRepository
import com.kwan.multidownloader.data.settings.SettingsRepository
import com.kwan.multidownloader.domain.DownloadRequest
import com.kwan.multidownloader.domain.MediaInfo
import com.kwan.multidownloader.domain.Platform
import com.kwan.multidownloader.engine.DownloadEngine
import com.kwan.multidownloader.engine.EngineEvent
import com.kwan.multidownloader.engine.EngineException
import com.kwan.multidownloader.engine.ErrorKind
import com.kwan.multidownloader.engine.ExtractorUpdater
import com.kwan.multidownloader.engine.MediaInfoFetcher
import com.kwan.multidownloader.engine.UpdateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Drives HomeViewModel with a fake engine (no Python, no network). Repositories come from the app's
 * Koin graph but are only read: these tests never queue a download or change settings.
 */
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    private val koin = GlobalContext.get()

    private class FakeEngine(private val result: () -> MediaInfo) : DownloadEngine {
        var fetches = 0
        override suspend fun init() = Unit
        override suspend fun fetchInfo(url: String): MediaInfo = result().also { fetches++ }
        override fun download(req: DownloadRequest): Flow<EngineEvent> = emptyFlow()
        override fun cancel(taskId: String) = Unit
        override suspend fun updateExtractor(): UpdateResult = UpdateResult.Failed("not in tests")
        override suspend fun version(): String? = null
    }

    private fun viewModel(engine: DownloadEngine): HomeViewModel {
        val settings = koin.get<SettingsRepository>()
        return HomeViewModel(MediaInfoFetcher(engine, ExtractorUpdater(engine, settings)), koin.get<DownloadRepository>(), settings)
    }

    private val info = MediaInfo(
        id = "abc", url = "https://youtu.be/abc", platform = Platform.YOUTUBE, title = "Song",
        uploader = "Artist", thumbnailUrl = null, durationSec = 100, formats = emptyList(),
    )

    private suspend fun HomeViewModel.awaitFetch(predicate: (FetchState) -> Boolean): FetchState =
        withTimeout(5_000) { state.first { predicate(it.fetch) }.fetch }

    @Test fun text_without_link_is_an_error_without_retry() = runBlocking {
        val engine = FakeEngine { info }
        val vm = viewModel(engine)
        vm.onUrlChange("nothing to see here")
        vm.fetch()
        val error = vm.awaitFetch { it is FetchState.Error } as FetchState.Error
        assertEquals(R.string.home_no_link, error.messageRes)
        assertFalse(error.canRetry)
        assertEquals(0, engine.fetches)
    }

    @Test fun shared_text_is_cleaned_and_loads_the_sheet() = runBlocking {
        val vm = viewModel(FakeEngine { info })
        vm.onUrlChange("Song - Artist https://youtu.be/abc?si=tracking")
        vm.fetch()
        val loaded = vm.awaitFetch { it is FetchState.Loaded } as FetchState.Loaded
        assertEquals("Song", loaded.info.title)
        assertEquals("https://youtu.be/abc", vm.state.value.url)
        assertTrue(vm.state.value.showSheet)
    }

    @Test fun engine_errors_get_translatable_text_and_retry() = runBlocking {
        val vm = viewModel(FakeEngine { throw EngineException("Private video", ErrorKind.PRIVATE) })
        vm.onUrlChange("https://youtu.be/abc")
        vm.fetch()
        val error = vm.awaitFetch { it is FetchState.Error } as FetchState.Error
        assertEquals(R.string.error_private, error.messageRes)
        assertTrue(error.canRetry)
    }
    @Test fun copied_supported_link_is_offered_and_used() = runBlocking {
        val engine = FakeEngine { info }
        val vm = viewModel(engine)
        vm.onClipboardText("https://youtu.be/abc?si=tracking", stamp = 1L)
        assertEquals("https://youtu.be/abc", vm.state.value.clipboardLink?.url)
        assertEquals(1L, vm.seenClipStamp)

        vm.useClipboardLink()
        assertEquals("https://youtu.be/abc", vm.state.value.url)
        vm.awaitFetch { it is FetchState.Loaded }
        assertEquals(1, engine.fetches)
    }

    @Test fun copying_something_without_supported_link_replaces_the_offer() {
        val vm = viewModel(FakeEngine { info })
        vm.onClipboardText("https://youtu.be/abc", stamp = 1L)
        vm.onClipboardText("https://example.com/video/1", stamp = 2L)
        assertNull(vm.state.value.clipboardLink)
        vm.onClipboardText("no link here", stamp = 3L)
        assertNull(vm.state.value.clipboardLink)
        assertEquals(3L, vm.seenClipStamp)
    }

    @Test fun clipboard_is_not_read_while_the_field_has_text() = runBlocking {
        val vm = viewModel(FakeEngine { info })
        vm.onUrlChange("https://youtu.be/typed")
        assertFalse(vm.wantsClipboard())
    }

    @Test fun clearing_the_field_keeps_the_offer() {
        val vm = viewModel(FakeEngine { info })
        vm.onClipboardText("https://youtu.be/abc", stamp = 1L)
        vm.onUrlChange("typed")
        vm.clear()
        assertEquals("https://youtu.be/abc", vm.state.value.clipboardLink?.url)
    }

    @Test fun dismissed_offer_stays_hidden_until_a_new_clip() {
        val vm = viewModel(FakeEngine { info })
        vm.onClipboardText("https://youtu.be/abc", stamp = 1L)
        vm.dismissClipboardLink()
        assertNull(vm.state.value.clipboardLink)
        assertEquals(1L, vm.seenClipStamp)

        vm.onClipboardText("https://youtu.be/def", stamp = 2L)
        assertEquals("https://youtu.be/def", vm.state.value.clipboardLink?.url)
    }
}
