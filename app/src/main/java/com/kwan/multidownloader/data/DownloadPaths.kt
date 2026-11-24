package com.kwan.multidownloader.data

import android.content.Context
import java.io.File

/** yt-dlp works in cacheDir/downloads/<taskId>/ (fast, no permission); the folder is removed once saved. */
class DownloadPaths(private val context: Context) {
    fun tempDir(taskId: String): File = File(context.cacheDir, "downloads/$taskId")

    fun deleteTemp(taskId: String) {
        tempDir(taskId).deleteRecursively()
    }
}
