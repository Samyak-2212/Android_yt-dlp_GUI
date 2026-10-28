# AGENTS.md — AI Agent Guidance for Android yt-dlp GUI

This file provides context and constraints for AI coding agents (GitHub Copilot, Gemini, Cursor, etc.) working on this repository.

---

## Project Overview

A **native Android application** (Jetpack Compose + Material 3) that provides a full GUI for [`yt-dlp`](https://github.com/yt-dlp/yt-dlp), supporting video/audio downloads, subtitle embedding, and YouTube Music metadata.

- **yt-dlp runtime**: Embedded via **Chaquopy** (Python-in-Android) running **Python 3.11** as a pip dependency (`install("yt-dlp")`). Python 3.11 provides the exact ABI balance required for 32-bit `armeabi-v7a` and 64-bit (`arm64-v8a`, `x86_64`) support on API 24+ while satisfying yt-dlp's Python 3.11+ runtime requirement. Runs directly on-device with in-memory zipapp self-updating capability.
- **FFmpeg backend**: In-process **FFmpeg 9.0.2 JNI Shared Libraries** (`.so`), eliminating subprocess spawning, `execve` W^X security blocks, and Bionic libc missing symbol issues.

---

## 🚫 Hard Constraints

### Do NOT touch the yt-dlp source tree
The directory `c:/Users/naska/OneDrive/Documents/GitHub/yt-dlp` is a **separate repository** and must **never be modified**. yt-dlp is integrated only as a Chaquopy pip dependency (`install("yt-dlp")` in `app/build.gradle.kts`).

### Android version compatibility
The app **must run on Android 7.0+ (API 24+) through Android 15+ (API 35+)**.
- Do NOT use APIs introduced after API 24 without appropriate `Build.VERSION.SDK_INT` guards.
- Do NOT invoke `execve` on standalone executable binaries in app-private directories (blocked by W^X on Android 10+).
- Dynamic code loading must satisfy Android 14+ (API 34) read-only requirements (`file.setReadOnly()`).
- All native shared libraries must be 16 KB page-aligned (`-Wl,-z,max-page-size=16384`) for Android 15 (API 35+).

---

## Architecture

```
app/src/main/
├── jniLibs/                         # FFmpeg 9.0.2 pre-built shared libraries
│   ├── arm64-v8a/                   # libavcodec, libavformat, libavfilter, libavutil,
│   ├── armeabi-v7a/                 # libswresample, libswscale, libffmpeg_jni (.so)
│   └── x86_64/                      # 16 KB page-aligned, compiled for API 24+
├── kotlin/com/ytdlp/
│   ├── android/
│   │   ├── MainActivity.kt          # Single-activity host, navigation setup
│   │   ├── YtDlpApp.kt              # Application class, FFmpeg JNI init, notification channels
│   │   ├── data/
│   │   │   ├── model/               # Data classes: DownloadTask, DownloadProgress, VideoInfo, etc.
│   │   │   ├── preferences/         # PreferencesManager (DataStore)
│   │   │   └── repository/          # DownloadRepository (in-memory + persistence)
│   │   ├── engine/
│   │   │   └── YtDlpEngine.kt       # Kotlin ↔ Python bridge via Chaquopy
│   │   ├── service/
│   │   │   └── DownloadService.kt   # Foreground Service managing download lifecycle & notifications
│   │   ├── updater/
│   │   │   ├── BinaryUpdater.kt     # FFmpeg & yt-dlp version check & update coordination
│   │   │   └── AppUpdater.kt        # Self-update via GitHub Releases
│   │   └── ui/
│   │       ├── download/            # Download tab: URL input, metadata, format picker, active downloads
│   │       ├── history/             # History tab: completed downloads list, search, share/open
│   │       ├── settings/            # Settings tab: theme, engine versions, app updates
│   │       ├── setup/               # Setup screen: status verification (skipped when bundled)
│   │       └── navigation/          # AppNavigation composable
│   └── ffmpeg/
│       ├── FFmpegBridge.kt          # JNI bridge: runFFmpeg, cancelFFmpeg, getFFmpegVersion
│       └── FFmpegLoader.kt          # Approach A: dynamic .so download, read-only chmod, System.load order
└── python/
    └── ytdlp_bridge.py              # Python bridge with FFmpegPostProcessor JNI monkey-patch
```

### Key Design Decisions

| Decision | Rationale |
|---|---|
| Chaquopy for Python runtime | Runs yt-dlp natively on-device without subprocess or server |
| FFmpeg 9.0.2 JNI Shared Libraries | In-process execution via `libffmpeg_jni.so`; avoids Android 10+ W^X `execve` blocks |
| Bundled `jniLibs/` | Zero-setup first run, offline operation, 100% compliant on API 24–35+ |
| `FFmpegLoader` (Approach A) | Dynamic fallback: downloads `.so`, sets `file.setReadOnly()`, loads in dependency order |
| In-Process Python Hook | `FFmpegPostProcessor.real_run_ffmpeg` in Python calls `FFmpegBridge.runFFmpegList` |
| Foreground Service | Keeps downloads alive when app is backgrounded; required for Android O+ |
| DataStore Preferences | Async settings storage replacing deprecated SharedPreferences |

---

## FFmpeg Strategy (Approach A: JNI Shared Libraries)

### The Legacy Problem (Binary Executables)
Standalone ELF binaries placed in `context.filesDir` fail on modern Android:
1. **Android 10+ W^X Policy**: Kernel blocks `execve()` on private app storage paths.
2. **Missing Bionic Symbols**: Dynamic binaries depend on `glob()`, absent before API 28.
3. **Outdated Binaries**: Static binaries targeting API 21 were stuck at FFmpeg 3.3.2 (2017).

### The In-Process JNI Architecture
Instead of spawning a child process, FFmpeg 9.0.2 is compiled into 7 shared libraries:
- `libavcodec.so` (decoders / encoders: AAC, Opus, FLAC, H.264, VP9, AV1)
- `libavformat.so` (muxers / demuxers: MP4, Matroska, OGG, WebM, MP3)
- `libavfilter.so` (audio resampling, scaling, volume filters)
- `libavutil.so` (core utilities and math)
- `libswresample.so` (audio conversion)
- `libswscale.so` (video scaling / color space)
- `libffmpeg_jni.so` (JNI wrapper: compiles `fftools/ffmpeg.c` with `-Dmain=ffmpeg_main`)

### Loading Flow
```
App Startup (YtDlpApp.onCreate)
  → FFmpegBridge.init(fromJniLibs = true)
  → System.loadLibrary() in strict dependency order:
      avutil → swresample → swscale → avcodec → avformat → avfilter → ffmpeg_jni
  → JNI_OnLoad fires: "FFmpeg 9.0.2 JNI library loaded"
```

### Python Bridge Interception (`ytdlp_bridge.py`)
yt-dlp's `FFmpegPostProcessor.real_run_ffmpeg` is monkey-patched:
```python
from com.ytdlp.ffmpeg import FFmpegBridge

def jni_real_run_ffmpeg(self, input_path_opts, output_path_opts, *, expected_retcodes=(0,)):
    # Build CLI options list (-i in.mp4 -i in.m4a -c:v copy -c:a copy out.mp4)
    # Execute in-process via JNI:
    retcode = FFmpegBridge.runFFmpegList(cmd)
    if retcode not in expected_retcodes:
        raise FFmpegPostProcessorError(f"FFmpeg JNI failed with code {retcode}")
```

### Cancellation Mechanism
Calling `FFmpegBridge.cancelFFmpeg()` sends `raise(SIGINT)`. FFmpeg's `sigterm_handler()` increments `received_nb_signals`, triggering the interrupt callback and safely aborting transcoding cleanly.

---

## Python Bridge (`ytdlp_bridge.py`) API

| Function | Signature | Returns |
|---|---|---|
| `is_available()` | `()` | `{"available": bool, "version": str}` |
| `get_version()` | `()` | version string |
| `reload_ytdlp()` | `()` | `{"status": "ok"\|"error", "version": str, ...}` |
| `extract_info(url)` | `(str)` | `{"status": "ok"\|"error", ...metadata...}` |
| `download(task_id, url, opts_json, ffmpeg_path)` | `(str,str,str,str?)` | `{"status": "complete"\|"error"\|"cancelled", ...}` |
| `get_progress(task_id)` | `(str)` | `{"status": str, "downloaded_bytes": int, "total_bytes": int, ...}` |
| `cancel_download(task_id)` | `(str)` | `{"status": "cancelled"\|"not_found"}` |

---

## Known Bugs & Status

| # | Item | Severity | Status |
|---|---|---|---|
| 1 | **History not persisted across restarts** — `DownloadRepository` in-memory | High | 🔧 Planned (Room) |
| 2 | **`getAvailableSubtitles` stub** — returns empty list | Low | 🐛 Bug |
| 3 | **Subtitle language dropdown UI incomplete** | Low | 🔧 Incomplete |
| 4 | **Filename capture for complex muxes** — post-processing renames final file | Medium | 🔧 Monitored |

### Resolved Issues
| # | Issue | Solution |
|---|---|---|
| R1 | `glob()` missing on Android 7/8 | Switched to modern NDK-compiled JNI shared libraries |
| R2 | `execve()` W^X block on Android 10+ | Switched to in-process JNI `ffmpeg_main` execution |
| R3 | False `COMPLETE` on error | Explicit JSON status parsing in `DownloadService.kt` |
| R4 | WebM stream-copy failures | FFmpeg 9.0.2 with native container muxing |
| R5 | FFmpeg first-run download requirement | Bundled FFmpeg 9.0.2 in `jniLibs` for instant startup |
| R6 | Custom format missing in Settings | Added DataStore key & two-way state in Settings & Download tabs |
| R7 | yt-dlp Engine self-update blocked | Implemented dynamic GitHub zipapp download, Android 14+ read-only chmod, and in-memory module eviction/reload |
| R8 | Typing URL flooded yt-dlp threads | Added 600ms debounce and active `Job` cancellation in `DownloadViewModel` |

---

## Build & Test

### Build
```powershell
cd Android_yt-dlp_GUI
.\gradlew assembleDebug
```

### Install & Test (ADB)
```powershell
# Install to connected device
adb -s 5210a084534c1521 install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor logs
adb -s 5210a084534c1521 logcat -s FFmpegJNI FFmpegBridge YtDlpEngine DownloadService -v time
```

### Physical Test Device
- **Samsung Galaxy J7 Prime** (`SM-G610F`)
- Android 8.1.0 Oreo (API 27)
- ABI: `armeabi-v7a`
- Device ID: `5210a084534c1521`
