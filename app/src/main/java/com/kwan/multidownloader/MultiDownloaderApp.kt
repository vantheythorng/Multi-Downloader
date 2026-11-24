package com.kwan.multidownloader

import android.app.Application
import com.kwan.multidownloader.di.allModules
import com.kwan.multidownloader.share.ShareShortcuts
import com.kwan.multidownloader.work.DownloadNotifications
import com.kwan.multidownloader.work.ExtractorUpdateWorker
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class MultiDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MultiDownloaderApp)
            // Initialises WorkManager with Koin's factory; the default initializer is removed in the manifest.
            workManagerFactory()
            modules(allModules)
        }
        get<DownloadNotifications>().createChannels()
        ExtractorUpdateWorker.schedule(this)
        ShareShortcuts.publish(this)
    }
}
