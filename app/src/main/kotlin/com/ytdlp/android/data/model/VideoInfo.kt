package com.ytdlp.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FormatInfo(
    val height: Int? = null,
    val ext: String = "mp4",
    val filesize: Long = 0,
    val format_note: String = "",
    val format_id: String = ""
)

@Serializable
data class VideoInfo(
    val title: String = "Unknown Title",
    val duration: Long = 0,
    val uploader: String = "Unknown Uploader",
    val thumbnail: String = "",
    val webpageUrl: String = "",
    val formats: List<FormatInfo> = emptyList(),
    val subtitles: List<String> = emptyList(),
    val autoSubtitles: List<String> = emptyList()
)

@Serializable
data class ExtractInfoResponse(
    val status: String = "ok",
    val title: String = "",
    val duration: Long = 0,
    val uploader: String = "",
    val thumbnail: String = "",
    val webpage_url: String = "",
    val formats: List<FormatInfo> = emptyList(),
    val subtitles: List<String> = emptyList(),
    val auto_subtitles: List<String> = emptyList(),
    val error: String? = null
) {
    fun toVideoInfo(): VideoInfo = VideoInfo(
        title = title,
        duration = duration,
        uploader = uploader,
        thumbnail = thumbnail,
        webpageUrl = webpage_url,
        formats = formats,
        subtitles = subtitles,
        autoSubtitles = auto_subtitles
    )
}
