# Multi-Downloader

An Android app that downloads audio (MP3) and video (MP4) from YouTube, YouTube Music, Facebook, TikTok and X, entirely on your phone. It embeds [yt-dlp](https://github.com/yt-dlp/yt-dlp), Python and FFmpeg through [youtubedl-android](https://github.com/yausername/youtubedl-android); there is no server.

Share a link from any app, pick MP3 or video, and the file lands in your Music or Movies folder.

> For personal use. Only download what you have the right to, and respect copyright and each platform's terms.

## Features

- **Download** MP3 (128/192/320 kbps, with cover art and title/artist tags) or MP4 (Best, 1080p, 720p, 480p, 360p).
- **Share to download**: pick Multi-Downloader in any app's share sheet, or use the **Download MP3** / **Download Video** share shortcuts. Optional "Always download as…" skips the format sheet.
- **Background downloads** with progress notifications, pause/resume, retry, and a limit of 1–3 at once. Optional Wi-Fi only.
- **Share files** you downloaded (one or several at once), or **share the original link**.
- **Keeps working when sites change**: yt-dlp updates itself in the app (daily check, plus an automatic update and retry when a site breaks).
- **Login-only posts** via per-platform `cookies.txt` import. Cookies stay on the phone and are excluded from backups.
- Light/dark theme, colours from the app icon or your wallpaper, compact bars.
- **Private**: no account, no ads, no tracking. The app only talks to the sites you download from and to GitHub to update yt-dlp.

| Site | Links it recognises |
|---|---|
| YouTube | `youtube.com/watch`, `youtu.be`, `/shorts/`, `/live/`, `m.youtube.com` |
| YouTube Music | `music.youtube.com` (defaults to MP3) |
| Facebook | `/videos/`, `/reel/`, `/watch`, `/share/v/`, `fb.watch` |
| TikTok | `/@user/video/`, `vm.tiktok.com`, `vt.tiktok.com`, `/t/` |
| X | `x.com/…/status/`, `twitter.com/…/status/` |

Other sites are passed to yt-dlp too, flagged as "may fail". TikTok photo posts download as their sound only (MP3).

## Install

Requires **Android 10 or newer** (arm64-v8a, armeabi-v7a or x86_64).

Download the APK for your phone's ABI from [Releases](https://github.com/vantheythorng/Multi-Downloader/releases) (most phones: `arm64-v8a`). The app is not on Google Play, whose policy doesn't allow YouTube downloaders.

## Build

Requirements: Android Studio with JDK 21, Android SDK platform 37. Gradle 9.4.1 and AGP 9.2.1 come with the wrapper.

```sh
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-<abi>-debug.apk
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug
```

Builds are split per ABI, so each APK carries a single copy of the native Python and FFmpeg.

### Release signing

Release builds are shrunk with R8 and signed from `secret/keystore.properties`, which is git-ignored:

```properties
storeFile=./secret/your-keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

`storeFile` is relative to the project root. Without this file, `./gradlew :app:assembleRelease` still builds, but the APKs are unsigned. Keep the keystore backed up: every update must be signed with the same key.

### Instrumented tests

`connectedAndroidTest` uninstalls the app afterwards, which wipes its data on your phone. To keep it, install the test APK and run it directly:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.kwan.multidownloader.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall com.kwan.multidownloader.test
```

## How it works

A shared or pasted link becomes a Room row plus a WorkManager job. The worker is the only part that runs yt-dlp; the finished file is copied into MediaStore so other apps can open and share it.

```
app/src/main/java/com/kwan/multidownloader/
├── ui/       Compose screens: Home, Downloads, Settings, licences, format sheet
├── share/    Share receiver, share intents, sharing shortcuts
├── domain/   Link parsing (UrlParser), models, size estimates
├── engine/   DownloadEngine + YtDlpEngine, yt-dlp options, error mapping, yt-dlp updates
├── data/     Room database, DataStore settings, cookies, MediaStore saving
├── work/     DownloadWorker, scheduling, notifications
└── di/       Koin modules
```

Built with Jetpack Compose and Material 3, Koin, Room, DataStore, WorkManager, Coil and kotlinx.serialization.

### 16 KB page-size devices

youtubedl-android 0.18.1 bundles libwebp aligned to 4 KB pages, so on 16 KB-page devices FFmpeg can't start ([upstream issue #365](https://github.com/yausername/youtubedl-android/issues/365)). The app ships libwebp 1.6.0 rebuilt with 16 KB alignment (`app/src/main/jniLibs/`, built by `tools/webp16k/build.sh`) and swaps it in on those devices only (`engine/Ffmpeg16kFix.kt`). It stops patching once upstream's own libraries are aligned.

## Licence

Multi-Downloader is free software, licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE).

Copyright © 2026 K'wan

It builds on these open-source projects, also listed in the app under **Settings → About → Open-source licences**:

| Project | Licence |
|---|---|
| yt-dlp | Unlicense |
| youtubedl-android | GPL-3.0 |
| FFmpeg | LGPL-2.1 / GPL-2.0 |
| libwebp | BSD-3-Clause |
| Python | PSF License |
| aria2 | GPL-2.0 |
| QuickJS | MIT |
| AndroidX, Kotlin, Koin, Coil, OkHttp, Jackson, Apache Commons | Apache-2.0 |

youtubedl-android is GPL-3.0, which is why the app is GPL-3.0 too: anyone distributing it must also make its source available under the same licence.

## Author

K'wan · [github.com/vantheythorng/Multi-Downloader](https://github.com/vantheythorng/Multi-Downloader)
