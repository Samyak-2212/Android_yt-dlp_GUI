package com.ytdlp.android.updater

import android.content.Context
import android.os.Build
import com.ytdlp.ffmpeg.FFmpegBridge
import com.ytdlp.ffmpeg.FFmpegLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0
)

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val hasUpdate: Boolean,
    val downloadUrl: String = "",
    val changelog: String = ""
)

class BinaryUpdater(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }
    val ffmpegLoader = FFmpegLoader(context, httpClient)

    fun isFfmpegReady(): Boolean {
        if (!FFmpegBridge.isAvailable() && ffmpegLoader.isReady()) {
            try {
                ffmpegLoader.loadFromFilesDir()
            } catch (_: Throwable) {}
        }
        return FFmpegBridge.isAvailable()
    }

    fun getFfmpegPath(): String {
        return if (FFmpegBridge.isAvailable()) {
            "jni"
        } else {
            ""
        }
    }

    suspend fun checkYtDlpUpdate(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
                .header("User-Agent", "YT-DLP-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API returned ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val release = json.decodeFromString<GitHubRelease>(body)
            val latestVersion = release.tag_name.removePrefix("v")

            val hasUpdate = latestVersion != currentVersion && latestVersion.isNotBlank()
            Result.success(
                UpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    hasUpdate = hasUpdate,
                    changelog = release.body
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFfmpeg(onProgress: (Float) -> Unit): Result<Boolean> = withContext(Dispatchers.IO) {
        if (FFmpegBridge.isAvailable()) {
            onProgress(1.0f)
            return@withContext Result.success(true)
        }

        val dlResult = ffmpegLoader.downloadLibraries { downloaded, total ->
            if (total > 0) {
                onProgress(downloaded.toFloat() / total.toFloat())
            }
        }

        if (dlResult.isSuccess) {
            try {
                ffmpegLoader.loadFromFilesDir()
                Result.success(true)
            } catch (e: Throwable) {
                Result.failure(e)
            }
        } else {
            Result.failure(dlResult.exceptionOrNull() ?: Exception("Failed to download FFmpeg libraries"))
        }
    }

    companion object {
        @Volatile
        private var instance: BinaryUpdater? = null

        fun getInstance(context: Context): BinaryUpdater {
            return instance ?: synchronized(this) {
                instance ?: BinaryUpdater(context.applicationContext).also { instance = it }
            }
        }
    }
}
