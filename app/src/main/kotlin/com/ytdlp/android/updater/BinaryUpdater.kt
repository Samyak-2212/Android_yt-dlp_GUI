package com.ytdlp.android.updater

import android.content.Context
import android.os.Build
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

    fun getFfmpegFile(): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        return File(binDir, "ffmpeg")
    }

    fun isFfmpegReady(): Boolean {
        val file = getFfmpegFile()
        return file.exists() && file.length() > 1024
    }

    fun getFfmpegPath(): String {
        val file = getFfmpegFile()
        if (file.exists()) {
            file.setExecutable(true, false)
            return file.absolutePath
        }
        return ""
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
        try {
            // Use Khang-NT/ffmpeg-binary-android: statically linked FFmpeg targeting Android 5.0+
            // This avoids the Tyrrrz/FFmpegBin issue where libc glob() is unavailable on Android < 9
            val relRequest = Request.Builder()
                .url("https://api.github.com/repos/Khang-NT/ffmpeg-binary-android/releases/latest")
                .header("User-Agent", "YT-DLP-Android")
                .build()

            val relResponse = httpClient.newCall(relRequest).execute()
            if (!relResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch FFmpeg release info: ${relResponse.code}"))
            }

            val relBody = relResponse.body?.string() ?: return@withContext Result.failure(Exception("Empty release body"))
            val release = json.decodeFromString<GitHubRelease>(relBody)

            // Map device ABI to Khang-NT archive name
            val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
            val assetName = when {
                arch.contains("arm64") -> "arm64-v8a-full.tar.bz2"
                arch.contains("x86_64") -> "x86_64-full.tar.bz2"
                arch.contains("x86") -> "i686-full.tar.bz2"
                else -> "armv7-a-full.tar.bz2"  // armeabi-v7a
            }

            val asset = release.assets.find { it.name == assetName }
                ?: release.assets.find { it.name.endsWith(".tar.bz2") }
                ?: return@withContext Result.failure(Exception("Could not find FFmpeg asset '$assetName' for $arch"))

            val dlRequest = Request.Builder()
                .url(asset.browser_download_url)
                .header("User-Agent", "YT-DLP-Android")
                .build()

            val dlResponse = httpClient.newCall(dlRequest).execute()
            if (!dlResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed with HTTP ${dlResponse.code}"))
            }

            val dlBody = dlResponse.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val totalBytes = dlBody.contentLength()

            val ffmpegDest = getFfmpegFile()
            val tempTar = File(context.cacheDir, "ffmpeg_temp.tar.bz2")

            // Stream download with progress
            dlBody.byteStream().use { input ->
                java.io.FileOutputStream(tempTar).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesCopied: Long = 0
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            onProgress(bytesCopied.toFloat() / totalBytes.toFloat())
                        }
                    }
                }
            }

            // Extract ffmpeg binary from the tar.bz2 archive
            extractFfmpegFromTarBz2(tempTar, ffmpegDest)
            tempTar.delete()

            ffmpegDest.setExecutable(true, false)
            ffmpegDest.setReadable(true, false)

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract the 'ffmpeg' binary from a .tar.bz2 archive.
     * Uses Java's built-in Apache Commons-compatible streams via BZip2CompressorInputStream fallback,
     * or reads raw bzip2 via java.util.zip-compatible approach with Apache-style buffered streams.
     * On Android, org.apache.commons is not available; instead we use the shell `tar` command
     * (available via Android's Toybox on API 21+) as a reliable fallback.
     */
    private fun extractFfmpegFromTarBz2(archive: File, dest: File) {
        // Strategy 1: Use Android's built-in /system/bin/tar (Toybox, available API 21+)
        // `tar -xjf archive.tar.bz2 -C destDir --wildcards */ffmpeg`
        val binDir = dest.parentFile!!
        binDir.mkdirs()

        val proc = ProcessBuilder(
            "/system/bin/tar", "-xjf", archive.absolutePath,
            "-C", binDir.absolutePath,
            "--strip-components=1"  // flatten any top-level directory
        )
            .redirectErrorStream(true)
            .start()

        val output = proc.inputStream.bufferedReader().readText()
        val exitCode = proc.waitFor()

        if (exitCode != 0) {
            throw Exception("tar extraction failed (exit $exitCode): $output")
        }

        // After extraction, find the ffmpeg binary (it may be named 'ffmpeg' directly)
        val extracted = File(binDir, "ffmpeg")
        if (!extracted.exists() || extracted.length() < 1024) {
            // Try searching sub-directories
            val found = binDir.walkTopDown().firstOrNull { it.name == "ffmpeg" && !it.isDirectory && it.length() > 1024 }
                ?: throw Exception("ffmpeg binary not found in extracted archive")
            if (found.absolutePath != dest.absolutePath) {
                found.copyTo(dest, overwrite = true)
                found.delete()
            }
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
