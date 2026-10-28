# Tasks

Spec: https://claude.ai/artifact/JxGqX7zoGuVu1VWU1jNfxu

## Active

### M0 - Spike (1 week)
- [ ] **Create Android project** - Jetpack Compose, Material 3, minSdk 26, targetSdk latest stable
- [ ] **Add youtubedl-android** - `library`, `ffmpeg`, `aria2c` modules; `jniLibs.useLegacyPackaging = true`; ABI splits (arm64-v8a, armeabi-v7a, x86_64)
- [ ] **Smoke test engine on real phone** - fetch info + MP3 + MP4 for one public URL per platform
    - YouTube, YouTube Music, Facebook, TikTok, X
- [ ] **Verify YouTube JS-runtime requirement** - confirm which yt-dlp version/runtime works on-device
- [ ] **Go/no-go on engine** - youtubedl-android vs Chaquopy fallback

### M1 - Core download (2 weeks)
- [ ] **Set up Koin DI** - appModule, dataModule, engineModule, workModule; `koin-androidx-compose`, `koin-androidx-workmanager`
- [ ] **Domain models + UrlParser** - Platform, MediaType, MediaInfo, DownloadRequest; extract first URL, strip `si=`/`igsh=`/`utm_*`
- [ ] **DownloadEngine interface + YtDlpEngine** - fetchInfo (`--dump-single-json`), download Flow, cancel, updateExtractor, version
- [ ] **yt-dlp option builder** - MP3 (128/192/320k, embed thumbnail + metadata), MP4 (Best/1080/720/480/360, merge to mp4)
- [ ] **Room DB** - DownloadEntity, DAO, DownloadRepository
- [ ] **DataStore settings** - default format/quality, bitrate, save location, concurrency, Wi-Fi only
- [ ] **DownloadWorker** - CoroutineWorker, foreground `dataSync`, progress/ETA, max 2 concurrent
- [ ] **Notifications** - channels: Progress, Completed (Open/Share), Errors (Retry); POST_NOTIFICATIONS prompt on 33+
- [ ] **MediaStore save** - temp in cacheDir, copy to `Music/MultiDownloader` / `Movies/MultiDownloader` with IS_PENDING
- [ ] **Home screen** - URL field, Paste, platform icons, Fetch, states (Empty/Loading/Loaded/Error)
- [ ] **FormatBottomSheet** - thumbnail, title, MP3/MP4 toggle, quality dropdown, size estimate, Download
- [ ] **Downloads screen** - Active/Completed/Failed tabs, progress rows, Pause/Cancel/Retry/Open/Delete

### M2 - Share integration (1 week)
- [ ] **ShareReceiverActivity** - translucent theme, ACTION_SEND text/plain filter, read EXTRA_TEXT
- [ ] **Optional VIEW intent filter** - YouTube, TikTok, X, Facebook hosts
- [ ] **Share-out single file** - ACTION_SEND with MediaStore URI + FLAG_GRANT_READ_URI_PERMISSION
- [ ] **Share-out multiple files** - multi-select in History, ACTION_SEND_MULTIPLE
- [ ] **Quick download from share** - "Always download as..." skips the sheet
- [ ] **Sharing shortcuts** - "Download MP3" / "Download Video" direct share targets

### M3 - Settings & updates (1 week)
- [ ] **Settings screen** - all preferences, theme, about
- [ ] **In-app yt-dlp update** - Update button, current version, optional weekly auto-check
- [ ] **Cookies import per platform** - Netscape `cookies.txt`, app-private storage only
- [ ] **Wi-Fi only + concurrency limits** - WorkManager constraints

### M4 - Polish & release (1 week)
- [ ] **Error copy + empty states** - "Private video", "Extractor outdated, update yt-dlp"
- [ ] **First-run notice** - personal use, respect copyright and platform terms
- [ ] **Licences screen** - yt-dlp (Unlicense), ffmpeg (LGPL/GPL), youtubedl-android (GPL-3.0)
- [ ] **Tests** - UrlParser unit tests, option builder, DAO, ViewModels; instrumented share-in/share-out
- [ ] **Device matrix** - Android 8, 10, 13, 14+; arm64 + armeabi-v7a
- [ ] **Release APKs** - ABI-split builds on GitHub Releases

## Waiting On

## Someday
- [ ] **Playlist download (P2)** - select all or some entries of YouTube / YT Music playlists
- [ ] **Clipboard detection (P2)** - offer supported link on app open
- [ ] **Preview player (P2)** - Media3 ExoPlayer
- [ ] **F-Droid submission**

## Done
