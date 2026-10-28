# Android yt-dlp GUI

<p align="center">
  <img src="https://img.shields.io/badge/Android-7.0%2B-brightgreen?logo=android" alt="Android 7.0+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.3.20-blueviolet?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?logo=jetpackcompose" alt="Compose"/>
  <img src="https://img.shields.io/badge/yt--dlp-latest-red" alt="yt-dlp"/>
  <img src="https://img.shields.io/badge/Chaquopy-17.1.0-orange" alt="Chaquopy"/>
  <img src="https://img.shields.io/badge/minSdk-24-yellow" alt="minSdk 24"/>
</p>

A native Android application providing a full-featured GUI for [yt-dlp](https://github.com/yt-dlp/yt-dlp), supporting YouTube video & audio downloads, subtitle embedding, YouTube Music metadata, and more — running entirely on-device via Python-in-Android (Chaquopy).

---

## Features

- 🎬 **Video Downloads** — Quality presets up to 4K, custom format specifier, MP4 output
- 🎵 **Audio Extraction** — MP3/M4A/Opus with configurable bitrate
- 📝 **Subtitles** — Embed in video, save separately, auto-generated, multi-language
- 🎤 **YouTube Music** — Lyrics & metadata embedding via FFmpegMetadata postprocessor
- 📁 **Storage Selection** — SAF picker + persistent path memory, app-external default
- 🔔 **Foreground Download Service** — Persistent notification with progress bar; survives app backgrounding
- 📜 **Download History** — Search, open, share, delete completed downloads
- ⚙️ **Settings** — Light/Dark/System theme, yt-dlp update check, FFmpeg update, app self-update
- 🤖 **yt-dlp on-device** — Python 3.10 via Chaquopy, no server required

---

## Screenshots

> Screenshots taken from a Samsung Galaxy J7 Prime (Android 8.1.0) during development testing.

| Download Tab | Format Selection | Active Download |
|---|---|---|
| *(URL input, metadata card, quality picker)* | *(Video quality & audio-only options)* | *(Progress notification & download card)* |

---

## Requirements

- Android 7.0 (API 24) or higher
- Internet connection for downloads
- ~150–200 MB free storage for app + Python runtime + FFmpeg binary

---

## Installation

### From Releases *(coming soon)*
Download the latest APK from [Releases](https://github.com/naska/Android_yt-dlp_GUI/releases) and install it.

### From Source
See [Building from Source](#building-from-source) below.

---

## Architecture

The app uses a **Python-in-Android** approach via [Chaquopy](https://chaquo.com/chaquopy/):

```
┌─────────────────────────────────────────────┐
│              Jetpack Compose UI              │
│  DownloadScreen  HistoryScreen  Settings     │
└─────────────────┬───────────────────────────┘
                  │ ViewModel / StateFlow
┌─────────────────▼───────────────────────────┐
│              DownloadService                 │
│      (Foreground Service, Coroutines)        │
└─────────────────┬───────────────────────────┘
                  │ YtDlpEngine (Kotlin)
┌─────────────────▼───────────────────────────┐
│         ytdlp_bridge.py (Python)             │
│   extract_info / download / get_progress     │
└─────────────────┬───────────────────────────┘
                  │ yt-dlp pip package
┌─────────────────▼───────────────────────────┐
│        yt-dlp (Python library)               │
│    + FFmpeg binary (statically linked)       │
└─────────────────────────────────────────────┘
```

### Module Summary

| Module | File | Purpose |
|---|---|---|
| Python bridge | `ytdlp_bridge.py` | Wraps yt-dlp; all functions return JSON |
| Engine | `YtDlpEngine.kt` | Kotlin↔Python via Chaquopy; async dispatch |
| Download service | `DownloadService.kt` | Foreground service, progress polling, notifications |
| Binary updater | `BinaryUpdater.kt` | FFmpeg download/extraction, yt-dlp version check |
| App updater | `AppUpdater.kt` | GitHub release APK self-update |
| Data models | `data/model/` | `DownloadTask`, `VideoInfo`, `DownloadProgress`, etc. |
| Repository | `DownloadRepository.kt` | In-memory task store (persistence planned) |
| Preferences | `PreferencesManager.kt` | DataStore-backed settings |

---

## FFmpeg — Android 7/8 Compatibility

> **This is a significant engineering challenge on Android ≤ 8.1.**

Most pre-built FFmpeg Android binaries (e.g., Tyrrrz/FFmpegBin) are **dynamically linked** against `libc++_shared.so` and call `glob()`, which was only added to Android's Bionic libc in **API 28 (Android 9)**. On Android 7.0–8.1 this causes a fatal crash:
```
CANNOT LINK EXECUTABLE "ffmpeg": cannot locate symbol "glob"
```

**Solution:** The app uses **statically compiled** FFmpeg binaries from [`Khang-NT/ffmpeg-binary-android`](https://github.com/Khang-NT/ffmpeg-binary-android), targeting Android API 21+. These contain no dynamic library dependencies and run cleanly on all supported versions.

FFmpeg is downloaded to `filesDir/bin/ffmpeg` on first launch and persists there.

> **Planned:** Bundle the latest static FFmpeg inside the APK assets to eliminate the first-run download.

---

## Known Issues & Bugs

| # | Issue | Severity | Status |
|---|---|---|---|
| 1 | **FFmpeg bundling not implemented** — first-run requires network download of FFmpeg | Medium | 🔧 Planned |
| 2 | **Subtitle `getAvailableSubtitles` is a stub** — returns empty list | Low | 🐛 Bug |
| 3 | **Download history not persisted** — cleared on app restart (no Room/SQLite) | High | 🔧 Planned |
| 4 | **Final filename sometimes not captured** — post-processing renames the file after progress hook fires | Medium | 🐛 Bug |
| 5 | **Subtitle language selection UI incomplete** — languages fetched but selector not wired | Low | 🔧 Incomplete |
| 6 | **`-strict -2` FFmpeg flag** — workaround for old static binary; may print warnings with newer FFmpeg | Low | ⚠️ Monitor |
| 7 | **APK install slow on older hardware** — Chaquopy Python runtime extraction takes 45–90s | Known | Limitation |

---

## Building from Source

### Prerequisites

- [Android Studio Meerkat](https://developer.android.com/studio) or later
- JDK 17 (`java -version` → 17.x)
- Android SDK with API 35 installed
- Python 3.10 (via [uv](https://github.com/astral-sh/uv)):
  ```powershell
  uv python install 3.10
  ```

### Steps

```powershell
# Clone
git clone https://github.com/naska/Android_yt-dlp_GUI.git
cd Android_yt-dlp_GUI

# Verify Python path in app/build.gradle.kts matches your UV install
# buildPython("C:/Users/<you>/AppData/Roaming/uv/python/cpython-3.10-windows-x86_64-none/python.exe")

# Build
.\gradlew assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### ADB Useful Commands

```powershell
# Watch app logs
adb logcat -s YtDlpEngine DownloadService BinaryUpdater -v time

# Check FFmpeg was extracted
adb shell ls -la /data/data/com.ytdlp.android/files/bin/

# Verify downloaded files
adb shell ls /sdcard/Android/data/com.ytdlp.android/files/Downloads/
```

---

## Tech Stack

| Component | Version |
|---|---|
| Language | Kotlin 2.3.20 |
| UI | Jetpack Compose + Material 3 |
| Python runtime | Chaquopy 17.1.0 + Python 3.10 |
| Downloader | yt-dlp (latest pip release) |
| FFmpeg | Static binary (Khang-NT, API 21+) |
| HTTP | OkHttp |
| Serialization | Kotlinx Serialization JSON |
| Settings | AndroidX DataStore Preferences |
| Gradle | 9.1.0 + AGP 9.0.1 |
| `minSdk` / `targetSdk` | 24 / 35 |
| `compileSdk` | 36 |

---

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Downloads and GitHub API calls |
| `FOREGROUND_SERVICE` | Background download service |
| `FOREGROUND_SERVICE_DATA_SYNC` | Required foreground service type on API 29+ |
| `POST_NOTIFICATIONS` | Progress and completion notifications |
| `REQUEST_INSTALL_PACKAGES` | App self-update |
| `READ/WRITE_EXTERNAL_STORAGE` | Legacy storage on API ≤ 28/32 |

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

yt-dlp is licensed under the [Unlicense](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE).

---

## Acknowledgements

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — The powerhouse downloader
- [Chaquopy](https://chaquo.com/chaquopy/) — Python SDK for Android
- [Khang-NT/ffmpeg-binary-android](https://github.com/Khang-NT/ffmpeg-binary-android) — Statically linked FFmpeg for Android
- [Material Design 3](https://m3.material.io/) — UI design system
