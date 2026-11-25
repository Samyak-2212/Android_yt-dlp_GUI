package com.ytdlp.android

import com.ytdlp.android.data.model.DownloadProgress
import com.ytdlp.android.data.model.ExtractInfoResponse
import com.ytdlp.android.data.model.VideoQuality
import com.ytdlp.android.service.DownloadBridgeOptions
import com.ytdlp.android.updater.AppUpdater
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun downloadProgress_calculatesFractionAndPercent() {
        val progress = DownloadProgress(
            status = "downloading",
            downloaded_bytes = 500,
            total_bytes = 1000,
            speed = 2048.0,
            eta = 65
        )
        assertEquals(0.5f, progress.progressFraction, 0.001f)
        assertEquals(50, progress.progressPercent)
        assertEquals("01:05", progress.formattedEta())
        assertEquals("2.0 KB/s", progress.formattedSpeed())
    }

    @Test
    fun downloadProgress_handlesZeroTotalBytes() {
        val progress = DownloadProgress(downloaded_bytes = 1024 * 1024, total_bytes = 0)
        assertEquals(0f, progress.progressFraction, 0.001f)
        assertEquals(0, progress.progressPercent)
        assertEquals("--:--", progress.formattedEta())
        assertEquals("-- KB/s", progress.formattedSpeed())
    }

    @Test
    fun videoQuality_audioOnlyCheck() {
        assertTrue(VideoQuality.AUDIO_ONLY.isAudioOnly())
        assertFalse(VideoQuality.P720.isAudioOnly())
        assertFalse(VideoQuality.CUSTOM.isAudioOnly())
    }

    @Test
    fun extractInfoResponse_parsesAndMapsToVideoInfo() {
        val payload = """
            {
              "status": "ok",
              "title": "Test Video",
              "duration": 125,
              "uploader": "Channel",
              "webpage_url": "https://example.com/v",
              "formats": [{"height": 720, "ext": "mp4", "format_id": "22"}],
              "subtitles": ["en", "es"],
              "auto_subtitles": ["fr"]
            }
        """.trimIndent()
        val resp = json.decodeFromString<ExtractInfoResponse>(payload)
        val info = resp.toVideoInfo()
        assertEquals("Test Video", info.title)
        assertEquals(125L, info.duration)
        assertEquals(1, info.formats.size)
        assertEquals(720, info.formats.first().height)
        assertEquals(listOf("en", "es"), info.subtitles)
        assertEquals(listOf("fr"), info.autoSubtitles)
    }

    @Test
    fun appUpdater_semanticVersionComparison() {
        assertTrue(AppUpdater.isVersionNewer("1.0.10", "1.0.2"))
        assertTrue(AppUpdater.isVersionNewer("v1.1.0", "1.0.9"))
        assertTrue(AppUpdater.isVersionNewer("2.0.0", "1.9.9"))
        assertTrue(AppUpdater.isVersionNewer("2026.11.23", "2026.10.26"))
        assertFalse(AppUpdater.isVersionNewer("2026.11.23", "2026.11.23"))
        assertFalse(AppUpdater.isVersionNewer("1.0.0", "1.0.0"))
        assertFalse(AppUpdater.isVersionNewer("1.0.2", "1.0.10"))
    }

    @Test
    fun downloadBridgeOptions_serializesSubtitleFlags() {
        val opts = DownloadBridgeOptions(
            output_dir = "/tmp/dl",
            write_subtitles = true,
            embed_subtitles = true,
            download_separate_file = true,
            subtitle_langs = listOf("en", "ja")
        )
        val encoded = json.encodeToString(opts)
        assertTrue(encoded.contains("\"download_separate_file\":true"))
        assertTrue(encoded.contains("\"embed_subtitles\":true"))
    }
}
