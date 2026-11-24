package com.kwan.multidownloader.ui.settings

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/** The three modes on Android's per-app battery page. */
enum class BatteryMode { UNRESTRICTED, OPTIMIZED, RESTRICTED }

object BatteryUsage {

    fun mode(context: Context): BatteryMode {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        return when {
            // "Restricted": WorkManager jobs barely run in the background.
            activityManager.isBackgroundRestricted -> BatteryMode.RESTRICTED
            powerManager.isIgnoringBatteryOptimizations(context.packageName) -> BatteryMode.UNRESTRICTED
            else -> BatteryMode.OPTIMIZED
        }
    }

    /**
     * Optimized → the system "Allow app to always run in background?" dialog.
     * Restricted can't be lifted by that dialog and Unrestricted is undone in the same place,
     * so both open the app's info page, where the Battery entry lives.
     */
    @SuppressLint("BatteryLife") // Distributed outside Google Play (GitHub / F-Droid), see spec.
    fun intentFor(context: Context, mode: BatteryMode): Intent = when (mode) {
        BatteryMode.OPTIMIZED -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData("package:${context.packageName}".toUri())
        BatteryMode.RESTRICTED, BatteryMode.UNRESTRICTED -> appDetails(context)
    }

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:${context.packageName}".toUri())
}
