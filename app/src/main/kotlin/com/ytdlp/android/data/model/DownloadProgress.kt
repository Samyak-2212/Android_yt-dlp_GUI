package com.ytdlp.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadProgress(
    val status: String = "unknown",
    val downloaded_bytes: Long = 0,
    val total_bytes: Long = 0,
    val speed: Double = 0.0,
    val eta: Long = 0,
    val filename: String = ""
) {
    val progressFraction: Float
        get() = if (total_bytes > 0) {
            (downloaded_bytes.toFloat() / total_bytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    fun formattedSpeed(): String {
        if (speed <= 0) return "-- KB/s"
        val kb = speed / 1024.0
        return if (kb >= 1024.0) {
            String.format("%.2f MB/s", kb / 1024.0)
        } else {
            String.format("%.1f KB/s", kb)
        }
    }

    fun formattedEta(): String {
        if (eta <= 0) return "--:--"
        val minutes = eta / 60
        val seconds = eta % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun formattedSize(): String {
        if (total_bytes <= 0) {
            val downMb = downloaded_bytes / (1024.0 * 1024.0)
            return String.format("%.1f MB", downMb)
        }
        val downMb = downloaded_bytes / (1024.0 * 1024.0)
        val totalMb = total_bytes / (1024.0 * 1024.0)
        return String.format("%.1f / %.1f MB", downMb, totalMb)
    }
}
