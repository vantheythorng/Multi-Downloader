package com.kwan.multidownloader.engine

import com.kwan.multidownloader.domain.MediaInfo

/** Fetches media info; on "extractor outdated" it updates yt-dlp and fetches once more. Shared by Home and share-in. */
class MediaInfoFetcher(
    private val engine: DownloadEngine,
    private val updater: ExtractorUpdater,
) {
    /** Starts the Python runtime early so the first fetch is faster. */
    suspend fun warmUp() {
        runCatching { engine.init() }
    }

    /** @throws EngineException with a user-facing message. */
    suspend fun fetch(url: String, onUpdatingExtractor: () -> Unit = {}): MediaInfo =
        try {
            engine.fetchInfo(url)
        } catch (e: EngineException) {
            if (e.kind != ErrorKind.EXTRACTOR_OUTDATED) throw e
            onUpdatingExtractor()
            when (updater.updateAfterFailure()) {
                is UpdateResult.Updated -> engine.fetchInfo(url)
                is UpdateResult.AlreadyUpToDate -> throw EngineException(YtDlpErrors.SITE_CHANGED_MESSAGE, ErrorKind.SITE_CHANGED, e)
                else -> throw e
            }
        }
}
