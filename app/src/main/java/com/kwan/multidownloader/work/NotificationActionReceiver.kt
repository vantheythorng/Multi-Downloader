package com.kwan.multidownloader.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kwan.multidownloader.data.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Handles the Cancel (progress) and Retry (errors) notification actions. */
class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {
    private val repository: DownloadRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_CANCEL -> repository.cancel(taskId)
                    ACTION_RETRY -> repository.retry(taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.kwan.multidownloader.action.CANCEL"
        const val ACTION_RETRY = "com.kwan.multidownloader.action.RETRY"
        const val EXTRA_TASK_ID = "task_id"
    }
}
