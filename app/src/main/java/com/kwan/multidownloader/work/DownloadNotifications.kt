package com.kwan.multidownloader.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kwan.multidownloader.MainActivity
import com.kwan.multidownloader.R
import com.kwan.multidownloader.data.SavedFile
import com.kwan.multidownloader.data.db.DownloadEntity
import com.kwan.multidownloader.domain.Status
import com.kwan.multidownloader.engine.ErrorKind
import com.kwan.multidownloader.share.ShareIntents
import com.kwan.multidownloader.ui.components.errorText

/** Channels: Progress (low, ongoing), Completed (Open/Share), Errors (Retry). */
class DownloadNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_COMPLETED, context.getString(R.string.channel_completed), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_ERRORS, context.getString(R.string.channel_errors), NotificationManager.IMPORTANCE_DEFAULT),
            )
        )
    }

    fun progressId(taskId: String): Int = taskId.hashCode()

    fun progress(task: DownloadEntity, status: Status, pct: Float, speed: String?, etaSec: Long?): Notification {
        val text = when (status) {
            Status.QUEUED -> context.getString(R.string.status_queued)
            Status.FETCHING -> context.getString(R.string.status_fetching)
            Status.CONVERTING -> context.getString(R.string.status_converting)
            else -> listOfNotNull(
                "${pct.toInt()}%",
                speed,
                etaSec?.let { context.getString(R.string.eta, formatEta(it)) },
            ).joinToString(" · ")
        }
        val indeterminate = status != Status.DOWNLOADING
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.title ?: task.sourceUrl)
            .setContentText(text)
            .setProgress(100, pct.toInt(), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setGroup(GROUP_PROGRESS)
            .setContentIntent(openDownloadsIntent())
            .addAction(0, context.getString(R.string.action_cancel), actionIntent(NotificationActionReceiver.ACTION_CANCEL, task.id))
            .build()
    }

    @SuppressLint("MissingPermission")
    fun updateProgress(task: DownloadEntity, status: Status, pct: Float, speed: String?, etaSec: Long?) {
        if (canPost()) manager.notify(progressId(task.id), progress(task, status, pct, speed, etaSec))
    }

    /** One grouped summary while several jobs run at once. */
    @SuppressLint("MissingPermission")
    fun refreshSummary(runningCount: Int) {
        if (runningCount < 2) {
            manager.cancel(SUMMARY_ID)
            return
        }
        if (!canPost()) return
        val text = context.resources.getQuantityString(R.plurals.downloads_running, runningCount, runningCount)
        manager.notify(
            SUMMARY_ID,
            NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(text)
                .setGroup(GROUP_PROGRESS)
                .setGroupSummary(true)
                .setSilent(true)
                .setContentIntent(openDownloadsIntent())
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    fun showCompleted(task: DownloadEntity, saved: SavedFile) {
        if (!canPost()) return
        val where = context.getString(R.string.saved_to, saved.folder)
        val size = Formatter.formatShortFileSize(context, saved.sizeBytes)
        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(task.title ?: task.sourceUrl)
            .setContentText("$where · $size")
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent())
            .addAction(0, context.getString(R.string.action_open), activityIntent(task.id, 1, ShareIntents.open(saved.uri, saved.mimeType)))
            .addAction(
                0,
                context.getString(R.string.action_share),
                activityIntent(task.id, 2, ShareIntents.share(saved.uri, saved.mimeType, task.title)),
            )
            .build()
        manager.notify(task.id, RESULT_ID, notification)
    }

    @SuppressLint("MissingPermission")
    fun showError(task: DownloadEntity, kind: ErrorKind, fallback: String) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ERRORS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.download_failed, task.title ?: task.sourceUrl))
            .setContentText(errorText(context.resources, kind, fallback))
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent())
            .addAction(0, context.getString(R.string.action_retry), actionIntent(NotificationActionReceiver.ACTION_RETRY, task.id))
            .build()
        manager.notify(task.id, RESULT_ID, notification)
    }

    fun cancelProgress(taskId: String) {
        manager.cancel(progressId(taskId))
    }

    fun cancelResult(taskId: String) {
        manager.cancel(taskId, RESULT_ID)
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openDownloadsIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun activityIntent(taskId: String, slot: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            taskId.hashCode() * 31 + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun actionIntent(action: String, taskId: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode() * 31 + action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_COMPLETED = "completed"
        const val CHANNEL_ERRORS = "errors"
        private const val GROUP_PROGRESS = "downloads"
        private const val SUMMARY_ID = 1
        private const val RESULT_ID = 2

        fun formatEta(seconds: Long): String =
            if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
            else "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
