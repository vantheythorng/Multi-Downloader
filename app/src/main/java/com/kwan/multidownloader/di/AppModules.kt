package com.kwan.multidownloader.di

import androidx.room.Room
import com.kwan.multidownloader.data.DownloadPaths
import com.kwan.multidownloader.data.DownloadRepository
import com.kwan.multidownloader.data.MediaStoreSaver
import com.kwan.multidownloader.data.cookies.CookieStore
import com.kwan.multidownloader.data.db.AppDatabase
import com.kwan.multidownloader.data.settings.SettingsRepository
import com.kwan.multidownloader.engine.DownloadEngine
import com.kwan.multidownloader.engine.ExtractorUpdater
import com.kwan.multidownloader.engine.MediaInfoFetcher
import com.kwan.multidownloader.share.ShareViewModel
import com.kwan.multidownloader.engine.YtDlpEngine
import com.kwan.multidownloader.ui.downloads.DownloadsViewModel
import com.kwan.multidownloader.ui.home.HomeViewModel
import com.kwan.multidownloader.ui.settings.SettingsViewModel
import com.kwan.multidownloader.work.ConcurrencyGate
import com.kwan.multidownloader.work.DownloadNotifications
import com.kwan.multidownloader.work.DownloadScheduler
import com.kwan.multidownloader.work.DownloadWorker
import com.kwan.multidownloader.work.ExtractorUpdateWorker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::ShareViewModel)
    viewModelOf(::SettingsViewModel)
}

val dataModule = module {
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, AppDatabase.NAME).build()
    }
    single { get<AppDatabase>().downloadDao() }
    singleOf(::SettingsRepository)
    singleOf(::MediaStoreSaver)
    singleOf(::DownloadPaths)
    singleOf(::CookieStore)
    singleOf(::DownloadRepository)
}

val engineModule = module {
    singleOf(::YtDlpEngine) bind DownloadEngine::class
    single { ExtractorUpdater(get(), get()) }
    singleOf(::MediaInfoFetcher)
}

val workModule = module {
    singleOf(::DownloadNotifications)
    singleOf(::DownloadScheduler)
    singleOf(::ConcurrencyGate)
    workerOf(::DownloadWorker)
    workerOf(::ExtractorUpdateWorker)
}

val allModules = listOf(appModule, dataModule, engineModule, workModule)
