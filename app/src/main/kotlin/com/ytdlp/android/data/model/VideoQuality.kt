package com.ytdlp.android.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class VideoQuality(val label: String, val formatSpec: String) {
    AUDIO_ONLY("Audio Only (MP3)", "bestaudio[ext=m4a]/bestaudio"),
    P360("360p", "bestvideo[height<=360]+bestaudio/best[height<=360]"),
    P480("480p", "bestvideo[height<=480]+bestaudio/best[height<=480]"),
    P720("720p (Default)", "bestvideo[height<=720]+bestaudio/best[height<=720]"),
    P1080("1080p (Full HD)", "bestvideo[height<=1080]+bestaudio/best[height<=1080]"),
    P1440("1440p (2K)", "bestvideo[height<=1440]+bestaudio/best[height<=1440]"),
    P4K("4K (2160p)", "bestvideo[height<=2160]+bestaudio/best[height<=2160]"),
    BEST("Best Available", "bestvideo+bestaudio/best"),
    CUSTOM("Custom Format", "");

    fun isAudioOnly(): Boolean = this == AUDIO_ONLY
}
