# AGENTS.md — AI Agent Guidance for Android yt-dlp GUI

This file provides context and constraints for AI coding agents (GitHub Copilot, Gemini, Cursor, etc.) working on this repository.

---

## Project Overview

A **native Android application** (Jetpack Compose + Material 3) that provides a full GUI for [`yt-dlp`](https://github.com/yt-dlp/yt-dlp), supporting video/audio downloads, subtitle embedding, and YouTube Music metadata.

yt-dlp is embedded via **Chaquopy** (Python-in-Android) as a pip dependency — the Python runtime runs directly on-device.

---

## 🚫 Hard Constraints

### Do NOT touch the yt-dlp source tree
The directory `c:/Users/naska/OneDrive/Documents/GitHub/yt-dlp` is a **separate repository** and must **never be modified**. yt-dlp is integrated only as a Chaquopy pip dependency (`install("yt-dlp")` in `app/build.gradle.kts`).

### Android version compatibility
The app **must run on Android 7.0+ (API 24+)**. All code, native binaries, and shell commands must work on API 24–27 (Oreo) and above.

- Do NOT use APIs introduced after API 24 without appropriate `Build.VERSION.SDK_INT` guards.
- Do NOT use native binaries that dynamically link against Bionic symbols unavailable before API 28 (e.g., `glob()`).
- Use **statically linked** FFmpeg binaries that target Android 5.0+ (API 21+).

---

## Architecture

```
app/src/main/
├── kotlin/com/ytdlp/android/
│   ├── MainActivity.kt              # Single-activity host, navigation setup
│   ├── YtDlpApp.kt                  # Application class, notification channel init
│   ├── data/
│   │   ├── model/                   # Data classes: DownloadTask, DownloadProgress, VideoInfo, etc.
│   │   ├── preferences/             # PreferencesManager (DataStore)
│   │   └── repository/              # DownloadRepository (in-memory + persistence)
│   ├── engine/
│   │   └── YtDlpEngine.kt           # Kotlin ↔ Python bridge via Chaquopy
│   ├── service/
│   │   └── DownloadService.kt       # Foreground Service managing download lifecycle & notifications
│   ├── updater/
│   │   ├── BinaryUpdater.kt         # FFmpeg binary download, extraction, version check
│   │   └── AppUpdater.kt            # Self-update via GitHub Releases
│   └── ui/
│       ├── download/                # Download tab: URL input, metadata, format picker, active downloads
│       ├── history/                 # History tab: completed downloads list, search, share/open
│       ├── settings/                # Settings tab: theme, yt-dlp/FFmpeg/app update buttons
│       ├── setup/                   # Setup screen: first-run FFmpeg download flow
│       ├── navigation/              # AppNavigation composable
│       └── theme/                   # Color, Theme, Type (Material 3)
└── python/
    └── ytdlp_bridge.py              # Python bridge: extract_info, download, get_progress, cancel_download
```

### Key Design Decisions

| Decision | Rationale |
|---|---|
| Chaquopy for Python runtime | Runs yt-dlp natively on-device without subprocess or server |
| Python 3.10 via UV | Required by Chaquopy 17.1.0 compatibility matrix |
| Statically linked FFmpeg | Avoids `glob()` missing on Bionic < API 28; API-21+ targets work on all devices |
| Foreground Service | Keeps downloads alive when app is backgrounded; required for Android O+ |
| DataStore Preferences | Replaces deprecated SharedPreferences for async settings storage |
| SAF + app-external storage | User can pick output directory; app-external used as default for easy file access |

---

## FFmpeg Strategy

> **Critical:** Do NOT use `Tyrrrz/FFmpegBin` or any dynamically-linked FFmpeg binary.

### Problem
FFmpeg binaries from common Android distributions dynamically link `libc++_shared.so` and call `glob()`, which is only available in Android Bionic starting from **API 28**. On API 24–27 (Android 7.0–8.1), this causes:
```
CANNOT LINK EXECUTABLE "ffmpeg": cannot locate symbol "glob"
```

### Solution
Use **statically compiled** FFmpeg targeting Android API 21+:
- Source: `Khang-NT/ffmpeg-binary-android` (GitHub Releases, `.tar.bz2` archives)
- ABI mapping: `arm64-v8a-full.tar.bz2`, `armv7-a-full.tar.bz2`, `x86_64-full.tar.bz2`
- Extraction: Via Android's built-in `/system/bin/tar` (Toybox, available API 21+)
- Storage: `context.filesDir/bin/ffmpeg` with `chmod 755`
- **Future:** Bundle latest static FFmpeg inside `app/src/main/assets/bin/<abi>/ffmpeg` for zero-download first-run

### FFmpeg Path Flow
```
BinaryUpdater.getFfmpegPath()
  → context.filesDir/bin/ffmpeg
  → passed to ytdlp_bridge.py download() as ffmpeg_path
  → sets ydl_opts['ffmpeg_location'] = dirname(ffmpeg_path)
  → resets FFmpegPostProcessor._ffmpeg_location cache
```

---

## yt-dlp Format Selection

> **Critical:** Do NOT default to `bestvideo+bestaudio` without container constraints.

### Problem
yt-dlp's default format selection can combine **WebM VP9 video** with **M4A audio**. Merging these into an MP4 requires re-encoding or stream-copy. Static FFmpeg v3.3.x cannot stream-copy WebM into MP4, causing:
```
ERROR: Postprocessing: Stream #1:0 -> #0:1 (copy)
```

### Solution (in `ytdlp_bridge.py`)
```python
ydl_opts['format'] = (
    'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]'
    '/bestvideo[height<=720]+bestaudio'
    '/best[height<=720]'
)
ydl_opts['postprocessor_args'] = {'ffmpeg': ['-strict', '-2']}
```

---

## Python Bridge (`ytdlp_bridge.py`)

All functions **return JSON strings** — never raw Python objects.

| Function | Signature | Returns |
|---|---|---|
| `is_available()` | `()` | `{"available": bool, "version": str}` |
| `get_version()` | `()` | version string |
| `extract_info(url)` | `(str)` | `{"status": "ok"\|"error", ...metadata...}` |
| `download(task_id, url, opts_json, ffmpeg_path)` | `(str,str,str,str?)` | `{"status": "complete"\|"error"\|"cancelled", ...}` |
| `get_progress(task_id)` | `(str)` | `{"status": str, "downloaded_bytes": int, "total_bytes": int, "speed": float, "eta": int, "filename": str}` |
| `cancel_download(task_id)` | `(str)` | `{"status": "cancelled"\|"not_found"}` |

**Cancellation** is cooperative: a `threading.Event` per `task_id` is checked in the progress hook. When set, `DownloadCancelled` is raised to abort yt-dlp mid-download.

---

## Kotlin–Python Boundary

- `YtDlpEngine.kt` wraps all `bridge.callAttr(...)` calls in `try/catch`.
- All Python calls are dispatched on `Dispatchers.IO`.
- The `DownloadService` parses the returned JSON `status` field explicitly — never assumes `onSuccess` from Kotlin `Result<String>` means the download succeeded.

```kotlin
result.onSuccess { resJson ->
    val statusField = json.parseToJsonElement(resJson)
        .jsonObject["status"]?.jsonPrimitive?.content ?: "complete"
    when (statusField) {
        "complete"   -> { /* mark COMPLETE */ }
        "cancelled"  -> { /* mark CANCELLED */ }
        else         -> { /* mark ERROR, extract "error" field */ }
    }
}
```

---

## Known Bugs & Limitations

### Active Issues

| # | Bug | Location | Status |
|---|---|---|---|
| 1 | **FFmpeg bundling not yet implemented** — app currently downloads FFmpeg on first run; asset bundling is planned | `BinaryUpdater.kt`, `SetupScreen` | 🔧 Planned |
| 2 | **`getAvailableSubtitles` stub** — function in `YtDlpEngine.kt` calls `extract_info` twice and returns empty list | `YtDlpEngine.kt:43-51` | 🐛 Bug |
| 3 | **No subtitle language selection UI** — subtitle language list is fetched but the UI dropdown is not fully wired | `DownloadScreen.kt` | 🔧 Incomplete |
| 4 | **File path not always captured** — `filename` from progress hook may be blank for muxed output (post-processing renames the file) | `ytdlp_bridge.py` `_progress_hook` | 🐛 Bug |
| 5 | **History not persisted across app restarts** — `DownloadRepository` is in-memory only; no Room/SQLite backing | `DownloadRepository.kt` | 🔧 Planned |
| 6 | **APK install slow on older hardware** — Chaquopy Python runtime extraction takes 45–90s on API 27 devices | Build / Install | Known limitation |
| 7 | **`-strict -2` flag may be incompatible with newer FFmpeg versions** — workaround for old static binary; may cause warnings with v6+ | `ytdlp_bridge.py` | ⚠️ Monitor |

### Resolved Issues

| # | Bug | Fix |
|---|---|---|
| R1 | `glob()` missing on Android 7/8 | Switched to statically linked FFmpeg (Khang-NT builds) |
| R2 | False `COMPLETE` on download failure | Parse JSON `status` field in `DownloadService.kt` instead of relying on `Result.onSuccess` |
| R3 | Stream-copy mismatch (WebM VP9 + M4A → MP4) | Format selector prefers `[ext=mp4]+[ext=m4a]`; passes `-strict -2` |

---

## Build & Test

### Prerequisites
- Android Studio Meerkat or later
- JDK 17
- Android SDK with API 35 installed
- Python 3.10 via UV: `uv python install 3.10`
- `local.properties` pointing to SDK: `sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk`

### Build
```powershell
cd Android_yt-dlp_GUI
.\gradlew assembleDebug
```

### Install & Test (ADB)
```powershell
# List connected devices
adb devices

# Install (replace device ID as needed)
adb -s <device_id> install -r app/build/outputs/apk/debug/app-debug.apk

# View logs
adb -s <device_id> logcat -s YtDlpEngine DownloadService BinaryUpdater -v time

# Check extracted files
adb -s <device_id> shell ls -la /data/data/com.ytdlp.android/files/bin/
```

### Test Device
- **Samsung Galaxy J7 Prime** (`SM-G610F`)
- Android 8.1.0 Oreo (API 27)
- ABI: `armeabi-v7a`
- ADB ID: `5210a084534c1521`

---

## Tech Stack

| Component | Version |
|---|---|
| Gradle | 9.1.0 |
| Android Gradle Plugin (AGP) | 9.0.1 |
| Kotlin | 2.3.20 |
| Chaquopy | 17.1.0 |
| Python | 3.10 (UV runtime) |
| Compose BOM | Latest stable |
| Material 3 | Via Compose BOM |
| `compileSdk` | 36 |
| `targetSdk` | 35 |
| `minSdk` | 24 (Android 7.0) |
| OkHttp | For GitHub API + binary downloads |
| Kotlinx Serialization | JSON parsing |
| DataStore Preferences | Settings persistence |

---

## Permissions

| Permission | Reason |
|---|---|
| `INTERNET` | yt-dlp downloads, GitHub API calls |
| `FOREGROUND_SERVICE` | Background download service |
| `FOREGROUND_SERVICE_DATA_SYNC` | Required for `dataSync` foreground type (API 29+) |
| `POST_NOTIFICATIONS` | Download progress & completion notifications |
| `REQUEST_INSTALL_PACKAGES` | Self-update APK install |
| `READ_EXTERNAL_STORAGE` (max API 32) | Legacy storage access for history |
| `WRITE_EXTERNAL_STORAGE` (max API 28) | Legacy storage write for API 24–28 |
