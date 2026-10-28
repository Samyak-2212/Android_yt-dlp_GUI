package com.ytdlp.android.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ytdlp.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val tagName: String,
    val apkUrl: String,
    val changelog: String,
    val isNewer: Boolean
)

class AppUpdater(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val REPO_OWNER = "naskar"
        const val REPO_NAME = "Android_yt-dlp_GUI"

        @Volatile
        private var instance: AppUpdater? = null

        fun getInstance(context: Context): AppUpdater {
            return instance ?: synchronized(this) {
                instance ?: AppUpdater(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "YT-DLP-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API returned ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val release = json.decodeFromString<GitHubRelease>(body)

            val currentVersion = BuildConfig.VERSION_NAME
            val releaseTag = release.tag_name.removePrefix("v")
            val isNewer = releaseTag > currentVersion

            val apkAsset = release.assets.find { it.name.endsWith(".apk") }
            val downloadUrl = apkAsset?.browser_download_url ?: release.html_url

            Result.success(
                AppUpdateInfo(
                    tagName = releaseTag,
                    apkUrl = downloadUrl,
                    changelog = release.body,
                    isNewer = isNewer
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(downloadUrl: String, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "YT-DLP-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
            val totalBytes = body.contentLength()
            val updateFile = File(context.cacheDir, "update.apk")

            body.byteStream().use { input ->
                FileOutputStream(updateFile).use { output ->
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

            Result.success(updateFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}
