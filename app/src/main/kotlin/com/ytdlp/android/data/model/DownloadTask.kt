package com.ytdlp.android.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "",
    val quality: VideoQuality = VideoQuality.P720,
    val customFormat: String = "",
    val subtitleConfig: SubtitleConfig = SubtitleConfig(),
    val downloadLyrics: Boolean = false,
    val outputDir: String = "",
    val outputUri: String = "",
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f,
    val speed: Double = 0.0,
    val eta: Long = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val filePath: String = "",
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
