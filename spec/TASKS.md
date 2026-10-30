# Tasks

Spec: https://claude.ai/artifact/JxGqX7zoGuVu1VWU1jNfxu

## Active

### M5 - App v2
- [ ] **Splash screen** - launcher icon on the icon's dark grey (core-splashscreen 1.2.0, so Android 10-11 get it too); held until settings load, so the theme and first-run notice are right on the first frame
    - Galaxy S10+, Android 12: the icon zooms out of its drawer tile, then Home; launches from adb or the drawer's search show the background only (Android 12 shows the icon for launcher launches only)
    - Not tested yet: Android 10-11, where the library draws it instead of the system

### M4 - Polish & release (1 week)
- [ ] **Device matrix** - Android 10, 13, 14+; arm64 + armeabi-v7a (minSdk 29, so Android 10 is the floor)
    - Pixel 3, Android 12, arm64 (4 KB pages): everything works
    - Emulator, Android 17, arm64, 16 KB pages: MP3 and video both work
        - youtubedl-android 0.18.1 bundles 4 KB-aligned libwebp, so FFmpeg couldn't start there (upstream issue yausername/youtubedl-android#365)
        - Fixed in-app: libwebp 1.6.0 rebuilt with 16 KB alignment (tools/webp16k/build.sh), swapped in on 16 KB devices only (engine/Ffmpeg16kFix.kt); it stops patching once upstream's own libraries are aligned
    - Not tested yet: Android 10, Android 13, a 32-bit armeabi-v7a device
- [ ] **Release APKs** - ABI-split builds on GitHub Releases
    - Release build has R8 shrinking (63 MB arm64, was 132 MB) and was checked on the emulator
    - Left (on hold, by choice): release signing key, then publishing to GitHub Releases
- [ ] **Instagram and Pinterest (video only)** - reels, video posts and video pins; no new library, yt-dlp's InstagramIE/PinterestIE
    - Done in code: link detection, badges, Open with hosts, photo-only posts show "only photos" (`ErrorKind.NO_VIDEO`) instead of updating yt-dlp
    - Not tested on a device yet: a public reel (may need a login), a video pin and its `pin.it` link, a photo post and an image pin

## Waiting On

## Someday
- [ ] **Playlist download (P2)** - select all or some entries of YouTube / YT Music playlists
- [ ] **Clipboard detection (P2)** - offer supported link on app open
- [ ] **Preview player (P2)** - Media3 ExoPlayer
- [ ] **F-Droid submission**

## Done

### M0 - Spike
- [x] ~~**Create Android project** - Jetpack Compose, Material 3, targetSdk latest stable~~ (2026-10-28)
    - minSdk kept at 29 instead of 26 (decided 2026-10-28); compileSdk 37
- [x] ~~**Add youtubedl-android** - `library`, `ffmpeg`, `aria2c` 0.18.1; `jniLibs.useLegacyPackaging = true`; ABI splits~~ (2026-10-28)
- [x] ~~**Verify YouTube JS-runtime requirement**~~ (2026-10-28)
    - yt-dlp 2026.10.26 + library-bundled QuickJS (`--js-runtimes quickjs`) + Python 3.12 works on-device; bundled yt-dlp 2025.11.12 fails
- [x] ~~**Smoke test engine on real phone** - fetch info + MP3 + MP4 for one public URL per platform~~ (2026-10-28)
    - YouTube: info + MP3 + MP4 on Pixel 3 (Android 12) and on a 16 KB Android 17 emulator
    - TikTok: MP4 downloaded on Pixel 3; Facebook: info fetched on Xiaomi (Android 17)
    - Not covered: YouTube Music and X downloads, and a Facebook download itself
- [x] ~~**Go/no-go on engine** - youtubedl-android vs Chaquopy fallback~~ (2026-10-28)
    - Go with youtubedl-android 0.18.1, with yt-dlp kept current in-app and the 16 KB libwebp fix (engine/Ffmpeg16kFix.kt)

### M1 - Core download
- [x] ~~**Set up Koin DI** - appModule, dataModule, engineModule, workModule~~ (2026-10-28)
- [x] ~~**Domain models + UrlParser** - extract first URL, strip `si=`/`igsh=`/`utm_*`~~ (2026-10-28)
- [x] ~~**DownloadEngine interface + YtDlpEngine** - fetchInfo, download Flow, cancel, updateExtractor, version~~ (2026-10-28)
- [x] ~~**yt-dlp option builder** - MP3 128/192/320k, MP4 Best/1080/720/480/360~~ (2026-10-28)
- [x] ~~**Room DB** - DownloadEntity, DAO, DownloadRepository~~ (2026-10-28)
- [x] ~~**DataStore settings** - default format/quality, bitrate, save location, concurrency, Wi-Fi only~~ (2026-10-28)
- [x] ~~**DownloadWorker** - CoroutineWorker, foreground `dataSync`, progress/ETA, max 2 concurrent~~ (2026-10-28)
- [x] ~~**Notifications** - Progress, Completed (Open/Share), Errors (Retry); POST_NOTIFICATIONS prompt on 33+~~ (2026-10-28)
- [x] ~~**MediaStore save** - cacheDir temp, copy to `Music/` / `Movies/MultiDownloader` with IS_PENDING~~ (2026-10-28)
- [x] ~~**Home screen** - URL field, Paste, platform icons, Fetch, states~~ (2026-10-28)
- [x] ~~**FormatBottomSheet** - thumbnail, title, MP3/MP4 toggle, quality, size estimate, Download~~ (2026-10-28)
- [x] ~~**Downloads screen** - Active/Completed/Failed tabs, Pause/Cancel/Retry/Open/Share/Delete~~ (2026-10-28)

### M2 - Share integration
- [x] ~~**ShareReceiverActivity** - translucent theme, ACTION_SEND text/plain filter, read EXTRA_TEXT~~ (2026-10-28)
- [x] ~~**Optional VIEW intent filter** - YouTube, TikTok, X, Facebook hosts~~ (2026-10-28)
    - Android 12+ only routes links here if the user enables "Open supported links"
- [x] ~~**Share-out single file** - ACTION_SEND with MediaStore URI + FLAG_GRANT_READ_URI_PERMISSION~~ (2026-10-28)
- [x] ~~**Share-out multiple files** - multi-select in History, ACTION_SEND_MULTIPLE~~ (2026-10-28)
    - Long-press a Completed row to select; bar offers Share and Delete
- [x] ~~**Quick download from share** - "Always download as..." skips the sheet~~ (2026-10-28)
    - Toggle in Settings → "When a link is shared"
- [x] ~~**Sharing shortcuts** - "Download MP3" / "Download Video" direct share targets~~ (2026-10-28)
    - Published and handled; where they rank in the share sheet's direct-share row is up to Android

### M3 - Settings & updates
- [x] ~~**Settings screen** - all preferences, theme, about~~ (2026-10-28)
    - Downloads, Sharing, Login cookies, Downloader (yt-dlp), Background downloads, Appearance, About; licences link comes with M4
- [x] ~~**In-app yt-dlp update** - Update button, current version, optional auto-check~~ (2026-10-28)
    - Check runs daily (not weekly) and can be turned off; update-and-retry on "extractor outdated" always runs
- [x] ~~**Cookies import per platform** - Netscape `cookies.txt`, app-private storage only~~ (2026-10-28)
    - Validated on import, excluded from backups; not yet tried with real login cookies on a private post
    - Hidden 2026-10-30 (`CookieStore.ENABLED`): import kept every site's cookies from a browser export. Re-enable after keeping only the platform's domains and warning before import
- [x] ~~**Wi-Fi only + concurrency limits** - WorkManager constraints~~ (2026-10-28)
    - Wi-Fi only also re-queues downloads that haven't started; not tested on mobile data

### M4 - Polish & release
- [x] ~~**Error copy + empty states** - "Private video", "Extractor outdated, update yt-dlp"~~ (2026-10-28)
    - Errors are stored as a kind (Room v2, auto-migration) and shown as translatable text that says what to do next
- [x] ~~**First-run notice** - personal use, respect copyright and platform terms~~ (2026-10-28)
    - Also shown before a first share, with Cancel
- [x] ~~**Licences screen** - yt-dlp (Unlicense), ffmpeg (LGPL/GPL), youtubedl-android (GPL-3.0)~~ (2026-10-28)
    - Plus Python, aria2, QuickJS and the Apache-2.0 libraries; Settings → About → Open-source licences
- [x] ~~**Tests** - UrlParser unit tests, option builder, DAO, ViewModels; instrumented share-in/share-out~~ (2026-10-28)
    - 40 unit tests; 14 instrumented tests (DAO, 1→2 migration, share intents, share screen, HomeViewModel) pass on the Pixel 3
