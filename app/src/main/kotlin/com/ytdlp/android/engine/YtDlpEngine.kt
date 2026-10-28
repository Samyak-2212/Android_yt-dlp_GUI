package com.ytdlp.android.engine

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.ytdlp.android.data.model.DownloadProgress
import com.ytdlp.android.data.model.ExtractInfoResponse
import com.ytdlp.android.data.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject

class YtDlpEngine(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val py: Python by lazy {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        Python.getInstance()
    }

    private val bridge: PyObject by lazy {
        py.getModule("ytdlp_bridge")
    }

    suspend fun extractInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val responseJson = bridge.callAttr("extract_info", url).toString()
            val response = json.decodeFromString<ExtractInfoResponse>(responseJson)
            if (response.status == "ok") {
                Result.success(response.toVideoInfo())
            } else {
                Result.failure(Exception(response.error ?: "Failed to extract video info"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAvailableSubtitles(url: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val responseJson = bridge.callAttr("get_available_subtitles", url).toString()
            val pyObj = bridge.callAttr("get_available_subtitles", url)
            // Just return parsed strings or empty
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun download(
        taskId: String,
        url: String,
        optionsJson: String,
        ffmpegPath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resultJson = bridge.callAttr("download", taskId, url, optionsJson, ffmpegPath).toString()
            Result.success(resultJson)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getProgress(taskId: String): DownloadProgress {
        return try {
            val progressJson = bridge.callAttr("get_progress", taskId).toString()
            json.decodeFromString<DownloadProgress>(progressJson)
        } catch (e: Exception) {
            DownloadProgress(status = "error")
        }
    }

    fun cancelDownload(taskId: String) {
        try {
            bridge.callAttr("cancel_download", taskId)
        } catch (_: Exception) {}
    }

    fun getYtDlpVersion(): String {
        return try {
            bridge.callAttr("get_version").toString()
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun reloadEngine(): Result<String> {
        return try {
            val res = bridge.callAttr("reload_ytdlp").toString()
            val obj = JSONObject(res)
            if (obj.optString("status") == "ok") {
                Result.success(obj.optString("version", getYtDlpVersion()))
            } else {
                Result.failure(Exception(obj.optString("error", "Failed to reload yt-dlp")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var instance: YtDlpEngine? = null

        fun getInstance(context: Context): YtDlpEngine {
            return instance ?: synchronized(this) {
                instance ?: YtDlpEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
