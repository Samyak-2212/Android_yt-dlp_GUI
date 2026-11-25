package com.ytdlp.ffmpeg

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

/**
 * FFmpegLoader — Runtime download and dynamic loading of FFmpeg shared libraries (Approach A).
 *
 * Provides dynamic downloading from GitHub releases and loading via System.load().
 * Strictly enforces read-only file permissions (file.setReadOnly()) as mandated by Android 14+ (API 34)
 * for dynamically loaded code.
 */
class FFmpegLoader(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val libDir = File(context.filesDir, "ffmpeg_libs")

    companion object {
        private const val TAG = "FFmpegLoader"

        // GitHub Releases base URL
        private const val RELEASES_BASE =
            "https://github.com/Samyak-2212/ffmpeg_yt-dlp_gui/releases/download/v9.1"

        // Strict dependency order for loading
        val LIBRARY_LOAD_ORDER = listOf(
            "avutil",
            "swresample",
            "swscale",
            "avcodec",
            "avformat",
            "avfilter",
            "ffmpeg_jni"
        )

        private const val MIN_SO_SIZE_BYTES = 64 * 1024L
    }

    /**
     * Returns true if all FFmpeg .so files are present in the private files dir
     * and valid.
     */
    fun isReady(): Boolean {
        return LIBRARY_LOAD_ORDER.all { lib ->
            val f = File(libDir, "lib$lib.so")
            f.exists() && f.length() >= MIN_SO_SIZE_BYTES
        }
    }

    fun getLibDir(): File = libDir

    /**
     * Load FFmpeg .so files from the private files directory.
     * Enforces read-only permissions and loads in dependency order.
     */
    fun loadFromFilesDir() {
        libDir.mkdirs()
        for (lib in LIBRARY_LOAD_ORDER) {
            val file = File(libDir, "lib$lib.so")
            if (!file.exists()) {
                throw UnsatisfiedLinkError("FFmpeg library not found: ${file.absolutePath}")
            }
            // Android 14+ requirement: must be read-only before dynamic loading
            makeReadOnly(file)
            Log.d(TAG, "Loading native library via System.load: ${file.absolutePath}")
            System.load(file.absolutePath)
        }
        Log.i(TAG, "All FFmpeg libraries loaded successfully from ${libDir.absolutePath}")
        FFmpegBridge.init(fromJniLibs = false)
    }

    /**
     * Download FFmpeg .so files for the current device ABI from GitHub Releases.
     */
    suspend fun downloadLibraries(
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val abi = getBestAbi()
            val zipName = "ffmpeg-android-9.1-$abi.zip"
            val url = "$RELEASES_BASE/$zipName"
            val tempZip = File(context.cacheDir, zipName)

            Log.i(TAG, "Downloading FFmpeg libraries for ABI $abi from $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "YtDlpAndroid/1.0 FFmpegLoader")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code} downloading $url")
                )
            }

            val body = response.body
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val total = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                tempZip.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }

            // Extract to private filesDir
            libDir.mkdirs()
            extractZip(tempZip, libDir)
            tempZip.delete()

            // Validate and enforce read-only
            for (lib in LIBRARY_LOAD_ORDER) {
                val f = File(libDir, "lib$lib.so")
                if (!f.exists() || f.length() < MIN_SO_SIZE_BYTES) {
                    return@withContext Result.failure(
                        Exception("lib$lib.so missing or corrupted after extraction")
                    )
                }
                makeReadOnly(f)
            }

            Log.i(TAG, "FFmpeg libraries ready at ${libDir.absolutePath}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }

    private fun getBestAbi(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        return when {
            supportedAbis.contains("arm64-v8a") -> "arm64-v8a"
            supportedAbis.contains("x86_64")    -> "x86_64"
            else                                 -> "armeabi-v7a"
        }
    }

    /**
     * Mark file strictly read-only for Android 14+ (API 34) dynamic code loading.
     */
    private fun makeReadOnly(file: File) {
        file.setReadable(true, false)
        file.setWritable(false, false)
        file.setReadOnly()
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(destDir, entry.name.substringAfterLast('/'))
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out ->
                        zis.copyTo(out)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
