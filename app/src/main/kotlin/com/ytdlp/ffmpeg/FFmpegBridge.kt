package com.ytdlp.ffmpeg

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * FFmpegBridge — Kotlin API for the FFmpeg 9.1 JNI wrapper.
 *
 * Exposes in-process FFmpeg execution without spawning external processes.
 * Compatible with Android 7.0+ (API 24+) through Android 15+ (API 35+).
 */
object FFmpegBridge {

    private const val TAG = "FFmpegBridge"

    @Volatile
    private var isLoaded = false

    private val json = Json { ignoreUnknownKeys = true }

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initialize the FFmpeg JNI bridge.
     *
     * In bundled mode (default): loads [libffmpeg_jni.so] and its dependencies
     * from the APK's `jniLibs/` using System.loadLibrary().
     *
     * In download mode: call [FFmpegLoader.loadFromFilesDir] first, which
     * loads the .so files directly via System.load(), then call this with [fromJniLibs] = false.
     *
     * @param fromJniLibs If true, load via System.loadLibrary (bundled APK mode).
     *                    If false, assume .so files are already loaded externally.
     */
    fun init(fromJniLibs: Boolean = true) {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            try {
                if (fromJniLibs) {
                    // Load in strict dependency order
                    System.loadLibrary("avutil")
                    System.loadLibrary("swresample")
                    System.loadLibrary("swscale")
                    System.loadLibrary("avcodec")
                    System.loadLibrary("avformat")
                    System.loadLibrary("avfilter")
                    System.loadLibrary("ffmpeg_jni")
                }
                isLoaded = true
                Log.i(TAG, "FFmpeg JNI loaded successfully: ${getFFmpegVersion()}")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load FFmpeg native libraries", e)
                throw e
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Run an FFmpeg command asynchronously via Coroutines.
     *
     * @param args FFmpeg arguments (without the leading "ffmpeg").
     *             Example: ["-i", "in.mkv", "-c:a", "aac", "out.mp4"]
     * @return Exit code: 0 = success, 1 = error, negative = cancelled.
     */
    suspend fun runFFmpegAsync(vararg args: String): Int = withContext(Dispatchers.IO) {
        checkLoaded()
        Log.d(TAG, "runFFmpegAsync: ${args.joinToString(" ")}")
        val arr = Array(args.size) { args[it] }
        val result = nativeRunFFmpeg(arr)
        Log.d(TAG, "runFFmpegAsync exit code: $result")
        result
    }

    /**
     * Run an FFmpeg command synchronously (blocking).
     * Accessible directly from Chaquopy Python.
     *
     * @param args FFmpeg arguments (without the leading "ffmpeg").
     * @return Exit code: 0 = success, 1 = error.
     */
    @JvmStatic
    fun runFFmpeg(vararg args: String): Int {
        checkLoaded()
        Log.i(TAG, "runFFmpeg: ${args.joinToString(" ")}")
        val arr = Array(args.size) { args[it] }
        return nativeRunFFmpeg(arr)
    }

    /**
     * Run an FFmpeg command passing arguments as a List<String>.
     * Convenient for Kotlin callers.
     */
    @JvmStatic
    fun runFFmpegList(args: List<String>): Int {
        checkLoaded()
        Log.i(TAG, "runFFmpegList: ${args.joinToString(" ")}")
        val arr = Array(args.size) { args[it] }
        return nativeRunFFmpeg(arr)
    }

    /**
     * Run an FFmpeg command from a JSON array string.
     * Guaranteed type compatibility across the Chaquopy Python/Java boundary.
     *
     * @param argsJson JSON-encoded array of argument strings: '["-y", "-i", ...]'
     * @return Exit code: 0 = success, non-zero = error.
     */
    @JvmStatic
    fun runFFmpegJson(argsJson: String): Int {
        checkLoaded()
        Log.i(TAG, "runFFmpegJson: $argsJson")
        val list = try {
            json.decodeFromString<List<String>>(argsJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse FFmpeg args JSON: $argsJson", e)
            return 1
        }
        val arr = Array(list.size) { list[it] }
        return nativeRunFFmpeg(arr)
    }

    /**
     * Cancel the currently running FFmpeg operation cooperatively.
     */
    @JvmStatic
    fun cancelFFmpeg() {
        if (!isLoaded) return
        Log.i(TAG, "Cancelling FFmpeg execution...")
        nativeCancelFFmpeg()
    }

    /**
     * Returns the FFmpeg version string.
     */
    @JvmStatic
    fun getFFmpegVersion(): String {
        return if (isLoaded) {
            try {
                nativeGetFFmpegVersion()
            } catch (e: Throwable) {
                "FFmpeg 9.1 (Error reading version: ${e.message})"
            }
        } else {
            "Not loaded"
        }
    }

    /**
     * Returns true if the FFmpeg libraries are loaded and ready.
     */
    @JvmStatic
    fun isAvailable(): Boolean = isLoaded

    // ─────────────────────────────────────────────────────────────────────────
    // Convenience Helpers for yt-dlp GUI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mux video + audio into an MP4 container (stream copy — no re-encode).
     */
    suspend fun muxToMp4(
        videoInput: String,
        audioInput: String,
        output: String,
        vararg extraArgs: String
    ): Int {
        val args = buildList {
            add("-y")
            add("-i"); add(videoInput)
            add("-i"); add(audioInput)
            add("-c:v"); add("copy")
            add("-c:a"); add("copy")
            addAll(extraArgs)
            add("-movflags"); add("+faststart")
            add(output)
        }
        return runFFmpegAsync(*args.toTypedArray())
    }

    /**
     * Re-encode audio to AAC.
     */
    suspend fun encodeAudioToAac(
        input: String,
        output: String,
        bitrate: String = "192k"
    ): Int = runFFmpegAsync(
        "-y", "-i", input,
        "-c:a", "aac",
        "-b:a", bitrate,
        "-vn",
        output
    )

    /**
     * Embed subtitles into an MKV container (soft subtitles).
     */
    suspend fun embedSubtitles(
        videoInput: String,
        subtitleInput: String,
        output: String,
        language: String = "und"
    ): Int = runFFmpegAsync(
        "-y",
        "-i", videoInput,
        "-i", subtitleInput,
        "-c:v", "copy",
        "-c:a", "copy",
        "-c:s", "srt",
        "-metadata:s:s:0", "language=$language",
        output
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Private
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkLoaded() {
        check(isLoaded) {
            "FFmpegBridge not initialized. Call FFmpegBridge.init() first."
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Native method declarations
    // ─────────────────────────────────────────────────────────────────────────

    @JvmStatic
    private external fun nativeRunFFmpeg(args: Array<String>): Int

    @JvmStatic
    private external fun nativeCancelFFmpeg()

    @JvmStatic
    private external fun nativeGetFFmpegVersion(): String
}
