# Android yt-dlp GUI

<p align="center">
  <img src="https://img.shields.io/badge/Android-7.0%2B-brightgreen?logo=android" alt="Android 7.0+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.3.20-blueviolet?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?logo=jetpackcompose" alt="Compose"/>
  <img src="https://img.shields.io/badge/yt--dlp-latest-red" alt="yt-dlp"/>
  <img src="https://img.shields.io/badge/FFmpeg-9.0.2%20JNI-blue" alt="FFmpeg 9.0.2 JNI"/>
  <img src="https://img.shields.io/badge/Chaquopy-17.1.0-orange" alt="Chaquopy"/>
  <img src="https://img.shields.io/badge/minSdk-24-yellow" alt="minSdk 24"/>
</p>

A native Android application providing a full-featured GUI for [yt-dlp](https://github.com/yt-dlp/yt-dlp), supporting YouTube video & audio downloads, subtitle embedding, YouTube Music metadata, and more — running entirely on-device via Python-in-Android (Chaquopy) with **in-process FFmpeg 9.0.2 JNI shared libraries**.

---

## Features

- 🎬 **Video Downloads** — Quality presets up to 4K, custom format specifier, MP4/MKV output
- 🎵 **Audio Extraction** — M4A (AAC), Opus, MP3 with configurable bitrate
- 📝 **Subtitles** — Embed in video, save separately, auto-generated, multi-language
- 🎤 **YouTube Music** — Lyrics & metadata embedding via in-process FFmpeg
- 🚀 **Zero-Setup Startup** — Pre-bundled FFmpeg 9.0.2 JNI shared libraries (`arm64-v8a`, `armeabi-v7a`, `x86_64`)
- 🔒 **Full Modern Android Compatibility** — In-process JNI avoids Android 10+ W^X `execve` blocks; 16 KB page-aligned for Android 15 (API 35+)
- 📁 **Storage Selection** — SAF picker + persistent path memory, app-external default
- 🔔 **Foreground Download Service** — Persistent notification with progress bar; survives app backgrounding
- 📜 **Download History** — Search, open, share, delete completed downloads
- 🔄 **In-App Dynamic Engine Updater** — Check and dynamically update yt-dlp on-the-fly directly from GitHub Releases zipapp without reinstalling the app (Android 14+ read-only compliant)
- ⚙️ **Settings** — Light/Dark/System theme, download defaults (including custom format specifier), yt-dlp update check & install, FFmpeg version info, app self-update

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Jetpack Compose UI                     │
│               DownloadScreen  HistoryScreen  Settings       │
└──────────────────────────────┬──────────────────────────────┘
                               │ ViewModel / StateFlow
┌──────────────────────────────▼──────────────────────────────┐
│                      DownloadService                        │
│              (Foreground Service, Coroutines)               │
└──────────────────────────────┬──────────────────────────────┘
                               │ YtDlpEngine (Kotlin)
┌──────────────────────────────▼──────────────────────────────┐
│                 ytdlp_bridge.py (Python)                    │
│           extract_info / download / get_progress            │
│         [Intercepts FFmpegPostProcessor via JNI]            │
└──────────────────────────────┬──────────────────────────────┘
                               │ In-Process JNI Calls
┌──────────────────────────────▼──────────────────────────────┐
│                com.ytdlp.ffmpeg.FFmpegBridge                │
│                     libffmpeg_jni.so                        │
│    ├─ libavcodec.so    ├─ libavformat.so  ├─ libavfilter.so │
│    ├─ libavutil.so     ├─ libswresample.so├─ libswscale.so  │
└─────────────────────────────────────────────────────────────┘
```

### Why JNI Shared Libraries (Approach A)?

| Execution Strategy | Android 7–9 (API 24–28) | Android 10–13 (API 29–33) | Android 14+ (API 34+) |
|---|---|---|---|
| Standalone binary in `filesDir` (`execve`) | ✅ Works | ❌ Blocked by W^X policy | ❌ Hard blocked |
| Static legacy binary (v3.3.2) | ⚠️ Codec mismatch | ❌ Blocked | ❌ Hard blocked |
| **FFmpeg 9.0.2 JNI (`jniLibs/`)** | **✅ Works** | **✅ Works** | **✅ Works (16KB aligned)** |

---

## Building from Source

### Prerequisites

- [Android Studio](https://developer.android.com/studio) Ladybug / Meerkat or later
- JDK 17 (`java -version` → 17.x)
- Android SDK with API 35 installed
- Python 3.11 (via [uv](https://github.com/astral-sh/uv)):
  ```powershell
  uv python install 3.11
  ```

### Build Steps

```powershell
# Clone
git clone https://github.com/Samyak-2212/Android_yt-dlp_GUI.git
cd Android_yt-dlp_GUI

# Assemble Debug APK
.\gradlew assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Tech Stack

| Component | Version |
|---|---|
| Language | Kotlin 2.3.20 |
| UI | Jetpack Compose + Material 3 |
| Python runtime | Chaquopy 17.1.0 + Python 3.11 |
| Downloader | yt-dlp (pip package) |
| Post-processor | FFmpeg 9.0.2 JNI Shared Libraries |
| HTTP | OkHttp |
| Serialization | Kotlinx Serialization JSON |
| Settings | AndroidX DataStore Preferences |
| Build System | Gradle 9.1.0 + AGP 9.0.1 |
| API Levels | minSdk 24 / targetSdk 35 / compileSdk 36 |

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

yt-dlp is licensed under the [Unlicense](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE).
FFmpeg is licensed under the [LGPL v2.1+](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html).
