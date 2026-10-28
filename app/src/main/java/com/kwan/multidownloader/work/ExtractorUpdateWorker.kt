package com.kwan.multidownloader.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kwan.multidownloader.engine.ExtractorUpdater
import com.kwan.multidownloader.engine.UpdateResult
import java.util.concurrent.TimeUnit

/**
 * Daily yt-dlp update check; the first run happens right after install, off the launch path.
 * Turning it off in Settings makes [ExtractorUpdater.updateIfDue] a no-op, so the schedule can stay.
 */
class ExtractorUpdateWorker(
    context: Context,
    params: WorkerParameters,
    private val updater: ExtractorUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (updater.updateIfDue() is UpdateResult.Failed) Result.retry() else Result.success()

    companion object {
        private const val NAME = "extractor-update"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExtractorUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
