<div align="center">

# Android yt-dlp GUI

[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-brightgreen?logo=android)](https://developer.android.com/about/versions/nougat)
[![Kotlin 2.3.20](https://img.shields.io/badge/Kotlin-2.3.20-blueviolet?logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose Material 3](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?logo=jetpackcompose)](https://developer.android.com/compose)
[![yt-dlp 2026.11.23](https://img.shields.io/badge/yt--dlp-2026.11.23-red)](https://github.com/yt-dlp/yt-dlp/releases)
[![FFmpeg 9.1 JNI](https://img.shields.io/badge/FFmpeg-9.1%20JNI-blue)](https://github.com/Samyak-2212/ffmpeg_yt-dlp_gui)
[![Chaquopy 17.1.0](https://img.shields.io/badge/Chaquopy-17.1.0-orange)](https://chaquo.com/chaquopy/)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

A native Material 3 Android frontend for [yt-dlp](https://github.com/yt-dlp/yt-dlp) that runs completely on-device via [Chaquopy](https://chaquo.com/chaquopy/) (Python 3.11) and in-process [FFmpeg 9.1 JNI shared libraries](https://github.com/Samyak-2212/ffmpeg_yt-dlp_gui), targeting Android 7.0 through Android 15+.

[Features](#features) · [Architecture](#architecture) · [Building from Source](#building-from-source) · [Tech Stack](#tech-stack) · [Links](#links--resources) · [License](#license)

</div>

---

## Features

- **Video Downloads** — Quality presets from `360p` up to `4K`, custom `yt-dlp` format specifier, and native `MP4` / `MKV` / `WebM` merging
- **Audio Extraction** — Extract and convert audio streams (`M4A`/AAC, `Opus`, `FLAC`, `WAV`) via in-process FFmpeg
- **Subtitles & Captions** — Embed soft subtitles (`mov_text` in MP4, `srt` in MKV), convert `VTT`/`ASS`/`SRT`, save separate subtitle files, and include auto-generated captions
- **YouTube Music Metadata** — Embed track metadata and chapter information via in-process FFmpeg post-processing
- **Zero-Setup First Run** — Pre-bundled FFmpeg 9.1 JNI shared libraries for `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- **Android 7.0–15+ Support (API 24–35+)** — In-process JNI execution, read-only dynamic Python zipapp updates, and 16 KB page-aligned native libraries
- **Storage Picker** — SAF folder selection with `primary:` path resolution, writability validation, recent folder chips, and app-external `Downloads` fallback
- **Foreground Download Service** — Persistent progress notifications, concurrency limits (`1–5` tasks), and signal-safe cancellation
- **Persistent Download History** — Search completed downloads and share or open media files directly via `FileProvider`
- **In-App Updaters** — Dynamically update `yt-dlp` from [yt-dlp GitHub Releases](https://github.com/yt-dlp/yt-dlp/releases) without reinstalling the APK, plus in-app APK update checks from [Releases](https://github.com/Samyak-2212/Android_yt-dlp_GUI/releases)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Jetpack Compose UI                     │
│               DownloadScreen · History · Settings           │
└──────────────────────────────┬──────────────────────────────┘
                               │ ViewModel / StateFlow
┌──────────────────────────────▼──────────────────────────────┐
│                      DownloadService                        │
│         (Foreground Service, Concurrency & Coroutines)      │
└──────────────────────────────┬──────────────────────────────┘
                               │ YtDlpEngine (Kotlin)
┌──────────────────────────────▼──────────────────────────────┐
│                 ytdlp_bridge.py (Python)                    │
│     extract_info / download / post_hooks / get_progress     │
│        [Hooks FFmpegPostProcessor to In-Process JNI]        │
└──────────────────────────────┬──────────────────────────────┘
                               │ In-Process JNI Calls
┌──────────────────────────────▼──────────────────────────────┐
│                com.ytdlp.ffmpeg.FFmpegBridge                │
│                     libffmpeg_jni.so                        │
│    ├─ libavcodec.so    ├─ libavformat.so  ├─ libavfilter.so │
│    ├─ libavutil.so     ├─ libswresample.so├─ libswscale.so  │
└─────────────────────────────────────────────────────────────┘
```

### Native Runtime Details

- **Embedded Python**: Chaquopy runs Python 3.11 with `yt-dlp` on-device and supports in-place zipapp updates (`filesDir/updates/yt-dlp.zip`, marked read-only for API 34+).
- **In-Process FFmpeg 9.1**: `YtDlpApp` loads the 7 bundled shared libraries from `jniLibs/` in dependency order (`avutil` → `swresample` → `swscale` → `avcodec` → `avformat` → `avfilter` → `ffmpeg_jni`), and `ytdlp_bridge.py` routes `FFmpegPostProcessor` calls directly through `FFmpegBridge.runFFmpegJson`.
- **Supported ABIs**: `arm64-v8a`, `armeabi-v7a`, and `x86_64` (16 KB ELF page-aligned for Android 15+).

> For architectural rationale and contributor constraints, see [AGENTS.md](AGENTS.md).

---

## Building from Source

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Ladybug / Meerkat or newer)
- JDK 17 (`java -version` → `17.x`)
- Android SDK with API 35/36 installed
- Python 3.11 via [uv](https://docs.astral.sh/uv/):
  ```powershell
  uv python install 3.11
  ```

### Build & Install

```powershell
# Clone the repository
git clone https://github.com/Samyak-2212/Android_yt-dlp_GUI.git
cd Android_yt-dlp_GUI

# Build Debug APK
.\gradlew.bat assembleDebug

# Run unit tests & lint
.\gradlew.bat testDebugUnitTest lintDebug

# Install to a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Tech Stack

| Component | Details |
|---|---|
| Language | [Kotlin 2.3.20](https://kotlinlang.org/) |
| UI Framework | [Jetpack Compose](https://developer.android.com/compose) + [Material 3](https://m3.material.io/) |
| Python Runtime | [Chaquopy 17.1.0](https://chaquo.com/chaquopy/) (Python 3.11) |
| Media Extractor | [yt-dlp 2026.11.23](https://github.com/yt-dlp/yt-dlp/releases) (pip + dynamic zipapp updater) |
| Post-Processor | [FFmpeg 9.1 JNI Shared Libraries](https://github.com/Samyak-2212/ffmpeg_yt-dlp_gui) |
| Networking | [OkHttp](https://github.com/square/okhttp) |
| Serialization | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) |
| Persistence | [AndroidX DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) |
| Build System | Gradle 9.1.0 + AGP 9.0.1 (`minSdk 24` / `targetSdk 35` / `compileSdk 36`) |

---

## Links & Resources

- **Repository**: [github.com/Samyak-2212/Android_yt-dlp_GUI](https://github.com/Samyak-2212/Android_yt-dlp_GUI)
- **Releases & APKs**: [github.com/Samyak-2212/Android_yt-dlp_GUI/releases](https://github.com/Samyak-2212/Android_yt-dlp_GUI/releases)
- **Issue Tracker**: [github.com/Samyak-2212/Android_yt-dlp_GUI/issues](https://github.com/Samyak-2212/Android_yt-dlp_GUI/issues)
- **FFmpeg 9.1 JNI Backend**: [github.com/Samyak-2212/ffmpeg_yt-dlp_gui](https://github.com/Samyak-2212/ffmpeg_yt-dlp_gui)
- **Architecture & Contributor Guide**: [AGENTS.md](AGENTS.md)

---

## License

- **Android yt-dlp GUI**: Licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for full text.
- **yt-dlp**: Licensed under the [Unlicense](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE).
- **FFmpeg**: Licensed under [LGPL v2.1+ / GPL](https://ffmpeg.org/legal.html). See [ffmpeg.org](https://ffmpeg.org/) for details.
