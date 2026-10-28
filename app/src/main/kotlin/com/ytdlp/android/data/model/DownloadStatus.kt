package com.ytdlp.android.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus {
    PENDING,
    EXTRACTING_INFO,
    DOWNLOADING,
    MERGING,
    COMPLETE,
    ERROR,
    CANCELLED
}
