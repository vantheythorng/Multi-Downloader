package com.kwan.multidownloader.engine

import com.kwan.multidownloader.data.settings.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Keeps yt-dlp current: platforms change often and the copy bundled in the APK goes stale,
 * which shows up as "Extractor outdated". Checks are rate-limited because the update hits
 * GitHub's API (60 unauthenticated requests per hour).
 */
class ExtractorUpdater(
    private val engine: DownloadEngine,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Routine check (periodic worker): only if enabled in Settings and the last one is older than [ROUTINE_INTERVAL_MS]. */
    suspend fun updateIfDue(): UpdateResult? {
        if (!settings.current().autoUpdateExtractor) return null
        return updateIfOlderThan(ROUTINE_INTERVAL_MS)
    }

    /** After an "extractor outdated" failure: update unless we just checked. Runs even with auto-check off. */
    suspend fun updateAfterFailure(): UpdateResult? = updateIfOlderThan(FAILURE_INTERVAL_MS)

    /** The Settings "Update now" button: always asks GitHub. */
    suspend fun updateNow(): UpdateResult = update(clock())

    private suspend fun updateIfOlderThan(intervalMs: Long): UpdateResult? {
        val now = clock()
        if (now - settings.lastExtractorCheck() < intervalMs) return null
        return update(now)
    }

    private suspend fun update(now: Long): UpdateResult {
        val result = engine.updateExtractor()
        if (result !is UpdateResult.Failed) settings.setLastExtractorCheck(now)
        return result
    }

    companion object {
        val ROUTINE_INTERVAL_MS = TimeUnit.HOURS.toMillis(20)
        val FAILURE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
