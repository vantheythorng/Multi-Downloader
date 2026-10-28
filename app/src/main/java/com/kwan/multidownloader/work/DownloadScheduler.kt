package com.kwan.multidownloader.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kwan.multidownloader.data.settings.SettingsRepository
import com.kwan.multidownloader.engine.DownloadEngine
import java.util.concurrent.TimeUnit

class DownloadScheduler(
    context: Context,
    private val settings: SettingsRepository,
    private val engine: DownloadEngine,
) {
    private val workManager = WorkManager.getInstance(context)

    /** [replace] swaps a pending job for one with the current network constraint (Wi-Fi only changed). */
    suspend fun enqueue(taskId: String, replace: Boolean = false) {
        val wifiOnly = settings.current().wifiOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to taskId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        // KEEP ignores double taps while a job is pending; a finished or cancelled job is replaced.
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(taskId, policy, request)
    }

    fun cancel(taskId: String) {
        engine.cancel(taskId)
        workManager.cancelUniqueWork(taskId)
    }

    companion object {
        const val TAG = "downloads"
    }
}
